package com.checkin.app.session

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The day boundary is the only thing that ends a forgotten session, so the instant it computes is
 * what decides whether a session recorded a plausible day or a sixteen-hour one.
 */

class SessionScheduleTest {

    private val utc = TimeZone.UTC

    /** A zone at a non-whole-hour offset, where a naive "round up to the next 24h" would be wrong. */
    private val kolkata = TimeZone.of("Asia/Kolkata")

    // Expected instants are pinned epoch millis, computed with java.time rather than the library
    // under test, so the check stays independent on every platform.
    private val utcJun15At0900 = 1_781_514_000_000L
    private val utcJun15Midnight = 1_781_481_600_000L
    private val utcJun16Midnight = 1_781_568_000_000L
    private val utcJun17At1100 = 1_781_694_000_000L
    private val kolkataJun15At2000 = 1_781_533_800_000L
    private val kolkataJun16Midnight = 1_781_548_200_000L
    private val newYorkMar9Midnight = 1_773_028_800_000L
    private val santiagoSep6At0100 = 1_788_667_200_000L

    // --- Reminder cadence ---

    @Test
    fun `the next reminder is one interval out`() {
        assertEquals(1_000L + SessionSchedule.REMINDER_INTERVAL_MS, SessionSchedule.nextReminderAt(1_000L))
    }

    // --- The day boundary from an instant ---

    @Test
    fun `the boundary is the midnight that ends the day the instant falls in`() {
        val boundary = SessionSchedule.nextDayBoundaryAfter(utcJun15At0900, utc)

        assertEquals(utcJun16Midnight, boundary)
    }

    /**
     * Strictly after, never equal. A session that begins exactly at midnight must get a full day,
     * not be closed the instant it opens.
     */
    @Test
    fun `an instant already at midnight gets the following midnight`() {
        val midnight = utcJun15Midnight

        assertEquals(utcJun16Midnight, SessionSchedule.nextDayBoundaryAfter(midnight, utc))
    }

    @Test
    fun `the boundary is local rather than UTC`() {
        // 20:00 in Kolkata on the 15th is still 14:30 UTC on the 15th, but the two days end at
        // different instants — deriving this in UTC would close the session 5h30m late.
        val evening = kolkataJun15At2000

        assertEquals(kolkataJun16Midnight, SessionSchedule.nextDayBoundaryAfter(evening, kolkata))
    }

    // --- The day boundary from a date_key ---

    @Test
    fun `a date key resolves to the midnight that ends that day`() {
        assertEquals(utcJun16Midnight, SessionSchedule.dayBoundaryOf("2026-06-15", utc))
    }

    /**
     * The whole point of deriving from the key rather than the fire time: an alarm that lands a day
     * late still closes the session where the day actually ended.
     */
    @Test
    fun `a date key in the past yields an instant in the past`() {
        val boundary = SessionSchedule.dayBoundaryOf("2026-06-15", utc)!!
        val muchLater = utcJun17At1100

        assertTrue(boundary < muchLater, "a late alarm must still close at the original boundary")
    }

    /** Nullable rather than throwing, matching how the rest of the app treats a stored date_key. */
    @Test
    fun `a malformed date key returns null rather than throwing`() {
        assertNull(SessionSchedule.dayBoundaryOf("not-a-date", utc))
        assertNull(SessionSchedule.dayBoundaryOf("", utc))
        assertNull(SessionSchedule.dayBoundaryOf("2026-13-45", utc))
    }

    /**
     * On a spring-forward day the local day is 23 hours long. Adding a fixed 24h to the day's start
     * would land an hour into the following day; going through the calendar does not.
     */
    @Test
    fun `a DST transition does not push the boundary past midnight`() {
        val newYork = TimeZone.of("America/New_York")
        // 2026-03-08 is the US spring-forward date; the pin is 2026-03-09T00:00 in New York.
        assertEquals(newYorkMar9Midnight, SessionSchedule.dayBoundaryOf("2026-03-08", newYork))
    }

    /**
     * Santiago springs forward at midnight, so 2026-09-06T00:00 never happens there. The day that
     * follows starts at the first instant that does exist, 01:00, as java.time resolves it.
     */
    @Test
    fun `a midnight skipped by DST resolves to the first instant after it`() {
        assertEquals(santiagoSep6At0100, SessionSchedule.dayBoundaryOf("2026-09-05", TimeZone.of("America/Santiago")))
    }
}
