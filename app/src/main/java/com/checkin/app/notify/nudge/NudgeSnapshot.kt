package com.checkin.app.notify.nudge

/**
 * Everything [NudgeEligibility] is allowed to look at, gathered by the caller. Passing a plain value
 * object — rather than letting the decision reach into a repository or a clock — is what keeps the
 * rules pure, exhaustively testable, and unable to touch tracking logic.
 *
 * The caller is [NudgeDispatcher], which does every read this holds the results of; that is where to
 * look for how a field is actually derived.
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
