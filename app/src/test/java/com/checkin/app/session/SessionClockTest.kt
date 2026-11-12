package com.checkin.app.session

import com.checkin.app.session.SessionClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The elapsed time on the ongoing notification is the number a user reads to decide whether they are
 * still checked in. The maths is kept out of the `Service` so the degenerate instants below are
 * verifiable here rather than by installing the app and waiting.
 */
class SessionClockTest {

    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    // --- The chronometer origin ---

    /** The platform counts up from the check-in instant itself; nothing is ever subtracted. */
    @Test
    fun `a session counts from its check-in`() {
        assertEquals(start, SessionClock.chronometerBase(start))
    }

    /**
     * A service that has posted before adopting a row. Counting up from the epoch for the instant
     * before the reconcile corrects it would flash a decades-long timer on the shade.
     */
    @Test
    fun `a session with no start yet has no origin`() {
        assertNull(SessionClock.chronometerBase(0L))
        assertNull(SessionClock.chronometerBase(-1L))
    }

    // --- Elapsed time ---

    @Test
    fun `elapsed is wall-clock since check-in`() {
        assertEquals(90 * minute, SessionClock.elapsedMs(start + 90 * minute, start))
    }

    /**
     * A backwards clock is a changed system time or a corrupt row. Showing nothing beats showing a
     * negative duration, and `TimeFormat` is not asked to render one.
     */
    @Test
    fun `time running backwards floors at zero`() {
        assertEquals(0L, SessionClock.elapsedMs(start - minute, start))
    }

    @Test
    fun `a session with no start yet reads zero rather than epoch`() {
        assertEquals(0L, SessionClock.elapsedMs(start, 0L))
    }
}
