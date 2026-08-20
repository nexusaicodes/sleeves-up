package com.checkin.app.ui.presence

/**
 * What the camera hardware will tell us about faces, and what counts as someone being there.
 *
 * Detection is done by the camera HAL and arrives as capture-result metadata, so the app reads a
 * face count off the preview stream rather than taking a picture and analysing it. Nothing here
 * touches Android, which is what lets the two rules that decide a presence check be unit-tested.
 */
object FaceDetectSupport {

    /** `CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF` — a mode, but not a detector. */
    const val OFF = 0

    /** `CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE` — bounds and a score. */
    const val SIMPLE = 1

    /** `CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL` — adds landmarks and stable ids. */
    const val FULL = 2

    /**
     * Out of 100. A presence check only has to separate a face from an empty frame, so this sits
     * mid-scale: high enough that the wall behind a desk does not pass, low enough that poor light
     * does not lock the user out of their own check-in.
     */
    const val MIN_SCORE = 50

    /**
     * The mode to request, or null when the camera cannot detect faces at all.
     *
     * [SIMPLE] is preferred over [FULL] because the gate reads only how many faces are present —
     * landmarks and tracking ids are work the HAL would do and nothing would look at. Both are
     * checked because the platform does not guarantee a camera offering [FULL] also offers [SIMPLE].
     * `OFF` is a mode but not a detector, so a camera offering only that returns null.
     */
    fun preferredMode(available: IntArray?): Int? = when {
        available == null -> null
        available.contains(SIMPLE) -> SIMPLE
        available.contains(FULL) -> FULL
        else -> null
    }

    /**
     * Whether the mode the HAL reports back on a capture result is one that actually detects.
     *
     * [preferredMode] reads what the camera advertises; this reads what it delivered. A camera that
     * lists [SIMPLE] and then runs with `OFF` reports no faces however long the user waits, which is
     * indistinguishable from an empty frame and turns the attempt ladder into a countdown that
     * cannot be won — the state the immediate device-unlock offer exists to avoid.
     */
    fun isDetecting(appliedMode: Int?): Boolean = appliedMode != null && appliedMode != OFF

    /** How many of the reported faces are confident enough to count as someone being there. */
    fun facesPresent(scores: IntArray): Int = scores.count { it >= MIN_SCORE }
}
