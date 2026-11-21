package com.checkin.app.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayClockTest {

    @Test
    fun `just before midnight returns a small positive delay`() {
        // 2026-06-15T23:59:59Z, pinned with java.time.
        assertEquals(1000L, DayClock.millisUntilNextMidnight(1_781_567_999_000L, TimeZone.UTC))
    }

    @Test
    fun `within the cap window returns the exact remaining time`() {
        // 2026-06-15T23:30Z, pinned with java.time.
        assertEquals(30 * 60 * 1000L, DayClock.millisUntilNextMidnight(1_781_566_200_000L, TimeZone.UTC))
    }

    @Test
    fun `far from midnight is capped so a timezone change self-corrects within the hour`() {
        // 2026-06-15T01:00Z, pinned with java.time.
        assertEquals(DayClock.MAX_TICK_MS, DayClock.millisUntilNextMidnight(1_781_485_200_000L, TimeZone.UTC))
    }

    @Test
    fun `a DST-transition day still yields a positive delay to its own midnight`() {
        // US spring-forward day; the 02:00->03:00 jump is earlier and doesn't touch 23:45 -> next midnight.
        // 2026-03-08T23:45 in New York, pinned with java.time.
        val delay = DayClock.millisUntilNextMidnight(1_773_027_900_000L, TimeZone.of("America/New_York"))
        assertTrue(delay in 1L..DayClock.MAX_TICK_MS)
        assertEquals(15 * 60 * 1000L, delay)
    }
}
