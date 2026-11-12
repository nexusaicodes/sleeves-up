package com.checkin.app.time

import com.checkin.app.time.DayClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DayClockTest {

    @Test
    fun `just before midnight returns a small positive delay`() {
        val now = ZonedDateTime.of(2026, 6, 15, 23, 59, 59, 0, ZoneId.of("UTC"))
        assertEquals(1000L, DayClock.millisUntilNextMidnight(now))
    }

    @Test
    fun `within the cap window returns the exact remaining time`() {
        val now = ZonedDateTime.of(2026, 6, 15, 23, 30, 0, 0, ZoneId.of("UTC"))
        assertEquals(30 * 60 * 1000L, DayClock.millisUntilNextMidnight(now))
    }

    @Test
    fun `far from midnight is capped so a timezone change self-corrects within the hour`() {
        val now = ZonedDateTime.of(2026, 6, 15, 1, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(DayClock.MAX_TICK_MS, DayClock.millisUntilNextMidnight(now))
    }

    @Test
    fun `a DST-transition day still yields a positive delay to its own midnight`() {
        // US spring-forward day; the 02:00->03:00 jump is earlier and doesn't touch 23:45 -> next midnight.
        val now = ZonedDateTime.of(2026, 3, 8, 23, 45, 0, 0, ZoneId.of("America/New_York"))
        val delay = DayClock.millisUntilNextMidnight(now)
        assertTrue(delay in 1L..DayClock.MAX_TICK_MS)
        assertEquals(15 * 60 * 1000L, delay)
    }
}
