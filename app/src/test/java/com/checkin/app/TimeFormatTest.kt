package com.checkin.app

import com.checkin.app.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class TimeFormatTest {

    private val second = 1000L
    private val minute = 60 * second
    private val hour = 60 * minute

    /** The patterns as shipped: read from the string resources, so a broken pattern fails here. */
    private val english = ShippedPatterns.read("values", listOf(Locale.UK))
    private val arabic = ShippedPatterns.read("values-ar", listOf(Locale.forLanguageTag("ar")))

    @Test
    fun `a fresh clock reads zero minutes and zero seconds`() {
        assertEquals("0m 0s", TimeFormat.durationLive(0L, english))
    }

    @Test
    fun `seconds tick before the first minute`() {
        assertEquals("0m 1s", TimeFormat.durationLive(second, english))
        assertEquals("0m 59s", TimeFormat.durationLive(59 * second, english))
    }

    /** The whole point of the format: the last reading before it switches units. */
    @Test
    fun `the last sub-hour reading is 59m 59s`() {
        assertEquals("59m 59s", TimeFormat.durationLive(59 * minute + 59 * second, english))
    }

    @Test
    fun `the hour boundary switches to hours and minutes`() {
        assertEquals("1h 0m", TimeFormat.durationLive(hour, english))
        assertEquals("1h 0m", TimeFormat.durationLive(hour + 59 * second, english))
        assertEquals("1h 1m", TimeFormat.durationLive(hour + minute, english))
    }

    @Test
    fun `past the hour, seconds are dropped rather than rounded into minutes`() {
        assertEquals("8h 30m", TimeFormat.durationLive(8 * hour + 30 * minute + 59 * second, english))
    }

    /** A system clock that has run backwards mid-session could hand this a negative value. */
    @Test
    fun `a negative elapsed reads as zero rather than a minus sign`() {
        assertEquals("0m 0s", TimeFormat.durationLive(-5000L, english))
    }

    @Test
    fun `durationShort still truncates to whole minutes`() {
        assertEquals("0h 0m", TimeFormat.durationShort(59 * second, english))
        assertEquals("8h 30m", TimeFormat.durationShort(8 * hour + 30 * minute + 59 * second, english))
    }

    @Test
    fun `a date key renders with its weekday and no year`() {
        assertEquals("Saturday 25 Jul", TimeFormat.dateKeyWithWeekday("2026-07-25", english))
    }

    /** The screens hold a nullable selection, and a malformed key must lose the heading, not crash. */
    @Test
    fun `an absent or unparseable date key returns null`() {
        assertEquals(null, TimeFormat.dateKeyWithWeekday(null, english))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("", english))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("25-07-2026", english))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("2026-13-01", english))
        assertEquals(null, TimeFormat.dateKeyWithWeekday("2026-02-30", english))
    }

    @Test
    fun `a date carries its year in the language's own order`() {
        assertEquals("25 Jul 2026", TimeFormat.dateWithYear(LocalDate.of(2026, 7, 25), english))
        assertEquals("25 يوليو 2026", TimeFormat.dateWithYear(LocalDate.of(2026, 7, 25), arabic))
    }

    /** One formatter for the whole heading, so the year follows the language like the month. */
    @Test
    fun `a month heading is spelled in the reader's language`() {
        assertEquals("August 2026", TimeFormat.monthYear(YearMonth.of(2026, 8), english))
        assertEquals("أغسطس 2026", TimeFormat.monthYear(YearMonth.of(2026, 8), arabic))
    }

    /**
     * Arabic words its units and orders its date, and numbers in Latin digits all the same: the
     * templates are filled under the root locale, and java.time prints ASCII digits in every locale.
     */
    @Test
    fun `arabic gets its own units and order with latin digits`() {
        assertEquals("8 س 30 د", TimeFormat.durationShort(8 * hour + 30 * minute, arabic))
        assertEquals("0 د 5 ث", TimeFormat.durationLive(5 * second, arabic))
        assertEquals("السبت، 25 يوليو", TimeFormat.dateKeyWithWeekday("2026-07-25", arabic))
        assertEquals("أغسطس 26", TimeFormat.axisMonth(YearMonth.of(2026, 8), multiYear = true, arabic))
    }

    @Test
    fun `an hour of day reads in the language's own clock`() {
        assertEquals("10:00 am", TimeFormat.hourOfDay(10, english))
        assertEquals("10:00 ص", TimeFormat.hourOfDay(10, arabic))
    }
}
