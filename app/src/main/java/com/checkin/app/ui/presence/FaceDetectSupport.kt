package com.checkin.app.ui.presence

/**
 * What the camera hardware will tell us about faces, and what counts as someone being there.
 *
 * Detection is done by the camera HAL and arrives as capture-result metadata, so the app reads a
 * face count off the preview stream rather than taking a picture and analysing it. Nothing here
 * touches Android, which is what lets the rules that decide a presence check be unit-tested.
 */
object FaceDetectSupport {

    /** `CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF` — a mode, but not a detector. */
    const val OFF = 0

    /** `CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE` — bounds and a score. */
    const val SIMPLE = 1

    /** `CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL` — adds landmarks and stable ids. */
    const val FULL = 2

    /**
     * Consecutive results showing no detection before the camera is written off as unable to run a
     * check at all.
     *
     * The first results after session configuration can legitimately carry `OFF`, or omit the mode
     * entirely, while the HAL applies the request the session was built with. Concluding from the
     * very first of those sends a working camera to the device-unlock fallback with a live detector
     * running behind it. At preview frame rates this grace is a fraction of a second, so a camera
     * that really does run `OFF` still reaches the fallback effectively immediately.
     */
    const val NON_DETECTING_RESULTS = 10

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
     * indistinguishable from a wall and would leave the user watching a countdown that cannot be
     * won — the state the immediate device-unlock offer exists to avoid.
     */
    fun isDetecting(appliedMode: Int?): Boolean = appliedMode != null && appliedMode != OFF

    /**
     * Whether one capture result shows detection running, reading the faces as well as the mode.
     *
     * A HAL is free to disagree with itself, and where it does the faces are the stronger evidence:
     * a result carrying face rectangles is detecting whatever its mode field claims. Taking the mode
     * alone would strand such a camera on the fallback with a working detector behind it.
     */
    fun resultIsDetecting(appliedMode: Int?, facesReported: Int): Boolean =
        isDetecting(appliedMode) || someonePresent(facesReported)

    /**
     * Whether a frame's reported faces mean someone is there. Every reported face counts.
     *
     * **The confidence score is deliberately not read, and a floor must not be reintroduced.** The
     * platform gives it no meaning a caller can rely on. `Face.getScore` is declared
     * `@IntRange(from = SCORE_MIN, to = SCORE_MAX)` with `SCORE_MIN = 1`, and the `SIMPLE` contract
     * spells out precisely what that mode does not support — the face id is `ID_UNSUPPORTED` and the
     * eye and mouth positions are null — while requiring nothing whatever of the score. A HAL that
     * does not grade is therefore free to return the legal minimum forever, and that is exactly what
     * a MediaTek front camera was measured doing: every genuine face, squarely framed and well lit
     * with bounds tracking the user as they moved, came back scored **1**. A floor of 50 rejected
     * all of it, which is what made the gate look like it could not see a face at all — and a floor
     * of 20 rejects the very same frames, so lowering it is not a fix either.
     *
     * What is left is the rectangle, and it is the honest signal: the HAL emits one only where it
     * already believes a face is. The same probe showed empty frames reported as zero faces and the
     * user's arrival reported the very next second, so the detector discriminates perfectly well —
     * it is only the grade that is uninformative. A wall was never going to pass this; a user was.
     */
    fun someonePresent(facesReported: Int): Boolean = facesReported > 0
}
