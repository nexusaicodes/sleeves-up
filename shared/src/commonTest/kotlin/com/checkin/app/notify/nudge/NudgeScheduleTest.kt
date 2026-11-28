package com.checkin.app.notify.nudge

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The alarm and the eligibility rule both read this object, and a disagreement between them is
 * silence with nothing to point at: an alarm firing at an instant the rule then declines to act on
 * looks exactly like an alarm that never fired.
 */
class NudgeScheduleTest {

    private val utc = TimeZone.UTC
    private val day = LocalDate(2026, 6, 15)
    private val nextDay = day.plus(1, DateTimeUnit.DAY)
    private val hour = 8

    /** [hour] o'clock as the minute of the day the schedule takes. */
    private val minute = hour * 60

    /** A UTC wall time as epoch millis; UTC has no transitions, so this cannot disagree with `java.time`. */
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(utc).toEpochMilliseconds()

    // Instants in zones with an offset or a transition, computed with `java.time` so the schedule is
    // checked against an independent reading of the zone rules rather than against itself.
    private val kolkataJun15At0700 = 1_781_487_000_000L
    private val kolkataJun15At0800 = 1_781_490_600_000L
    private val newYorkMar8At0100 = 1_772_949_600_000L
    private val newYorkMar8At0800 = 1_772_971_200_000L
    private val newYorkMar9Midnight = 1_773_028_800_000L

    // --- inBand ---

    /** One band, from the chosen hour to the end of the day. */
    @Test
    fun `the band runs from the chosen hour to midnight`() {
        for (h in hour..23) assertTrue(NudgeSchedule.inBand(h * 60, minute), "hour $h")
    }

    /**
     * The hours before the chosen one are what keep a nudge out of the small hours, and they do it
     * without a separate quiet-hours mechanism — the app deliberately has none.
     */
    @Test
    fun `hours before the chosen hour are outside it`() {
        for (h in 0 until hour) assertFalse(NudgeSchedule.inBand(h * 60, minute), "hour $h")
    }

    /**
     * The latest choosable time still leaves three hours before midnight: an inexact alarm may land
     * up to an hour late, and a habit held back behind another's reminder by the ninety-minute gap
     * later still, so a narrower band could close before either delivery.
     */
    @Test
    fun `every choosable band keeps three hours before midnight`() {
        NudgeSchedule.minuteRange.forEach { chosen -> assertTrue(24 * 60 - chosen >= 3 * 60, "chosen minute $chosen") }
    }

    /** A stored value from outside the range is read as the nearest valid time, never used as-is. */
    @Test
    fun `an out-of-range time is clamped`() {
        assertEquals(NudgeSchedule.minuteRange.first, NudgeSchedule.clampMinute(0))
        assertEquals(NudgeSchedule.minuteRange.last, NudgeSchedule.clampMinute(23 * 60 + 59))
    }

    @Test
    fun `the range runs from 05 00 to 21 00 inclusive`() {
        assertEquals(5 * 60, NudgeSchedule.minuteRange.first)
        assertEquals(21 * 60, NudgeSchedule.minuteRange.last)
    }

    @Test
    fun `the default time is choosable`() {
        assertTrue(NudgeSchedule.DEFAULT_MINUTE in NudgeSchedule.minuteRange)
    }

    /** Reminders are chosen to the minute, and the band opens on that minute rather than its hour. */
    @Test
    fun `a time off the hour opens the band on its minute`() {
        val quarterPast = minute + 15

        assertFalse(NudgeSchedule.inBand(quarterPast - 1, quarterPast))
        assertTrue(NudgeSchedule.inBand(quarterPast, quarterPast))
        assertEquals(
            at(day, hour, 15) until at(nextDay, 0),
            NudgeSchedule.bandOn(day, utc, quarterPast),
        )
        assertEquals(at(day, hour, 15), NudgeSchedule.nextBandAfter(at(day, hour), utc, quarterPast))
    }

    // --- nextBandAfter ---

    @Test
    fun `before the chosen hour the next band is today's`() {
        assertEquals(at(day, hour), NudgeSchedule.nextBandAfter(at(day, hour - 2), utc, minute))
    }

    /**
     * Strictly after, and that is load-bearing: the fallback can be armed from inside the pass the
     * alarm just triggered (see the Android app's `armFallback`), so an inclusive
     * comparison would schedule the instant that had only just fired and spin on it.
     */
    @Test
    fun `an instant exactly on the band's opening yields tomorrow's`() {
        assertEquals(at(nextDay, hour), NudgeSchedule.nextBandAfter(at(day, hour), utc, minute))
    }

    @Test
    fun `inside or after the band it rolls to tomorrow's`() {
        assertEquals(at(nextDay, hour), NudgeSchedule.nextBandAfter(at(day, 23, 59), utc, minute))
    }

    /** Changing the time moves the next alarm with it — the reason every chooser re-arms at once. */
    @Test
    fun `a different chosen hour arms a different instant`() {
        assertEquals(at(day, 15), NudgeSchedule.nextBandAfter(at(day, 6), utc, 15 * 60))
    }

    /** The alarm is armed in local wall time, so the zone has to reach the calculation. */
    @Test
    fun `the zone decides which instant the band opens at`() {
        val kolkata = TimeZone.of("Asia/Kolkata")
        assertEquals(kolkataJun15At0800, NudgeSchedule.nextBandAfter(kolkataJun15At0700, kolkata, minute))
    }

    /**
     * Through the calendar, not by adding hours to an epoch: on a spring-forward day the wall clock
     * loses an hour, so arithmetic on millis would drift the band's opening after the transition.
     */
    @Test
    fun `a DST day still opens the band at its stated local hour`() {
        val newYork = TimeZone.of("America/New_York")
        val springForward = LocalDate(2026, 3, 8)

        assertEquals(newYorkMar8At0800, NudgeSchedule.nextBandAfter(newYorkMar8At0100, newYork, minute))
        assertEquals(newYorkMar8At0800 until newYorkMar9Midnight, NudgeSchedule.bandOn(springForward, newYork, minute))
    }

    // --- bandOn ---

    /** The same band [NudgeSchedule.inBand] reads by minute, so the alarm and the rule mean the same span. */
    @Test
    fun `the band's instants run from the chosen hour to midnight`() {
        assertEquals(at(day, hour) until at(nextDay, 0), NudgeSchedule.bandOn(day, utc, minute))
    }

    /** Re-arming repeatedly must converge rather than walk the alarm forward a day at a time. */
    @Test
    fun `re-arming from the result is stable within the day`() {
        val next = NudgeSchedule.nextBandAfter(at(day, 6), utc, minute)

        assertEquals(next, NudgeSchedule.nextBandAfter(next - 1, utc, minute))
        assertTrue(NudgeSchedule.nextBandAfter(next, utc, minute) > next)
    }
}
