package com.checkin.app.notify.nudge

/**
 * One nudge attempt, reduced to what the frequency rules read: which nudge, when, and whether the
 * post reached the tray.
 *
 * [key] is the stored name rather than a [Nudge] for the reason [NudgeSend.nudge] gives.
 */
data class NudgeSending(val key: String, val atMillis: Long, val posted: Boolean)

/**
 * The app's whole interface to the send ledger, and the ledger's whole purpose: remembering what was
 * already sent today so the next pass does not send it again.
 *
 * This is scheduler state, not analytics. It replaced a log that also recorded opens, dismissals,
 * conversions, session reminders and foreground-service lifecycle events — none of which anything
 * ever read back. **Nothing may be added here that no code path reads**: the app makes no network
 * calls and ships no surface that displays this data, so a row written for someone to look at later
 * is a row nobody looks at.
 */
interface NudgeSendLog {

    /**
     * Records an attempt to post [nudge] at [atMillis], and whether [posted] it reached the tray.
     * Undefaulted: a caller that forgot to say would either spend a cap slot on a notification nobody
     * saw or leave a refused checkpoint to be retried on every re-arm.
     */
    suspend fun record(nudge: Nudge, atMillis: Long, posted: Boolean)

    /** Every attempt since [since], oldest first — the whole input to the frequency rules. */
    suspend fun attemptsSince(since: Long): List<NudgeSending>

    /** Drops rows older than [before]. Nothing reads past the start of today; this is just tidying. */
    suspend fun prune(before: Long)
}

class RoomNudgeSendLog(private val dao: NudgeSendDao) : NudgeSendLog {

    override suspend fun record(nudge: Nudge, atMillis: Long, posted: Boolean) {
        dao.insert(NudgeSend(nudge = nudge.name, at = atMillis, posted = posted))
    }

    override suspend fun attemptsSince(since: Long): List<NudgeSending> =
        dao.attemptsSince(since).map { NudgeSending(it.nudge, it.at, it.posted) }

    override suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
