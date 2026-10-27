package com.checkin.app.notify

/**
 * The extras a notification or the home-screen widget sets on its tap intent to tell
 * [com.checkin.app.MainActivity] what the user was answering: a boolean per action, each of which opens
 * the presence gate, and the habit a nudge was about. None is ever read by a service. Why this sits in
 * `notify/` rather than `nudge/`: see the tree map in [Notifier].
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

    // Never reuse "check_out" for a new extra: older builds posted it, and an extra rides an
    // already-posted notification's PendingIntent across an update.

    /** Set by a nudge tap. Opens a session for [HABIT_UUID]'s habit once the gate passes. */
    const val CHECK_IN = "check_in"

    /**
     * Which habit a nudge tap is about, by uuid. **Absent on a nudge posted before habits existed** —
     * an update can land with one still in the tray — and absent means the seed habit, which is the
     * record that nudge was about.
     */
    const val HABIT_UUID = "habit_uuid"

    /**
     * Set by the home-screen widget's button, and the only thing it carries: what the tap opens — the
     * open session's check-out, the gate for the one habit, or the picker — is decided from the rows
     * at the tap, never from what the widget was showing.
     */
    const val WIDGET_TAP = "widget_tap"
}
