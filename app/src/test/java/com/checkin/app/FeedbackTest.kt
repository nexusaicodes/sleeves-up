package com.checkin.app

import com.checkin.app.ui.about.AppBuild
import com.checkin.app.ui.about.DeviceBuild
import com.checkin.app.ui.about.Feedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {

    private fun draft(manufacturer: String = "Google", model: String = "Pixel 8") = Feedback.draft(
        app = AppBuild(versionName = "1.2", versionCode = 20260726),
        device = DeviceBuild(
            manufacturer = manufacturer,
            model = model,
            androidRelease = "16",
            sdkInt = 36,
        ),
    )

    @Test
    fun `subject names the app and the version`() {
        assertEquals("CheckIn feedback (1.2)", draft().subject)
    }

    @Test
    fun `body carries the diagnostics needed to reproduce a report`() {
        val body = draft().body
        assertTrue(body, body.contains("App: CheckIn 1.2 (20260726)"))
        assertTrue(body, body.contains("Device: Google Pixel 8"))
        assertTrue(body, body.contains("Android: 16 (API 36)"))
    }

    @Test
    fun `body opens with blank lines so the user writes above the footer`() {
        assertTrue(draft().body.startsWith("\n\n"))
    }

    @Test
    fun `footer tells the user the diagnostics are optional`() {
        assertTrue(draft().body.contains("Delete them if you'd rather not share"))
    }

    @Test
    fun `manufacturer is not repeated when the model already leads with it`() {
        assertTrue(
            draft(manufacturer = "motorola", model = "motorola edge 50").body
                .contains("Device: motorola edge 50"),
        )
        assertTrue(
            draft(manufacturer = "Samsung", model = "SM-S911B").body
                .contains("Device: Samsung SM-S911B"),
        )
    }

    @Test
    fun `a blank manufacturer or model never leaves a dangling space`() {
        assertTrue(draft(manufacturer = "", model = "Pixel 8").body.contains("Device: Pixel 8\n"))
        assertTrue(draft(manufacturer = "Google", model = "").body.contains("Device: Google\n"))
    }

    @Test
    fun `address matches the contact on the play listing`() {
        assertEquals("saksham@nexusai.world", Feedback.ADDRESS)
    }
}
