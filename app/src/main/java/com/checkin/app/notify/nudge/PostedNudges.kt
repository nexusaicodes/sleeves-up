package com.checkin.app.notify.nudge

/**
 * Clears a habit's nudge from the tray.
 *
 * A seam with one method, because it has one caller-visible job and callers that must not know how
 * nudges are posted: the nudge tap in `MainActivity`, and every check-in writer. A nudge asking for
 * a check-in is stale the moment that habit gets one — left posted, tapping it later runs the user
 * through the full presence gate and then resolves to nothing, which reads as a check-in that
 * silently failed. Only that habit's: another habit's reminder is still true.
 *
 * Takes the row id, not the uuid, because a notification id is built from it (see
 * `NotificationIds.habitNudge`) and every caller already holds the session's habit id.
 *
 * Cancelling only — this seam records nothing; see [NudgeSendLog] for why.
 */
interface PostedNudges {
    fun retire(habitId: Long)
}
