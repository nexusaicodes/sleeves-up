package com.checkin.app

import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signal is a process-global object with no owner to reset it, so every test clears up after
 * itself — and its expiry is the only thing standing between an abandoned gate and a check-in
 * written hours later onto a row nothing in the app can edit.
 */
class PresenceCheckSignalTest {

    private val raisedAt = 1_000_000L

    @After
    fun tearDown() = PresenceCheckSignal.clear()

    @Test
    fun `a fresh request survives`() {
        PresenceCheckSignal.raise(Reason.CHECK_IN, raisedAt)

        assertTrue(PresenceCheckSignal.expireIfStale(raisedAt))
        assertEquals(Reason.CHECK_IN, PresenceCheckSignal.request.value)
    }

    @Test
    fun `a request survives the round trip out to system settings and back`() {
        // The camera-recovery screen's only escape hatch leaves the app; the user is expected back.
        PresenceCheckSignal.raise(Reason.CHECK_OUT_FROM_TIMER, raisedAt)

        val thirtySecondsLater = raisedAt + 30_000L
        assertTrue(PresenceCheckSignal.expireIfStale(thirtySecondsLater))
        assertEquals(Reason.CHECK_OUT_FROM_TIMER, PresenceCheckSignal.request.value)
    }

    @Test
    fun `a request the user walked away from is dropped`() {
        // The nudge tapped in the morning, abandoned on the disclosure, reopened that evening.
        PresenceCheckSignal.raise(Reason.CHECK_IN, raisedAt)

        val twelveHoursLater = raisedAt + 12 * 60 * 60 * 1000L
        assertFalse(PresenceCheckSignal.expireIfStale(twelveHoursLater))
        assertEquals(Reason.NONE, PresenceCheckSignal.request.value)
    }

    @Test
    fun `the boundary belongs to the live side`() {
        PresenceCheckSignal.raise(Reason.CHECK_OUT_FROM_TIMER, raisedAt)

        assertTrue(PresenceCheckSignal.expireIfStale(raisedAt + PresenceCheckSignal.EXPIRY_MS))
        assertFalse(PresenceCheckSignal.expireIfStale(raisedAt + PresenceCheckSignal.EXPIRY_MS + 1))
    }

    @Test
    fun `an absent request stays absent however much time passes`() {
        // Nothing was asked for, so nothing may be reported as live — expiry must not invent one.
        assertFalse(PresenceCheckSignal.expireIfStale(raisedAt))
        assertEquals(Reason.NONE, PresenceCheckSignal.request.value)
    }

    @Test
    fun `clearing resets the stamp so the next request is judged on its own age`() {
        PresenceCheckSignal.raise(Reason.CHECK_IN, raisedAt)
        PresenceCheckSignal.clear()

        // Raised much later: it is fresh at that moment, and must not inherit the old timestamp.
        val muchLater = raisedAt + 12 * 60 * 60 * 1000L
        PresenceCheckSignal.raise(Reason.CHECK_IN, muchLater)
        assertTrue(PresenceCheckSignal.expireIfStale(muchLater))
        assertEquals(Reason.CHECK_IN, PresenceCheckSignal.request.value)
    }
}
