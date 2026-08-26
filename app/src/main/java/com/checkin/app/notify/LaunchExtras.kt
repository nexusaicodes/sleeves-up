package com.checkin.app.notify

/**
 * The boolean extras a notification sets on its tap intent to tell [com.checkin.app.MainActivity]
 * what the user was answering. Both open the presence gate; neither is ever read by a service.
 *
 * They live here rather than in `nudge/` because all three senders already deal in this
 * package — the timer, the session reminder and every nudge — while the Activity that reads them
 * already imports from it.
 *
 * `MainActivity.handlePresenceIntent` consumes whichever is set and clears it, so an Activity
 * recreation cannot replay a tap the user has already answered.
 */
object LaunchExtras {

    /**
     * Set by the ongoing timer notification's Check Out action. Checks the active session out once
     * the gate passes — check-out is never un-gated — and records it as
     * [ClosedBy.TIMER_NOTIFICATION][com.checkin.app.data.local.ClosedBy.TIMER_NOTIFICATION].
     */
    const val CHECK_OUT_FROM_TIMER = "check_out_from_timer"

    /**
     * Set by the session reminder's Check Out action. Same effect as [CHECK_OUT_FROM_TIMER], and a
     * separate key only so the export can tell the two apart: the reminder exists to catch a session
     * the user has stopped noticing, so a check-out from it means something the timer's does not.
     */
    const val CHECK_OUT_FROM_REMINDER = "check_out_from_reminder"

    // The single key both of the above replaced was "check_out". It is not declared here: nothing
    // reads it, and a private constant kept only to describe a string is the unreachable weight this
    // repo deletes on sight. Worth knowing only if a future extra reaches for that spelling — an
    // extra rides an already-posted notification's PendingIntent, so on the one update where a
    // notification is live in the tray, its Check Out action still carries it.

    /** Set by a nudge tap. Opens a session once the gate passes. */
    const val CHECK_IN = "check_in"
}
