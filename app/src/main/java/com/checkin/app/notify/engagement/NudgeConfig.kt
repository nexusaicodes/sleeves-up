package com.checkin.app.notify.engagement

/**
 * How often a nudge may be sent. Its own file because this is what anyone tuning nudge cadence is
 * looking for, and the three files they would try first — [NudgeEligibility], which reads these
 * values but does not own them, [NudgeSchedule], which owns the checkpoint hours, and
 * [NudgeCatalog], which owns the copy — are all dead ends.
 *
 * Frequency is bounded three ways and the checkpoint hours are **not** one of them: this type holds
 * two of the bounds, and [NudgeSnapshot.alreadySentToday] is the third.
 *
 * **Cadence only.** The other nudge constant a reader may be hunting is the attribution window —
 * how long after a nudge a check-in still counts as caused by it — and that is not cadence, so it
 * lives with the reporter that applies it: `DefaultEngagementReporter.CONVERSION_WINDOW_MS`.
 */
data class NudgeConfig(
    /**
     * The coarse frequency bound: how many nudges a calendar day may carry at most.
     *
     * Two, not one: the checkpoints exist because a single delivery can slip, and a cap of one means
     * the first checkpoint to land consumes the day even when the user has gone on to spend all of it
     * without checking in. Per-checkpoint deduplication is what keeps those two from being the same
     * message twice — see [NudgeSnapshot.alreadySentToday].
     */
    val maxPerDay: Int = 2,
    /**
     * The minimum spacing between two nudges, whichever checkpoints they belong to.
     *
     * The checkpoint hours look like they already guarantee this, and they do not: the alarm is
     * `setAndAllowWhileIdle`, so Doze can defer a delivery by hours. A morning checkpoint landing at
     * 13:57 would otherwise be followed by the afternoon one at 14:00 — two high-importance messages
     * three minutes apart, and the whole day's budget spent before it was half over.
     *
     * Three hours is under the smallest gap between two checkpoints, so it never blocks a delivery
     * that arrived on time, and well over the window in which a repeat reads as a malfunction.
     *
     * Note this defers a checkpoint rather than cancelling it: the nudge is not marked sent, so the
     * next hourly [NudgeWorker] pass still inside the band picks it up once the gap has elapsed.
     * That is what keeps the rule from silently costing a user the day's second message.
     */
    val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {
    companion object {
        private const val HOUR_MS = 60L * 60L * 1_000L
        const val DEFAULT_MIN_GAP_MS = 3L * HOUR_MS
    }
}
