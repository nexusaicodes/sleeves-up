package com.checkin.app.ui.presence

import com.checkin.app.ui.presence.CameraReadings
import com.checkin.app.ui.presence.FaceDetectSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraReadingsTest {

    private fun CameraReadings.nonDetecting(times: Int) = repeat(times) { onResult(FaceDetectSupport.OFF, 0) }

    @Test
    fun `nothing is known until the camera is asked`() {
        val readings = CameraReadings()

        assertNull(readings.cameraUsable.value)
        assertFalse(readings.facePresent.value)
    }

    @Test
    fun `the first detecting result with a face reports someone present`() {
        val readings = CameraReadings()
        readings.onBound()

        readings.onResult(FaceDetectSupport.SIMPLE, 1)

        assertTrue(readings.facePresent.value)
        assertEquals(true, readings.cameraUsable.value)
    }

    /** The results around session configuration can carry OFF on a camera that then works. */
    @Test
    fun `a camera is written off only after a run of non-detecting results`() {
        val readings = CameraReadings()
        readings.onBound()

        readings.nonDetecting(FaceDetectSupport.NON_DETECTING_RESULTS - 1)
        assertEquals(true, readings.cameraUsable.value)

        readings.nonDetecting(1)
        assertEquals(false, readings.cameraUsable.value)
    }

    @Test
    fun `a detecting result resets the run of non-detecting ones`() {
        val readings = CameraReadings()
        readings.onBound()

        readings.nonDetecting(FaceDetectSupport.NON_DETECTING_RESULTS - 1)
        readings.onResult(FaceDetectSupport.SIMPLE, 0)
        readings.nonDetecting(FaceDetectSupport.NON_DETECTING_RESULTS - 1)

        assertEquals(true, readings.cameraUsable.value)
    }

    @Test
    fun `a written-off camera that starts detecting is taken back`() {
        val readings = CameraReadings()
        readings.nonDetecting(FaceDetectSupport.NON_DETECTING_RESULTS)

        readings.onResult(FaceDetectSupport.SIMPLE, 0)

        assertEquals(true, readings.cameraUsable.value)
    }

    /** A stale `true` would otherwise survive the empty frames the prompt holds back. */
    @Test
    fun `a non-detecting result clears a face seen before it`() {
        val readings = CameraReadings()
        readings.onResult(FaceDetectSupport.SIMPLE, 1)

        readings.onResult(FaceDetectSupport.OFF, 0)

        assertFalse(readings.facePresent.value)
    }

    /** No score floor and no mode trust: rectangles mean someone is there. */
    @Test
    fun `faces reported under a mode claiming OFF still count`() {
        val readings = CameraReadings()

        readings.onResult(FaceDetectSupport.OFF, 2)

        assertTrue(readings.facePresent.value)
    }

    @Test
    fun `a stop clears the face and the non-detecting run`() {
        val readings = CameraReadings()
        readings.onBound()
        readings.onResult(FaceDetectSupport.SIMPLE, 1)
        readings.nonDetecting(FaceDetectSupport.NON_DETECTING_RESULTS - 1)

        readings.onStop()
        readings.nonDetecting(FaceDetectSupport.NON_DETECTING_RESULTS - 1)

        assertFalse(readings.facePresent.value)
        assertEquals(true, readings.cameraUsable.value)
    }

    @Test
    fun `an unusable camera is reported as such`() {
        val readings = CameraReadings()

        readings.onUnusable()

        assertEquals(false, readings.cameraUsable.value)
    }
}
