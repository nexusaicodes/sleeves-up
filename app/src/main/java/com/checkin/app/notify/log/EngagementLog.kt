package com.checkin.app.notify.log

import com.checkin.app.notify.engagement.Nudge
import kotlinx.coroutines.flow.Flow

/**
 * Records what each notification did, so nudges can be judged on whether they actually produce
 * check-ins rather than on whether they were sent.
 *
 * Conversion is attributed in Kotlin rather than SQL because the sessions table lives in a different
 * database — the deliberate cost of keeping engagement data isolated from session data.
 */
/**
 * One nudge showing, reduced to what the frequency rules need: which nudge, and when.
 *
 * [key] is the stored enum name rather than a [Nudge], because a showing of a since-retired nudge
 * still counts against the daily cap even though nothing can map it back to a constant.
 */
data class NudgeShowing(val key: String, val atMillis: Long)

interface EngagementLog {
    suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long)

    /**
     * Records a session-reminder event. Kept to its own entry point rather than widening [record],
     * because everything a nudge needs — a variant, a place in the frequency cap, eligibility for
     * conversion credit — is exactly what a reminder must not have.
     */
    suspend fun recordPresenceCheck(event: EngagementEventType, atMillis: Long)

    /**
     * Records an infrastructure lifecycle event — the foreground service, a session alarm, or the
     * nudge checkpoint alarm — with a short free-text [detail] stored in the key column (a reason, or
     * an instant). Scoped to [EngagementSource.SERVICE] for the same reason presence rows are scoped:
     * it must be invisible to the nudge cap and to attribution. A checkpoint row belongs here rather
     * than under [EngagementSource.NUDGE] precisely because it records that the *wake-up* happened,
     * which is not a nudge impression and must never be counted as one.
     */
    suspend fun recordService(event: ServiceEventType, atMillis: Long, detail: String = "")

    /**
     * Marks a check-in at [atMillis] as converted if a nudge was shown within [windowMs] before it
     * and hasn't already been credited. Returns the nudge credited, or null if the check-in was
     * unprompted.
     */
    suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge?

    /**
     * Records a tap against whichever nudge was most recently shown within [windowMs]. The tap
     * itself carries no identity, so attribution has to come from the log rather than the intent.
     */
    suspend fun recordOpenedForLastShown(atMillis: Long, windowMs: Long): Nudge?

    /**
     * Every nudge shown since [since], oldest first — the whole input to the frequency rules.
     *
     * Returned as the set rather than as a count because the rules ask three things of it: how many
     * were sent (the daily cap), which ones (so a checkpoint cannot be sent twice), and when the last
     * one landed (the minimum gap). Answering those from one query is what stops them disagreeing.
     */
    suspend fun shownNudgesSince(since: Long): List<NudgeShowing>

    fun recent(limit: Int): Flow<List<EngagementEvent>>

    suspend fun clear()

    /** Drops events older than [before]; the log is analytics, not an audit trail. */
    suspend fun prune(before: Long)
}

class RoomEngagementLog(private val dao: EngagementEventDao) : EngagementLog {

    override suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long) {
        dao.insert(
            EngagementEvent(
                at = atMillis,
                key = nudge.name,
                variant = variant,
                event = event.name,
                source = EngagementSource.NUDGE.name,
            ),
        )
    }

    override suspend fun recordPresenceCheck(event: EngagementEventType, atMillis: Long) {
        dao.insert(
            EngagementEvent(
                at = atMillis,
                key = PRESENCE_CHECK_KEY,
                variant = 0,
                event = event.name,
                source = EngagementSource.PRESENCE.name,
            ),
        )
    }

    override suspend fun recordService(event: ServiceEventType, atMillis: Long, detail: String) {
        dao.insert(
            EngagementEvent(
                at = atMillis,
                key = detail,
                variant = 0,
                event = event.name,
                source = EngagementSource.SERVICE.name,
            ),
        )
    }

    override suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val latestConverted = dao.latestOfType(
            EngagementEventType.CONVERTED.name,
            EngagementSource.NUDGE.name,
            shown.at,
        )?.at
        // Any nudge swiped away since the showing, not only this one: `latestOfType` is not scoped by
        // key, and a dismissal inside the window reads as a rejection whichever nudge it landed on.
        // Under-crediting is the direction this log errs in deliberately.
        val latestDismissed = dao.latestOfType(
            EngagementEventType.DISMISSED.name,
            EngagementSource.NUDGE.name,
            shown.at,
        )?.at
        if (!AttributionRules.canCredit(shown.at, atMillis, windowMs, latestConverted, latestDismissed)) {
            return null
        }

        val nudge = shown.toNudge() ?: return null
        record(nudge, shown.variant, EngagementEventType.CONVERTED, atMillis)
        return nudge
    }

    override suspend fun recordOpenedForLastShown(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val nudge = shown.toNudge() ?: return null
        record(nudge, shown.variant, EngagementEventType.OPENED, atMillis)
        return nudge
    }

    private suspend fun lastShownWithin(atMillis: Long, windowMs: Long): EngagementEvent? = dao.latestOfType(
        EngagementEventType.SHOWN.name,
        EngagementSource.NUDGE.name,
        atMillis - windowMs,
    )

    /** Null when the stored name no longer maps to a nudge — a renamed or removed experiment. */
    private fun EngagementEvent.toNudge(): Nudge? = Nudge.entries.firstOrNull { it.name == key }

    override suspend fun shownNudgesSince(since: Long): List<NudgeShowing> =
        dao.ofTypeSince(EngagementEventType.SHOWN.name, EngagementSource.NUDGE.name, since)
            .map { NudgeShowing(it.key, it.at) }

    override fun recent(limit: Int): Flow<List<EngagementEvent>> = dao.recent(limit)

    override suspend fun clear() = dao.clear()

    override suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
