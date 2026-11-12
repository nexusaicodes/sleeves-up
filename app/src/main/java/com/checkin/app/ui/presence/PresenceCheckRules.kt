package com.checkin.app.ui.presence

/**
 * How long the gate looks without finding a face before device unlock is offered.
 *
 * The presence check completes itself the moment the camera reports a face, so there is no attempt
 * for the user to spend and nothing to count. Elapsed time is what unlocks the fallback instead, and
 * something has to: a camera detecting perfectly well but never resolving *this* user — an unlit
 * room, an obscured lens, a face it simply will not find — would otherwise leave the gate with no
 * exit but Dismiss, and every check-in and check-out in the app comes through here.
 *
 * Long enough that a user raising the phone into position never sees the escape hatch at all, and
 * short enough that one the camera cannot resolve is not left waiting on it. Where the wait is
 * measured **from** is the call site's decision and a load-bearing one — see the effect that spends
 * this budget in [PresenceCheckScreen].
 */
internal const val DEVICE_UNLOCK_OFFERED_AFTER_MS = 10_000L

/** The decisions [PresenceCheckScreen] makes over the readings, the clock and the prompt. */
object PresenceCheckRules {

    /**
     * Whether the check passes now. Never while the device-unlock prompt is up: the camera streams
     * behind it, and a face found mid-prompt would pass under a dialog the user is still answering.
     */
    fun passes(facePresent: Boolean, usingDeviceUnlock: Boolean): Boolean = facePresent && !usingDeviceUnlock

    /**
     * What is left of the wait before device unlock is offered, from the gate opening ([startedAtMs],
     * saved across rotation). Clamped at both ends: a reboot resets the clock [nowMs] reads.
     */
    fun remainingWaitMs(startedAtMs: Long, nowMs: Long): Long =
        (DEVICE_UNLOCK_OFFERED_AFTER_MS - (nowMs - startedAtMs)).coerceIn(0L, DEVICE_UNLOCK_OFFERED_AFTER_MS)

    /**
     * Whether the fallback button shows: once [searchTimedOut] (latched by the screen), and not after
     * the check passed or while a prompt is up, so no second authentication is raised.
     */
    fun offersFallback(passed: Boolean, usingDeviceUnlock: Boolean, searchTimedOut: Boolean): Boolean =
        !passed && !usingDeviceUnlock && searchTimedOut
}
