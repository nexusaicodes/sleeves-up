package com.checkin.app.notify.nudge

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
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

    /** Returns the nudge sent, or null when nothing was eligible or posting was refused. */
    suspend fun runOnce(): Nudge? = passLock.withLock {
        val snapshot = buildSnapshot()
        val nudge = NudgeEligibility.select(snapshot) ?: return@withLock null
        val sent = send(nudge, snapshot.nowMillis) ?: return@withLock null

        // Retire the day's earlier checkpoints, but only now that this one is actually up. They carry
        // distinct ids by design, so otherwise an evening nudge stacks under a morning one still in
        // the tray — two notifications saying the user hasn't checked in, which reads as a stuck loop
        // rather than as one message that came back. Clearing *before* the post would instead take a
        // still-actionable notification away on the pass where the post is refused.
        Nudge.entries.filter { it != sent }.forEach { notifier.cancel(it.notificationId) }
        sent
    }

    /**
     * Cancels every nudge id. Clears the whole set rather than one, because this is called from paths
     * that cannot tell which nudge is posted — and a nudge asking for a check-in is stale the moment
     * one happens, whichever checkpoint sent it.
     */
    override fun retireAll() {
        Nudge.entries.forEach { notifier.cancel(it.notificationId) }
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

    private suspend fun buildSnapshot(): NudgeSnapshot {
        val now = timeSource.nowMillis()
        val today = timeSource.today()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        val todaySessions = repository.getSessionsByDate(today.format(dateFormatter))
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // One query answers all three frequency rules, so they cannot disagree about where the day
        // started. This is the ledger's only read, and the only reason it exists.
        val sent = sendLog().sentSince(startOfDay)

        return NudgeSnapshot(
            nowMillis = now,
            hourOfDay = hour,
            // Deliberately the day's rows and not `getActiveSession()`: a session still open from an
            // earlier day is a lost day-boundary close, not a user who is present. See the field.
            hasCheckedInToday = todaySessions.isNotEmpty(),
            alreadySentToday = sent.mapNotNullTo(mutableSetOf()) { sending ->
                Nudge.entries.firstOrNull { it.name == sending.key }
            },
            shownToday = sent.size,
            lastShownAtMs = sent.maxOfOrNull { it.atMillis },
        )
    }
}
