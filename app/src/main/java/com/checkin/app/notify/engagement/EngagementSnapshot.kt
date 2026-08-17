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
    /** A session row is open right now. Raw fact — see [isPresent] for the one the rules use. */
    val isCheckedIn: Boolean,
    /**
     * That open session is already past its own day boundary.
     *
     * A session cannot legitimately outlive the midnight of the day it began on: the day-boundary
     * alarm closes it. One that has is a session whose alarms were lost — a force stop and a package
     * replace both cancel them — and nothing re-arms them until the app is opened. Left counting as
     * "checked in", it suppresses every nudge for as long as it stays open, which is indefinitely,
     * and the nudge that would surface the problem is the one it silences.
     */
    val openSessionOverdue: Boolean = false,
    /** Any session, open or closed, exists for today. */
    val hasCheckedInToday: Boolean,
    /** Nudges already shown in the current day. */
    val shownToday: Int = 0,
    val config: NudgeConfig = NudgeConfig(),
) {
    /**
     * Whether an open session is evidence the user is actually here. An overdue one is evidence of a
     * lost alarm instead, so it does not hold a nudge back.
     */
    val isPresent: Boolean get() = isCheckedIn && !openSessionOverdue
}

/** Tunables for the eligibility rules — the surface an engagement experiment varies. */
data class NudgeConfig(
    /**
     * The only frequency bound there is, and the only one there should be.
     *
     * Two, not one: the checkpoints exist because a single delivery can slip, and a cap of one means
     * the first checkpoint to land consumes the day even when the user has gone on to spend all of it
     * without checking in. Two is still bounded — the checkpoints are hours apart, so the worst case
     * is a morning message and an evening one, and [NudgeDispatcher] cancels the earlier before
     * posting the later, so at most one is ever in the tray.
     */
    val maxPerDay: Int = 2,
)
