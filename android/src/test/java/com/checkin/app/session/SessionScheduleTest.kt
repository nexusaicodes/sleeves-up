package com.checkin.app.session

import com.checkin.app.session.SessionSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The day boundary is the only thing that ends a forgotten session, so the instant it computes is
 * what decides whether a session recorded a plausible day or a sixteen-hour one.
 */
class SessionScheduleTest {

    private val utc = ZoneId.of("UTC")

    /** A zone at a non-whole-hour offset, where a naive "round up to the next 24h" would be wrong. */
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // --- Reminder cadence ---

    @Test
    fun `the next reminder is one interval out`() {
        assertEquals(1_000L + SessionSchedule.REMINDER_INTERVAL_MS, SessionSchedule.nextReminderAt(1_000L))
    }

    // --- The day boundary from an instant ---

    @Test
    fun `the boundary is the midnight that ends the day the instant falls in`() {
        val boundary = SessionSchedule.nextDayBoundaryAfter(at(utc, 2026, 6, 15, 9), utc)

        assertEquals(at(utc, 2026, 6, 16, 0), boundary)
    }

    /**
     * Strictly after, never equal. A session that begins exactly at midnight must get a full day,
     * not be closed the instant it opens.
     */
    @Test
    fun `an instant already at midnight gets the following midnight`() {
        val midnight = at(utc, 2026, 6, 15, 0)

        assertEquals(at(utc, 2026, 6, 16, 0), SessionSchedule.nextDayBoundaryAfter(midnight, utc))
    }

    @Test
    fun `the boundary is local, not UTC`() {
        // 20:00 in Kolkata on the 15th is still 14:30 UTC on the 15th, but the two days end at
        // different instants — deriving this in UTC would close the session 5h30m late.
        val evening = at(kolkata, 2026, 6, 15, 20)

        assertEquals(at(kolkata, 2026, 6, 16, 0), SessionSchedule.nextDayBoundaryAfter(evening, kolkata))
    }

    // --- The day boundary from a date_key ---

    @Test
    fun `a date key resolves to the midnight that ends that day`() {
        assertEquals(at(utc, 2026, 6, 16, 0), SessionSchedule.dayBoundaryOf("2026-06-15", utc))
    }

    /**
     * The whole point of deriving from the key rather than the fire time: an alarm that lands a day
     * late still closes the session where the day actually ended.
     */
    @Test
    fun `a date key in the past yields an instant in the past`() {
        val boundary = SessionSchedule.dayBoundaryOf("2026-06-15", utc)!!
        val muchLater = at(utc, 2026, 6, 17, 11)

        assertTrue("a late alarm must still close at the original boundary", boundary < muchLater)
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
        val newYork = ZoneId.of("America/New_York")
        // 2026-03-08 is the US spring-forward date.
        val boundary = SessionSchedule.dayBoundaryOf("2026-03-08", newYork)!!

        val asLocal = Instant.ofEpochMilli(boundary).atZone(newYork)
        assertEquals(LocalDate.of(2026, 3, 9), asLocal.toLocalDate())
        assertEquals(0, asLocal.hour)
    }
}
