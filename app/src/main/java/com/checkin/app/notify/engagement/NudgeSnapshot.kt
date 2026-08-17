package com.checkin.app.notify.engagement

/**
 * Everything [NudgeEligibility] is allowed to look at, gathered by the caller. Passing a plain value
 * object — rather than letting the decision reach into a repository or a clock — is what keeps the
 * rules pure, exhaustively testable, and unable to touch tracking logic.
 */
data class NudgeSnapshot(
    val nowMillis: Long,
    /** Local hour of day, 0-23. */
    val hourOfDay: Int,
    /**
     * A session exists for today, open or closed.
     *
     * This is the only presence question the rules ask, and it is deliberately about *today's record*
     * rather than about whether a session is open right now. A session still open from an earlier day
     * is not evidence the user is here: it is a session whose day-boundary close was lost to a force
     * stop or a package replace. Suppressing on "a row is open" left such a session silencing every
     * nudge indefinitely, on exactly the device that had stopped being opened — and the nudge it
     * silenced was the one that would have surfaced the problem.
     */
    val hasCheckedInToday: Boolean,
    /**
     * The nudges already shown today, so a checkpoint cannot be sent twice.
     *
     * Counting sends alone is not enough: the checkpoint bands are hours wide and [NudgeWorker] runs
     * hourly, so a repeat pass inside the same band re-selects the same nudge, reposts identical copy
     * under the same id, and spends a slot the later checkpoints needed.
     */
    val alreadySentToday: Set<Nudge> = emptySet(),
    /** Nudges already shown in the current day, including any whose name no longer maps to a [Nudge]. */
    val shownToday: Int = 0,
    /** When the most recent nudge was shown, or null if none has been today. */
    val lastShownAtMs: Long? = null,
    val config: NudgeConfig = NudgeConfig(),
)

/** Tunables for the eligibility rules — the surface an engagement experiment varies. */
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
