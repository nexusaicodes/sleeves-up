package com.checkin.app.notify.nudge

/**
 * One nudge attempt, reduced to what the frequency rules and the rotation read: which habit, which
 * nudge, when, whether the post reached the tray, and the [NudgeCatalog] keys of its wording.
 *
 * [key] is the stored name rather than a [Nudge] for the reason [NudgeSend.nudge] gives, and
 * [habitUuid] is empty on a row written before habits existed — see [NudgeSend.habitUuid].
 */
data class NudgeSending(
    val habitUuid: String,
    val key: String,
    val atMillis: Long,
    val posted: Boolean,
    val titleKey: String? = null,
    val bodyKey: String? = null,
)

/**
 * The app's whole interface to the send ledger, and the ledger's whole purpose: remembering what was
 * already sent today so the next pass does not send it again.
 *
 * This is scheduler state, not analytics. **Nothing may be added here that no code path reads**: the
 * app makes no network calls and ships no surface that displays this data, so a row written for
 * someone to look at later is a row nobody looks at.
 */
interface NudgeSendLog {

    /**
     * Records an attempt to post [nudge] for the habit [habitUuid] at [atMillis], whether [posted] it
     * reached the tray, and the wording it carried. Undefaulted: a caller that forgot [posted] would
     * either space the next reminder off a notification nobody saw or leave a refused one to be
     * retried on every re-arm, and one that forgot the wording would repeat it the next day.
     */
    @Suppress("LongParameterList")
    suspend fun record(
        habitUuid: String,
        nudge: Nudge,
        atMillis: Long,
        posted: Boolean,
        titleKey: String?,
        bodyKey: String?,
    )

    /** Every attempt since [since], every habit's, oldest first — the whole input to the frequency rules. */
    suspend fun attemptsSince(since: Long): List<NudgeSending>

    /** [habitUuid]'s posts since [since], refusals excluded — the input to the wording rotation. */
    suspend fun shownSince(habitUuid: String, since: Long): List<NudgeSending>

    /** Drops rows older than [before]. The rotation reads furthest back; this is just tidying. */
    suspend fun prune(before: Long)
}

class RoomNudgeSendLog(private val dao: NudgeSendDao) : NudgeSendLog {

    override suspend fun record(
        habitUuid: String,
        nudge: Nudge,
        atMillis: Long,
        posted: Boolean,
        titleKey: String?,
        bodyKey: String?,
    ) {
        dao.insert(
            NudgeSend(
                nudge = nudge.name,
                at = atMillis,
                posted = posted,
                habitUuid = habitUuid,
                titleKey = titleKey,
                bodyKey = bodyKey,
            ),
        )
    }

    override suspend fun attemptsSince(since: Long): List<NudgeSending> = dao.attemptsSince(since).map {
        it.toSending()
    }

    override suspend fun shownSince(habitUuid: String, since: Long): List<NudgeSending> =
        dao.shownSince(habitUuid, since).map { it.toSending() }

    private fun NudgeSend.toSending() = NudgeSending(habitUuid, nudge, at, posted, titleKey, bodyKey)

    override suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
