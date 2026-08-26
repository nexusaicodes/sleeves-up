package com.checkin.app.notify.nudge

/**
 * Clears whatever nudges are sitting in the tray.
 *
 * A seam with one method, because it has one caller-visible job and two callers that must not know
 * how nudges are posted: the nudge tap in `MainActivity`, and every check-in writer. A nudge asking
 * for a check-in is stale the moment one happens — left posted, tapping it later runs the user
 * through the full presence gate and then resolves to nothing, which reads as a check-in that
 * silently failed.
 *
 * This is what is left of a reporter that also recorded opens and conversions. The recording had no
 * reader and is gone; the cancelling was always the half that changed what the user sees.
 */
interface PostedNudges {
    fun retireAll()
}
