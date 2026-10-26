package com.checkin.app

import com.checkin.app.platform.UpdateOffer
import com.checkin.app.platform.UpdatePrompt
import com.checkin.app.platform.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePromptTest {

    private fun status(available: Int? = null, flexible: Boolean = true, downloaded: Boolean = false) =
        UpdateStatus(availableVersionCode = available, flexibleAllowed = flexible, downloaded = downloaded)

    @Test
    fun `no answer from play offers nothing`() {
        assertEquals(UpdateOffer.None, UpdatePrompt.decide(null, lastOfferedVersionCode = 0))
    }

    @Test
    fun `no update available offers nothing`() {
        assertEquals(UpdateOffer.None, UpdatePrompt.decide(status(), lastOfferedVersionCode = 0))
    }

    @Test
    fun `a new version code is offered`() {
        assertEquals(UpdateOffer.Prompt(20261101), UpdatePrompt.decide(status(20261101), lastOfferedVersionCode = 0))
    }

    @Test
    fun `a version code already offered is not offered again`() {
        assertEquals(UpdateOffer.None, UpdatePrompt.decide(status(20261101), lastOfferedVersionCode = 20261101))
    }

    @Test
    fun `a later version code than the one offered is offered`() {
        assertEquals(
            UpdateOffer.Prompt(2026110201),
            UpdatePrompt.decide(status(2026110201), lastOfferedVersionCode = 20261101),
        )
    }

    @Test
    fun `an update play will not run flexibly is not offered`() {
        assertEquals(
            UpdateOffer.None,
            UpdatePrompt.decide(status(20261101, flexible = false), lastOfferedVersionCode = 0),
        )
    }

    @Test
    fun `a finished download is surfaced even after its version was offered`() {
        assertEquals(
            UpdateOffer.RestartReady,
            UpdatePrompt.decide(status(20261101, downloaded = true), lastOfferedVersionCode = 20261101),
        )
    }

    @Test
    fun `a finished download is surfaced when play reports no update available`() {
        assertEquals(
            UpdateOffer.RestartReady,
            UpdatePrompt.decide(status(downloaded = true), lastOfferedVersionCode = 0),
        )
    }
}
