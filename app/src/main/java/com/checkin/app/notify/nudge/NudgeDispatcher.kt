package com.checkin.app.notify.nudge

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Assembles a snapshot, asks [NudgeEligibility] what to send, and posts the result.
 *
 * [runOnce] is the only way a nudge is ever posted — there is no bypass and no forced send, so the
 * copy a device shows is whatever eligibility produces.
 *
 * Every read here is a read-only observation of tracking state — this layer never writes to the
 * sessions table, and the decision itself stays in the pure rules.
 *
 * It also implements [PostedNudges], because retiring a posted nudge is cancelling notification ids
 * this class already owns; a second holder of the [Notifier] and of `Nudge.entries` would be free to
 * disagree with `runOnce` about which ids exist.
 */
class NudgeDispatcher(
    private val strings: StringResolver,
    private val repository: CheckInRepository,
    private val notifier: Notifier,
    /**
     * A provider, not the ledger: [retireAll] is a notification-only path called from both check-in
     * writers, and constructing this class must not build a database it will never read.
     */
    private val sendLog: () -> NudgeSendLog,
    private val timeSource: TimeSource,
    private val reminderSettings: ReminderSettings,
    private val alarms: NudgeAlarms,
) : PostedNudges {

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * One pass at a time. A pass reads the day's sends and then records one, and two independent
     * triggers reach it — the checkpoint alarm and the hourly worker pass — so unserialised they
     * both read an empty ledger and both record. That shows one notification (same id) and spends
     * both of the day's slots on it, leaving the later checkpoints unreachable.
     */
    private val passLock = Mutex()

    /**
     * Runs one pass and arms the alarm for the next one. Returns the nudge sent, or null when nothing
     * was eligible or posting was refused.
     *
     * Arms in a `finally`, so a pass that throws still schedules the next one — without that, one
     * bad pass would end the chain for good. The arm re-reads the ledger rather than adjusting the
     * snapshot by hand, so what it arms for is exactly what the next pass will see: a refused post is
     * recorded as an attempt and so waits for the next band, and a pass that threw before recording
     * anything leaves its nudge due, which [NudgeEligibility.nextAlarmAt] puts off rather than
     * re-firing at once.
     *
     * Never throws except to cancel: every caller is a background path with nothing above it to
     * report to, and the arm below has already run.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun runOnce(): Nudge? = passLock.withLock {
        val snapshot = readSnapshot() ?: run {
            armNextCheckpoint()
            return@withLock null
        }
        var sent: Nudge? = null
        try {
            sent = NudgeEligibility.select(snapshot)?.let { send(it, snapshot.nowMillis) }
            if (sent != null) {
                // Retire the day's earlier checkpoint, but only now that this one is actually up.
                // They carry distinct ids by design, so otherwise the follow-up stacks under the first
                // one still in the tray — two notifications saying the user hasn't checked in, which
                // reads as a stuck loop rather than as one message that came back. Clearing *before*
                // the post would instead take a still-actionable notification away on the pass where
                // the post is refused.
                Nudge.entries.filter { it != sent }.forEach { notifier.cancel(it.notificationId) }
                NotificationIds.RETIRED_NUDGES.forEach { notifier.cancel(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Absorbed: the arm below runs regardless, so a thrown pass costs this nudge and nothing
            // after it. There is nowhere to report it to and nothing that would read such a report.
        } finally {
            armFromLedger()
        }
        sent
    }

    /**
     * Arms the alarm for [NudgeEligibility.nextAlarmAt] without running a pass — for every site that
     * has just lost or moved the alarm: process start (which every boot and package replace goes
     * through) and a new hour. Serialised with [runOnce] on the same lock, so the hour read, the
     * decision and the arm are one step: a choice stored before this is called cannot be overwritten
     * by an arm derived from the hour it replaced.
     *
     * A band open but unsent when the alarm was lost gets its nudge later today rather than tomorrow,
     * but not at once: these sites are usually the user opening the app or changing the hour, and a
     * "you haven't checked in" posted seconds later over that screen is noise. Never throws except to
     * cancel — a failed read arms the plain next checkpoint, and a cancelled one arms nothing,
     * leaving the alarm already set in place rather than replacing it with one that knows less.
     */
    suspend fun rearm() = passLock.withLock { armFromLedger() }

    /**
     * Cancels every nudge id. Clears the whole set rather than one, because this is called from paths
     * that cannot tell which nudge is posted — and a nudge asking for a check-in is stale the moment
     * one happens, whichever checkpoint sent it.
     */
    override fun retireAll() {
        Nudge.entries.forEach { notifier.cancel(it.notificationId) }
        NotificationIds.RETIRED_NUDGES.forEach { notifier.cancel(it) }
    }

    private suspend fun send(nudge: Nudge, nowMillis: Long): Nudge? {
        val copy = NudgeCatalog.copyFor(nudge)

        val posted = notifier.show(
            NotificationSpec(
                id = nudge.notificationId,
                channelId = NotificationChannels.NUDGE,
                title = strings.get(copy.titleRes),
                body = strings.get(copy.bodyRes),
                launchExtra = LaunchExtras.CHECK_IN,
            ),
        )
        // Notifications can be refused (permission revoked, notifications off, the channel blocked).
        // The attempt is recorded either way, flagged, so the checkpoint is spent for the day without
        // spending a slot in the daily cap on a nudge the user was never shown.
        sendLog().record(nudge, nowMillis, posted)
        return nudge.takeIf { posted }
    }

    /** Must be called holding [passLock]. Reads afresh, so it sees whatever the pass just recorded. */
    private suspend fun armFromLedger() {
        val snapshot = readSnapshot()
        if (snapshot == null) {
            armNextCheckpoint()
        } else {
            runCatching { alarms.arm(NudgeEligibility.nextAlarmAt(snapshot, timeSource.zone())) }
        }
    }

    private fun armNextCheckpoint() {
        runCatching {
            alarms.armNextCheckpoint(timeSource.nowMillis(), timeSource.zone(), reminderSettings.firstReminderHour())
        }
    }

    /** The snapshot, or null when it cannot be read. Cancellation propagates rather than reading as a failure. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun readSnapshot(): NudgeSnapshot? = try {
        buildSnapshot()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun buildSnapshot(): NudgeSnapshot {
        val now = timeSource.nowMillis()
        val zone = timeSource.zone()
        // The day and the hour from the one clock read: a second read for the date can land past
        // midnight while `now` did not, opening tomorrow's empty ledger with today's follow-up band.
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val today = local.toLocalDate()
        val todaySessions = repository.getSessionsByDate(today.format(dateFormatter))
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        // One query answers all three frequency rules, so they cannot disagree about where the day
        // started. This is the ledger's only read, and the only reason it exists.
        val attempts = sendLog().attemptsSince(startOfDay)
        val shown = attempts.filter { it.posted }

        return NudgeSnapshot(
            nowMillis = now,
            hourOfDay = local.hour,
            firstReminderHour = reminderSettings.firstReminderHour(),
            // Deliberately the day's rows and not `getActiveSession()`: a session still open from an
            // earlier day is a lost day-boundary close, not a user who is present. See the field.
            hasCheckedInToday = todaySessions.isNotEmpty(),
            spentToday = attempts.mapNotNullTo(mutableSetOf()) { Nudge.fromStoredKey(it.key) },
            shownToday = shown.size,
            lastShownAtMs = shown.maxOfOrNull { it.atMillis },
        )
    }
}
