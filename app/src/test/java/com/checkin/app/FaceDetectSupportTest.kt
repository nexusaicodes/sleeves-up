package com.checkin.app

import com.checkin.app.ui.presence.FaceDetectSupport
import com.checkin.app.ui.presence.FaceDetectSupport.FULL
import com.checkin.app.ui.presence.FaceDetectSupport.SIMPLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(0, FaceDetectSupport.facesPresent(intArrayOf()))
    }

    /**
     * The score floor is what separates a face from the wall behind a desk. Counting every reported
     * rectangle regardless of confidence would pass a check nobody was present for.
     */
    @Test
    fun `faces below the score floor do not count`() {
        assertEquals(0, FaceDetectSupport.facesPresent(intArrayOf(1, FaceDetectSupport.MIN_SCORE - 1)))
    }

    @Test
    fun `a face at the floor counts`() {
        assertEquals(1, FaceDetectSupport.facesPresent(intArrayOf(FaceDetectSupport.MIN_SCORE)))
    }

    @Test
    fun `only the confident faces are counted out of a mixed frame`() {
        val scores = intArrayOf(100, 10, 80, FaceDetectSupport.MIN_SCORE - 1)

        assertEquals(2, FaceDetectSupport.facesPresent(scores))
    }
}
