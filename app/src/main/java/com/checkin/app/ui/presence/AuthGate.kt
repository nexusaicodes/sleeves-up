package com.checkin.app.ui.presence

/**
 * When the device-unlock fallback is offered to a user the camera is not finding.
 *
 * The presence check completes itself the moment the camera reports a face, so there is no attempt
 * for the user to spend and nothing to count. Elapsed time is what unlocks the fallback instead, and
 * something has to: a camera that is detecting perfectly well but never resolves *this* user — an
 * unlit room, an obscured lens, a face it simply will not find — would otherwise leave the gate with
 * no exit but Dismiss, and every check-in and check-out in the app comes through here.
 */
object AuthGate {

    /**
     * How long the gate looks without finding a face before device unlock is offered.
     *
     * Long enough that a user raising the phone into position never sees the escape hatch at all,
     * and short enough that one the camera cannot resolve is not left waiting on it. Where the wait
     * is measured from is the call site's decision, and a load-bearing one — see the effect that
     * spends this budget in `PresenceCheckScreen`.
     */
    const val BIOMETRIC_FALLBACK_AFTER_MS = 10_000L
}
