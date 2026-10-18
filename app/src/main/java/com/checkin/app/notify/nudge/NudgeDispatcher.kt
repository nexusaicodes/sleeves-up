package com.checkin.app.notify.nudge

import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.notify.HabitChannels
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField

/**
 * Assembles a snapshot per active habit, asks [NudgeEligibility] what to send, and posts the result.
 *
 * [runOnce] is the only way a nudge is ever posted — there is no bypass and no forced send, so the
 * copy a device shows is whatever eligibility produces.
 *
 * Every read here is a read-only observation of tracking state — this layer never writes to the
 * sessions table, and the decision itself stays in the pure rules. Archived habits are never read:
 * the habits asked about are the active ones, which is the whole of "an archived habit sends no
 * nudges".
 *
 * It also implements [PostedNudges], because retiring a posted nudge is cancelling notification ids
 * this class already owns; a second holder of the [Notifier] and of `Nudge.entries` would be free to
 * disagree with `runOnce` about which ids exist.
 *
 * Nine constructor parameters, each a separate seam a test stands up on its own; bundling them into
 * a holder to pass the count would hide which of them a test is faking without removing any.
 */
@Suppress("LongParameterList")
class NudgeDispatcher(
    private val strings: StringResolver,
    private val repository: CheckInRepository,
    private val habits: HabitRepository,
    private val notifier: Notifier,
    /**
     * A provider, not the ledger: [retire] is a notification-only path called from both check-in
     * writers, and constructing this class must not build a database it will never read.
     */
    private val sendLog: () -> NudgeSendLog,
    private val timeSource: TimeSource,
    private val reminderSettings: ReminderSettings,
    private val alarms: NudgeAlarms,
    private val channels: HabitChannels,
) : PostedNudges {

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /** One habit as a pass sees it. */
    private data class HabitState(val habit: Habit, val snapshot: NudgeSnapshot)

    /**
     * One pass at a time. A pass reads the day's sends and then records one, and two independent
     * triggers reach it — the checkpoint alarm and the hourly worker pass — so unserialised they
     * both read an empty ledger and both post: the same habit reminded twice, or two habits'
     * reminders inside the gap that exists to keep them apart.
     */
    private val passLock = Mutex()

    /**
     * Runs one pass and arms the alarm for the next one. Returns the habits reminded — usually none
     * or one, since the gap holds every other habit back behind the first post of a pass.
     *
     * Arms in a `finally`, so a pass that throws still schedules the next one — without that, one
     * bad pass would end the chain for good. The arm re-reads the ledger rather than adjusting the
     * snapshots by hand, so what it arms for is exactly what the next pass will see: a refused post is
     * recorded as an attempt and so waits for tomorrow, a habit the gap held back is armed for the
     * instant it clears, and a pass that threw before recording anything leaves its nudge due, which
     * [NudgeEligibility.nextAlarmAt] puts off rather than re-firing at once.
     *
     * Never throws except to cancel: every caller is a background path with nothing above it to
     * report to, and the arm below has already run.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun runOnce(): List<Habit> = passLock.withLock {
        val states = readStates() ?: run {
            armFallback()
            return@withLock emptyList()
        }
        val reminded = mutableListOf<Habit>()
        try {
            // The gap spans habits, so a post here moves it for every habit after it in this pass.
            var lastShown = states.firstOrNull()?.snapshot?.lastShownAtMs
            for ((habit, snapshot) in states) {
                val nudge = NudgeEligibility.select(snapshot.copy(lastShownAtMs = lastShown)) ?: continue
                if (send(habit, nudge, snapshot.nowMillis)) {
                    reminded += habit
                    lastShown = snapshot.nowMillis
                }
            }
            // A notification an earlier build posted is about the record the seed habit now holds,
            // and as stale as anything a fresh reminder replaces.
            if (reminded.isNotEmpty()) NotificationIds.RETIRED_NUDGES.forEach { notifier.cancel(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Absorbed: the arm below runs regardless, so a thrown pass costs this pass's nudges and
            // nothing after it. There is nowhere to report it to and nothing that would read it.
        } finally {
            armFromLedger()
        }
        reminded
    }

    /**
     * Arms the alarm for [NudgeEligibility.nextAlarmAcross] without running a pass — for every site
     * that has just lost or moved the alarm: process start (which every boot and package replace
     * goes through), a new or changed reminder time, and a habit created, archived or restored. Serialised
     * with [runOnce] on the same lock, so the time reads, the decision and the arm are one step: a
     * choice stored before this is called cannot be overwritten by an arm derived from the time it
     * replaced.
     *
     * A band open but unsent when the alarm was lost gets its nudge later today rather than tomorrow,
     * but not at once: these sites are usually the user opening the app or changing a time, and a
     * "you haven't checked in" posted seconds later over that screen is noise. Never throws except to
     * cancel — a failed read arms the plain fallback, and a cancelled one arms nothing, leaving the
     * alarm already set in place rather than replacing it with one that knows less.
     */
    suspend fun rearm() = passLock.withLock { armFromLedger() }

    /**
     * Cancels every nudge id of [habitId]'s, and the ids earlier builds posted under: a nudge asking
     * for a check-in is stale the moment that habit gets one, and a pre-habits one was about the
     * record the seed habit holds. Cancelling an id with nothing posted under it is a no-op, so the
     * retired ids need no habit test.
     */
    override fun retire(habitId: Long) {
        Nudge.entries.forEach { notifier.cancel(it.notificationIdFor(habitId)) }
        NotificationIds.RETIRED_NUDGES.forEach { notifier.cancel(it) }
    }

    /** Posts [nudge] for [habit], records the attempt, and reports whether it reached the tray. */
    private suspend fun send(habit: Habit, nudge: Nudge, nowMillis: Long): Boolean {
        val copy = NudgeCatalog.copyFor(nudge)
        // A post to a channel that does not exist is discarded — and recorded below as a refusal,
        // spending the habit's day. The channels are reconciled off the habits table, but on a
        // process this alarm just started that collector may not have run yet.
        runCatching { channels.ensure(habit) }
        val name = habit.name
        val posted = notifier.show(
            NotificationSpec(
                id = nudge.notificationIdFor(habit.id),
                channelId = habit.channelId,
                title = name?.let { strings.format(copy.titleRes, it) } ?: strings.get(copy.unnamedTitleRes),
                body = strings.get(copy.bodyRes),
                launchExtra = LaunchExtras.CHECK_IN,
                launchHabitUuid = habit.uuid,
            ),
        )
        // Notifications can be refused (permission revoked, notifications off, the habit's channel or
        // their group blocked). The attempt is recorded either way, flagged, so the habit's day is
        // spent without spacing any other habit's reminder off a notification nobody was shown.
        sendLog().record(habit.uuid, nudge, nowMillis, posted)
        return posted
    }

    /** Must be called holding [passLock]. Reads afresh, so it sees whatever the pass just recorded. */
    private suspend fun armFromLedger() {
        val at = readStates()?.let { states ->
            NudgeEligibility.nextAlarmAcross(states.map { it.snapshot }, timeSource.zone())
        }
        if (at == null) armFallback() else runCatching { alarms.arm(at) }
    }

    private fun armFallback() {
        runCatching { alarms.armFallback(timeSource.nowMillis(), timeSource.zone(), reminderSettings) }
    }

    /** Every active habit's state, or null when they cannot be read. Cancellation propagates. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun readStates(): List<HabitState>? = try {
        buildStates()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun buildStates(): List<HabitState> {
        val now = timeSource.nowMillis()
        val zone = timeSource.zone()
        // The day and the minute from the one clock read: a second read for the date can land past
        // midnight while `now` did not, opening tomorrow's empty ledger with today's band.
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val today = local.toLocalDate()
        val todayKey = today.format(dateFormatter)
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        // One query answers both frequency rules for every habit, so they cannot disagree about where
        // the day started. This is the ledger's only read, and the only reason it exists.
        val attempts = sendLog().attemptsSince(startOfDay)
        // A row with no habit predates habits, and was about the one record the seed habit now holds.
        val seedUuid = habits.byId(Habit.SEED_ID)?.uuid
        val spentByHabit = attempts.groupBy { it.habitUuid.ifEmpty { seedUuid } }
            .mapValues { (_, rows) -> rows.mapNotNullTo(mutableSetOf()) { Nudge.fromStoredKey(it.key) } }
        val lastShown = attempts.filter { it.posted }.maxOfOrNull { it.atMillis }

        return habits.activeHabits().map { habit ->
            HabitState(
                habit,
                NudgeSnapshot(
                    nowMillis = now,
                    minuteOfDay = local.get(ChronoField.MINUTE_OF_DAY),
                    reminderMinute = reminderSettings.reminderMinute(habit.uuid),
                    // Deliberately the habit's rows for the day and not `getActiveSession()`: a
                    // session still open from an earlier day is a lost day-boundary close, not a user
                    // who is present. See the field.
                    hasCheckedInToday = repository.getSessionsByDate(habit.id, todayKey).isNotEmpty(),
                    spentToday = spentByHabit[habit.uuid].orEmpty(),
                    lastShownAtMs = lastShown,
                ),
            )
        }
    }
}
