package com.checkin.app.ui.presence

import com.checkin.app.ui.presence.DEVICE_UNLOCK_OFFERED_AFTER_MS
import com.checkin.app.ui.presence.PresenceCheckRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceCheckRulesTest {

    @Test
    fun `a face never passes the check while the device-unlock prompt is up`() {
        assertTrue(PresenceCheckRules.passes(facePresent = true, usingDeviceUnlock = false))
        assertFalse(PresenceCheckRules.passes(facePresent = true, usingDeviceUnlock = true))
        assertFalse(PresenceCheckRules.passes(facePresent = false, usingDeviceUnlock = false))
    }

    @Test
    fun `the wait resumes from the gate opening, clamped at both ends`() {
        assertEquals(DEVICE_UNLOCK_OFFERED_AFTER_MS, PresenceCheckRules.remainingWaitMs(1_000L, 1_000L))
        assertEquals(DEVICE_UNLOCK_OFFERED_AFTER_MS - 4_000L, PresenceCheckRules.remainingWaitMs(1_000L, 5_000L))
        assertEquals(0L, PresenceCheckRules.remainingWaitMs(1_000L, 1_000L + DEVICE_UNLOCK_OFFERED_AFTER_MS * 3))
        // A reboot resets the elapsed-time clock below the saved instant.
        assertEquals(DEVICE_UNLOCK_OFFERED_AFTER_MS, PresenceCheckRules.remainingWaitMs(50_000L, 2_000L))
    }

    @Test
    fun `the fallback is withdrawn once passed or while a prompt is up`() {
        assertTrue(PresenceCheckRules.offersFallback(passed = false, usingDeviceUnlock = false, searchTimedOut = true))
        assertFalse(PresenceCheckRules.offersFallback(passed = true, usingDeviceUnlock = false, searchTimedOut = true))
        assertFalse(PresenceCheckRules.offersFallback(passed = false, usingDeviceUnlock = true, searchTimedOut = true))
        assertFalse(
            PresenceCheckRules.offersFallback(passed = false, usingDeviceUnlock = false, searchTimedOut = false),
        )
    }
}
