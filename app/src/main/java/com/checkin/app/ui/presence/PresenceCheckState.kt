package com.checkin.app.ui.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the camera has said during one presence check, folded from its capture results. No Android
 * here, so the rules [PresenceCheckScreen] runs on are unit-tested (`PresenceCheckStateTest`); the
 * screen only binds the camera, forwards each result, and draws.
 *
 * Thread-safe: the capture callback writes from a camera thread, and the screen reads on the main
 * one. StateFlows drop a write equal to what they hold, so a steady face or a steady empty frame
 * recomposes nothing.
 */
class CameraReadings {
    private val usable = MutableStateFlow<Boolean?>(null)
    private val face = MutableStateFlow(false)

    // Consecutive results showing no detection at all. Counted rather than acted on at the first,
    // because the results either side of session configuration can carry OFF on a camera that then
    // detects perfectly well.
    private val nonDetecting = AtomicInteger(0)

    /**
     * Null until the camera has been asked. False means no check can run here at all — no
     * face-detection mode, a mode advertised but not run, or a failed bind — the one state that
     * skips the wait before device unlock is offered, since the answer it waits for is known.
     */
    val cameraUsable: StateFlow<Boolean?> = usable.asStateFlow()

    /** Whether the latest result held a face. The whole check: the first frame setting it passes. */
    val facePresent: StateFlow<Boolean> = face.asStateFlow()

    /** The camera bound and is streaming. */
    fun onBound() {
        usable.value = true
    }

    /** No check can run: no provider, no detection mode on the front camera, or a failed bind. */
    fun onUnusable() {
        usable.value = false
    }

    /** One capture result: the mode the camera actually ran with, and the faces it reported. */
    fun onResult(appliedMode: Int?, facesReported: Int) {
        // What the camera advertised was read before the bind; this is what it actually runs with.
        // A HAL that lists a mode and then reports OFF returns no faces however long the user waits,
        // so it takes the same route as one that never offered detection — but only once it has said
        // so consistently.
        if (!FaceDetectSupport.resultIsDetecting(appliedMode, facesReported)) {
            // A non-detecting result reports nobody by definition, and writing that is load-bearing:
            // without it a `true` from before the user walked away survives a run of empty frames,
            // which the device-unlock prompt holds back long enough to be acted on stale.
            face.value = false
            if (nonDetecting.incrementAndGet() >= FaceDetectSupport.NON_DETECTING_RESULTS) {
                usable.value = false
            }
            return
        }
        nonDetecting.set(0)
        // Detection is running after all, so a camera written off by an earlier run of OFF results is
        // taken back rather than left on the fallback.
        usable.value = true
        face.value = FaceDetectSupport.someonePresent(facesReported)
    }

    /**
     * The screen stopped. CameraX unbinds at ON_STOP and results stop arriving, but these readings
     * survive it. A stale face is the one that matters: it is reachable while the device-unlock
     * prompt holds the check back, so a gate backgrounded with a face in frame and the prompt then
     * cancelled would pass on a frame from before the camera reopened. Clearing re-imposes the wait.
     */
    fun onStop() {
        face.value = false
        nonDetecting.set(0)
    }
}

/** The decisions [PresenceCheckScreen] makes over the readings, the clock and the prompt. */
object PresenceCheckRules {

    /**
     * Whether the check passes now. Never while the device-unlock prompt is up: the camera streams
     * behind it, and a face found mid-prompt would pass under a dialog the user is still answering.
     */
    fun passes(facePresent: Boolean, usingDeviceUnlock: Boolean): Boolean = facePresent && !usingDeviceUnlock

    /**
     * How much of the wait before device unlock is offered is left, measured from the gate opening
     * ([startedAtMs], saved across rotation) rather than the first result — a camera that binds and
     * never delivers would otherwise start no clock. Clamped at both ends: saved state can outlive a
     * reboot, which resets the elapsed-time clock [nowMs] is read from.
     */
    fun remainingWaitMs(startedAtMs: Long, nowMs: Long): Long =
        (DEVICE_UNLOCK_OFFERED_AFTER_MS - (nowMs - startedAtMs)).coerceIn(0L, DEVICE_UNLOCK_OFFERED_AFTER_MS)

    /**
     * Whether the fallback button shows, once [searchTimedOut] — the wait ran out, or the camera was
     * written off, which the screen latches so a recovering camera cannot take back a button the user
     * is already reaching for. Withdrawn once the check has passed and while a prompt is
     * outstanding, so no second authentication is raised against a gate already leaving — a success
     * never clears `usingDeviceUnlock`, so that half also covers the gap before the caller reacts.
     */
    fun offersFallback(passed: Boolean, usingDeviceUnlock: Boolean, searchTimedOut: Boolean): Boolean =
        !passed && !usingDeviceUnlock && searchTimedOut
}
