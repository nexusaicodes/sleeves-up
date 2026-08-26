package com.checkin.app.notify.nudge

/**
 * One nudge send, reduced to what the frequency rules read: which nudge, and when.
 *
 * [key] is the stored name rather than a [Nudge] for the reason [NudgeSend.nudge] gives.
 */
data class NudgeSending(val key: String, val atMillis: Long)

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

    /** Records that [nudge] was posted at [atMillis]. Called only after a post actually succeeded. */
    suspend fun record(nudge: Nudge, atMillis: Long)

    /** Every send since [since], oldest first — the whole input to the frequency rules. */
    suspend fun sentSince(since: Long): List<NudgeSending>

    /** Drops sends older than [before]. Nothing reads past the start of today; this is just tidying. */
    suspend fun prune(before: Long)
}

class RoomNudgeSendLog(private val dao: NudgeSendDao) : NudgeSendLog {

    override suspend fun record(nudge: Nudge, atMillis: Long) {
        dao.insert(NudgeSend(nudge = nudge.name, at = atMillis))
    }

    override suspend fun sentSince(since: Long): List<NudgeSending> =
        dao.sentSince(since).map { NudgeSending(it.nudge, it.at) }

    override suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
