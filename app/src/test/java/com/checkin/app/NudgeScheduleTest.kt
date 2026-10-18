package com.checkin.app

import com.checkin.app.notify.nudge.NudgeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The alarm and the eligibility rule both read this object, and a disagreement between them is
 * silence with nothing to point at: an alarm firing at an instant the rule then declines to act on
 * looks exactly like an alarm that never fired.
 */
class NudgeScheduleTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val day: LocalDate = LocalDate.of(2026, 6, 15)
    private val hour = 8

    private fun at(zone: ZoneId, date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // --- inBand ---

    /** One band, from the chosen hour to the end of the day. */
    @Test
    fun `the band runs from the chosen hour to midnight`() {
        for (h in hour..23) assertTrue("hour $h", NudgeSchedule.inBand(h, hour))
    }

    /**
     * The hours before the chosen one are what keep a nudge out of the small hours, and they do it
     * without a separate quiet-hours mechanism — the app deliberately has none.
     */
    @Test
    fun `hours before the chosen hour are outside it`() {
        for (h in 0 until hour) assertFalse("hour $h", NudgeSchedule.inBand(h, hour))
    }

    /**
     * The latest choosable hour still leaves three hours before midnight: an inexact alarm may land
     * up to an hour late, and a habit held back behind another's reminder by the ninety-minute gap
     * later still, so a narrower band could close before either delivery.
     */
    @Test
    fun `every choosable band keeps three hours before midnight`() {
        NudgeSchedule.hourRange.forEach { chosen -> assertTrue("chosen hour $chosen", 24 - chosen >= 3) }
    }

    /** A stored value from outside the range is read as the nearest valid hour, never used as-is. */
    @Test
    fun `an out-of-range hour is clamped`() {
        assertEquals(NudgeSchedule.hourRange.first, NudgeSchedule.clampHour(0))
        assertEquals(NudgeSchedule.hourRange.last, NudgeSchedule.clampHour(23))
    }

    @Test
    fun `the default hour is choosable`() {
        assertTrue(NudgeSchedule.DEFAULT_HOUR in NudgeSchedule.hourRange)
    }

    // --- nextBandAfter ---

    @Test
    fun `before the chosen hour the next band is today's`() {
        assertEquals(at(utc, day, hour), NudgeSchedule.nextBandAfter(at(utc, day, hour - 2), utc, hour))
    }

    /**
     * Strictly after, and that is load-bearing: [com.checkin.app.notify.nudge.NudgeAlarmReceiver]
     * re-arms from inside the pass the alarm just triggered, so an inclusive comparison would
     * schedule the instant that had only just fired and spin on it.
     */
    @Test
    fun `an instant exactly on the band's opening yields tomorrow's`() {
        assertEquals(at(utc, day.plusDays(1), hour), NudgeSchedule.nextBandAfter(at(utc, day, hour), utc, hour))
    }

    @Test
    fun `inside or after the band it rolls to tomorrow's`() {
        assertEquals(at(utc, day.plusDays(1), hour), NudgeSchedule.nextBandAfter(at(utc, day, 23, 59), utc, hour))
    }

    /** Changing the hour moves the next alarm with it — the reason every chooser re-arms at once. */
    @Test
    fun `a different chosen hour arms a different instant`() {
        assertEquals(at(utc, day, 15), NudgeSchedule.nextBandAfter(at(utc, day, 6), utc, 15))
    }

    /** The alarm is armed in local wall time, so the zone has to reach the calculation. */
    @Test
    fun `the zone decides which instant the band opens at`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertEquals(at(kolkata, day, hour), NudgeSchedule.nextBandAfter(at(kolkata, day, hour - 1), kolkata, hour))
    }

    /**
     * Through the calendar, not by adding hours to an epoch: on a spring-forward day the wall clock
     * loses an hour, so arithmetic on millis would drift the band's opening after the transition.
     */
    @Test
    fun `a DST day still opens the band at its stated local hour`() {
        val newYork = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8)

        assertEquals(
            at(newYork, springForward, hour),
            NudgeSchedule.nextBandAfter(at(newYork, springForward, 1), newYork, hour),
        )
        assertEquals(
            at(newYork, springForward, hour) until at(newYork, springForward.plusDays(1), 0),
            NudgeSchedule.bandOn(springForward, newYork, hour),
        )
    }

    // --- bandOn ---

    /** The instant band is the hour band, so the alarm and the rule mean the same span. */
    @Test
    fun `the band's instants run from the chosen hour to midnight`() {
        assertEquals(at(utc, day, hour) until at(utc, day.plusDays(1), 0), NudgeSchedule.bandOn(day, utc, hour))
    }

    /** Re-arming repeatedly must converge rather than walk the alarm forward a day at a time. */
    @Test
    fun `re-arming from the result is stable within the day`() {
        val next = NudgeSchedule.nextBandAfter(at(utc, day, 6), utc, hour)

        assertEquals(next, NudgeSchedule.nextBandAfter(next - 1, utc, hour))
        assertTrue(NudgeSchedule.nextBandAfter(next, utc, hour) > next)
    }
}
