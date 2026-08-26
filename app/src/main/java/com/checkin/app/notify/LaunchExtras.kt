package com.checkin.app.notify

/**
 * The boolean extras a notification sets on its tap intent to tell [com.checkin.app.MainActivity]
 * what the user was answering. Both open the presence gate; neither is ever read by a service.
 *
 * They live here, beside [EngagementTag], because they are the other half of the same thing — what
 * rides a notification's `PendingIntent` — and because all three senders already deal in this
 * package while the Activity that reads them already imports from it.
 *
 * `MainActivity.handlePresenceIntent` consumes whichever is set and clears it, so an Activity
 * recreation cannot replay a tap the user has already answered.
 */
object LaunchExtras {

    /**
     * Set by the ongoing timer notification's Check Out action and by the session reminder's.
     * Checks the active session out once the gate passes — check-out is never un-gated.
     */
    const val CHECK_OUT = "check_out"

    /** Set by an engagement nudge tap. Opens a session once the gate passes. */
    const val CHECK_IN = "check_in"
}
