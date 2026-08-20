package com.checkin.app

import com.checkin.app.ui.presence.FaceDetectSupport
import com.checkin.app.ui.presence.FaceDetectSupport.FULL
import com.checkin.app.ui.presence.FaceDetectSupport.SIMPLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera pipeline itself cannot be exercised on a JVM suite, so the two decisions that are not
 * the camera's — which mode to ask for, and what counts as a face — are stated here.
 */
class FaceDetectSupportTest {

    /** `OFF` is a mode, not a detector. A camera offering only it can never pass the check. */
    @Test
    fun `a camera that only reports OFF supports no detection`() {
        assertNull(FaceDetectSupport.preferredMode(intArrayOf(0)))
    }

    @Test
    fun `no reported modes at all supports no detection`() {
        assertNull(FaceDetectSupport.preferredMode(null))
        assertNull(FaceDetectSupport.preferredMode(intArrayOf()))
    }

    /**
     * SIMPLE wins where both exist: the gate reads a count, and FULL's landmarks and tracking ids
     * are work the hardware would do for a caller that never looks at them.
     */
    @Test
    fun `simple is preferred over full`() {
        assertEquals(SIMPLE, FaceDetectSupport.preferredMode(intArrayOf(0, SIMPLE, FULL)))
    }

    /** The platform does not promise SIMPLE wherever FULL exists, so FULL alone must still work. */
    @Test
    fun `full is taken when it is the only detector offered`() {
        assertEquals(FULL, FaceDetectSupport.preferredMode(intArrayOf(0, FULL)))
    }

    @Test
    fun `an empty frame holds nobody`() {
        assertFalse(FaceDetectSupport.someonePresent(facesReported = 0))
    }

    /**
     * Every reported face counts, whatever the camera scored it. The score is not comparable across
     * HALs — a MediaTek front camera returned the defined minimum of 1 for every genuine, well-lit,
     * squarely-framed face — so any floor at all rejects those users outright while the rectangle
     * itself, which the HAL emits only where it believes a face is, discriminates perfectly well.
     */
    @Test
    fun `a reported face means someone is there`() {
        assertTrue(FaceDetectSupport.someonePresent(facesReported = 1))
        assertTrue(FaceDetectSupport.someonePresent(facesReported = 3))
    }

    /**
     * Reaching the confirm button means looking down and away, so the frames around the tap are the
     * least likely in the whole check to hold a face. The window is what stops one such frame
     * deciding a check the user was plainly present for.
     */
    @Test
    fun `a face stays present for the length of the window`() {
        assertTrue(FaceDetectSupport.stillPresent(lastFaceAtMs = 1_000L, nowMs = 1_000L))
        assertTrue(
            FaceDetectSupport.stillPresent(
                lastFaceAtMs = 1_000L,
                nowMs = 1_000L + FaceDetectSupport.PRESENCE_WINDOW_MS,
            ),
        )
    }

    @Test
    fun `a face older than the window has gone`() {
        assertFalse(
            FaceDetectSupport.stillPresent(
                lastFaceAtMs = 1_000L,
                nowMs = 1_001L + FaceDetectSupport.PRESENCE_WINDOW_MS,
            ),
        )
    }

    /** No frame has held a face yet, which the window must not read as one seen at time zero. */
    @Test
    fun `a face never seen is never present`() {
        assertFalse(FaceDetectSupport.stillPresent(lastFaceAtMs = 0L, nowMs = 0L))
        assertFalse(FaceDetectSupport.stillPresent(lastFaceAtMs = 0L, nowMs = 500L))
    }

    /**
     * Where the mode field and the faces disagree, the faces win: a result carrying rectangles came
     * from a detector, whatever the metadata says, and routing that camera to the fallback would
     * abandon a working check.
     */
    @Test
    fun `a result carrying faces is detecting whatever its mode says`() {
        assertTrue(FaceDetectSupport.resultIsDetecting(appliedMode = FaceDetectSupport.OFF, facesReported = 1))
        assertTrue(FaceDetectSupport.resultIsDetecting(appliedMode = null, facesReported = 1))
    }

    @Test
    fun `an empty result from a mode that detects is still detection`() {
        assertTrue(FaceDetectSupport.resultIsDetecting(appliedMode = SIMPLE, facesReported = 0))
    }

    @Test
    fun `no mode and no faces is no detection`() {
        assertFalse(FaceDetectSupport.resultIsDetecting(appliedMode = FaceDetectSupport.OFF, facesReported = 0))
        assertFalse(FaceDetectSupport.resultIsDetecting(appliedMode = null, facesReported = 0))
    }

    /**
     * What the camera advertises and what it runs with are separate answers, and a HAL is free to
     * disagree with itself. Detection that silently ran OFF reports an empty frame forever, so it
     * has to be caught rather than spent as three failed attempts.
     */
    @Test
    fun `a mode of OFF on the result is not detection`() {
        assertFalse(FaceDetectSupport.isDetecting(FaceDetectSupport.OFF))
    }

    @Test
    fun `a result carrying no mode at all is not detection`() {
        assertFalse(FaceDetectSupport.isDetecting(null))
    }

    @Test
    fun `both detecting modes count as detection`() {
        assertTrue(FaceDetectSupport.isDetecting(SIMPLE))
        assertTrue(FaceDetectSupport.isDetecting(FULL))
    }
}
