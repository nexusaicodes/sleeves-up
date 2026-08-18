package com.checkin.app

import com.checkin.app.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    private val second = 1000L
    private val minute = 60 * second
    private val hour = 60 * minute

    @Test
    fun `a fresh clock reads zero minutes and zero seconds`() {
        assertEquals("0m 0s", TimeFormat.durationLive(0L))
    }

    @Test
    fun `seconds tick before the first minute`() {
        assertEquals("0m 1s", TimeFormat.durationLive(second))
        assertEquals("0m 59s", TimeFormat.durationLive(59 * second))
    }

    /** The whole point of the format: the last reading before it switches units. */
    @Test
    fun `the last sub-hour reading is 59m 59s`() {
        assertEquals("59m 59s", TimeFormat.durationLive(59 * minute + 59 * second))
    }

    @Test
    fun `the hour boundary switches to hours and minutes`() {
        assertEquals("1h 0m", TimeFormat.durationLive(hour))
        assertEquals("1h 0m", TimeFormat.durationLive(hour + 59 * second))
        assertEquals("1h 1m", TimeFormat.durationLive(hour + minute))
    }

    @Test
    fun `past the hour, seconds are dropped rather than rounded into minutes`() {
        assertEquals("8h 30m", TimeFormat.durationLive(8 * hour + 30 * minute + 59 * second))
    }

    /** A system clock that has run backwards mid-session could hand this a negative value. */
    @Test
    fun `a negative elapsed reads as zero rather than a minus sign`() {
        assertEquals("0m 0s", TimeFormat.durationLive(-5000L))
    }

    @Test
    fun `durationShort still truncates to whole minutes`() {
        assertEquals("0h 0m", TimeFormat.durationShort(59 * second))
        assertEquals("8h 30m", TimeFormat.durationShort(8 * hour + 30 * minute + 59 * second))
    }

    @Test
    fun `a date key renders with its weekday and no year`() {
        assertEquals("Saturday, Jul 25", TimeFormat.dateKeyWithWeekday("2026-07-25"))
    }

    /** The screens hold a nullable selection, and a malformed key must lose the heading, not crash. */
    @Test
    fun `an absent or unparseable date key returns null`() {
        assertEquals(null, TimeFormat.dateKeyWithWeekday(null))
        assertEquals(null, TimeFormat.dateKeyWithWeekday(""))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("25-07-2026"))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("2026-13-01"))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("2026-02-30"))
    }
}
