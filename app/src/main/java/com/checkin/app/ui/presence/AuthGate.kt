package com.checkin.app.ui.presence

/**
 * When the device-unlock fallback is offered to a user the camera is not finding.
 *
 * The presence check completes itself the moment the camera reports a face, so there is no attempt
 * for the user to spend and nothing to count. What used to unlock the fallback was three failed
 * confirm taps; with no tap, elapsed time is what replaces it. Something has to: a camera that is
 * detecting perfectly well but never resolves *this* user — an unlit room, an obscured lens, a face
 * it simply will not find — would otherwise leave the gate with no exit but Dismiss, and every
 * check-in and check-out in the app comes through here.
 */
object AuthGate {

    /**
     * How long the camera looks without finding a face before device unlock is offered.
     *
     * Counted from the first capture result rather than from composition, so a camera that is slow
     * to open does not spend the budget before it has looked at anything. It is deliberately longer
     * than a check takes to pass — a user raising the phone into position should never see the
     * escape hatch at all — and short enough that one who cannot pass is not left waiting on it.
     */
    const val BIOMETRIC_FALLBACK_AFTER_MS = 10_000L
}
