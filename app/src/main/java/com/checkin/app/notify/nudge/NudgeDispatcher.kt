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
     * Arms from the snapshot the pass already read, in a `finally`, so a pass that throws still
     * schedules the next one — without that, one bad pass would end the chain for good. Whatever the
     * pass selected counts as spent for the arm even when the post was refused: it was due now, so
     * arming for it again would fire at once, be refused again, and spin. A refused nudge therefore
     * waits for the next band, as it always has.
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
        var attempted: Nudge? = null
        var sent: Nudge? = null
        try {
            attempted = NudgeEligibility.select(snapshot)
            sent = attempted?.let { send(it, snapshot.nowMillis) }
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
            armAfterPass(snapshot, attempted, sent)
        }
        sent
    }

    /**
     * Arms the alarm for [NudgeEligibility.nextEligibleAt] without running a pass — for every site
     * that has just lost or moved the alarm: process start, boot and package replace, and a new hour.
     * Serialised with [runOnce] on the same lock, so the hour read, the decision and the arm are one
     * step: a choice stored before this is called cannot be overwritten by an arm derived from the
     * hour it replaced.
     *
     * An instant already reached is armed as is and fires at once, which is the point: a band open
     * but unsent when the alarm was lost gets its nudge now rather than tomorrow, and the pass that
     * fire runs arms past it. Never throws except to cancel — a failed read arms the plain next
     * checkpoint, and a cancelled one arms nothing, leaving the alarm already set in place rather
     * than replacing it with one that knows less.
     */
    suspend fun rearm() = passLock.withLock {
        val snapshot = readSnapshot()
        if (snapshot == null) armNextCheckpoint() else arm(NudgeEligibility.nextEligibleAt(snapshot, timeSource.zone()))
    }

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
        // Notifications can be refused (permission revoked). Recording a send that never reached the
        // tray would spend a slot in the daily cap on a nudge the user was never shown.
        if (!posted) return null

        sendLog().record(nudge, nowMillis)
        return nudge
    }

    /**
     * After a pass, nothing is due now — whatever was has just been tried — so an instant not ahead
     * of now could only mean [NudgeEligibility.select] and [NudgeEligibility.nextEligibleAt]
     * disagree. Arming it would fire at once into the same pass, so the plain next checkpoint is
     * armed instead.
     */
    private fun armAfterPass(snapshot: NudgeSnapshot, attempted: Nudge?, sent: Nudge?) {
        val after = when {
            sent != null -> snapshot.copy(
                alreadySentToday = snapshot.alreadySentToday + sent,
                shownToday = snapshot.shownToday + 1,
                lastShownAtMs = snapshot.nowMillis,
            )
            attempted != null -> snapshot.copy(alreadySentToday = snapshot.alreadySentToday + attempted)
            else -> snapshot
        }
        val at = NudgeEligibility.nextEligibleAt(after, timeSource.zone())
        if (at > snapshot.nowMillis) arm(at) else armNextCheckpoint(snapshot.firstReminderHour)
    }

    private fun arm(atMs: Long) {
        runCatching { alarms.arm(atMs) }
    }

    private fun armNextCheckpoint(firstHour: Int? = null) {
        runCatching {
            alarms.armNextCheckpoint(
                timeSource.nowMillis(),
                timeSource.zone(),
                firstHour ?: reminderSettings.firstReminderHour(),
            )
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
        val today = timeSource.today()
        val zone = timeSource.zone()
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        val todaySessions = repository.getSessionsByDate(today.format(dateFormatter))
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        // One query answers all three frequency rules, so they cannot disagree about where the day
        // started. This is the ledger's only read, and the only reason it exists.
        val sent = sendLog().sentSince(startOfDay)

        return NudgeSnapshot(
            nowMillis = now,
            hourOfDay = hour,
            firstReminderHour = reminderSettings.firstReminderHour(),
            // Deliberately the day's rows and not `getActiveSession()`: a session still open from an
            // earlier day is a lost day-boundary close, not a user who is present. See the field.
            hasCheckedInToday = todaySessions.isNotEmpty(),
            alreadySentToday = sent.mapNotNullTo(mutableSetOf()) { Nudge.fromStoredKey(it.key) },
            shownToday = sent.size,
            lastShownAtMs = sent.maxOfOrNull { it.atMillis },
        )
    }
}
