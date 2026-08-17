package com.checkin.app.notify.engagement

/**
 * Everything [NudgeEligibility] is allowed to look at, gathered by the caller. Passing a plain value
 * object — rather than letting the decision reach into a repository or a clock — is what keeps the
 * rules pure, exhaustively testable, and unable to touch tracking logic.
 */
data class EngagementSnapshot(
    val nowMillis: Long,
    /** Local hour of day, 0-23. */
    val hourOfDay: Int,
    /** A session is open right now. */
    val isCheckedIn: Boolean,
    /** Any session, open or closed, exists for today. */
    val hasCheckedInToday: Boolean,
    /** Nudges already shown in the current day. */
    val shownToday: Int = 0,
    val config: NudgeConfig = NudgeConfig(),
)

/** Tunables for the eligibility rules — the surface an engagement experiment varies. */
data class NudgeConfig(
    /**
     * [Nudge.NOT_CHECKED_IN_BY] can fire from this local hour onward.
     *
     * Deliberately not quoted in any user-facing string. Delivery is best-effort — the pass that
     * sends it runs hourly and is deferrable — so naming an exact time promises a punctuality the
     * app cannot keep, and changing the value would then silently make the copy wrong.
     */
    val notCheckedInByHour: Int = 10,
    /**
     * The only frequency bound there is, and the only one there should be. A per-nudge cooldown
     * beside it could suppress nothing the cap does not already suppress at one nudge a day, while
     * measuring a rolling window the cap does not — the two would disagree about where a day ends.
     */
    val maxPerDay: Int = 1,
)
