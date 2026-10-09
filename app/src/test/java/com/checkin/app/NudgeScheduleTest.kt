package com.checkin.app

import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.NudgeSchedule.Checkpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val first = 8
    private val followUp = first + NudgeSchedule.FOLLOW_UP_OFFSET_HOURS

    private fun at(zone: ZoneId, date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // --- checkpointAt ---

    @Test
    fun `the chosen hour and its follow-up map to their checkpoints`() {
        assertEquals(Checkpoint.FIRST, NudgeSchedule.checkpointAt(first, first))
        assertEquals(Checkpoint.FOLLOW_UP, NudgeSchedule.checkpointAt(followUp, first))
    }

    /**
     * Bands, not thresholds. If FIRST matched every hour from the chosen one onward, the follow-up
     * would become unreachable — silently, since nothing else would change.
     */
    @Test
    fun `an hour inside a band maps to that band's checkpoint`() {
        assertEquals(Checkpoint.FIRST, NudgeSchedule.checkpointAt(followUp - 1, first))
        assertEquals(Checkpoint.FOLLOW_UP, NudgeSchedule.checkpointAt(23, first))
    }

    /**
     * The hours before the chosen one are what keep a nudge out of the small hours, and they do it
     * without a separate quiet-hours mechanism — the app deliberately has none.
     */
    @Test
    fun `hours before the chosen hour map to nothing`() {
        for (hour in 0 until first) {
            assertNull("hour $hour should be outside every band", NudgeSchedule.checkpointAt(hour, first))
        }
    }

    /** At every hour a user may choose, both bands are reachable, so no nudge is orphaned. */
    @Test
    fun `every checkpoint is reachable at every choosable hour`() {
        NudgeSchedule.firstHourRange.forEach { chosen ->
            val reached = (0..23).mapNotNull { NudgeSchedule.checkpointAt(it, chosen) }.toSet()
            assertEquals("chosen hour $chosen", Checkpoint.entries.toSet(), reached)
        }
    }

    /**
     * The latest choosable hour still leaves the follow-up band two hours before midnight: an
     * inexact alarm may land up to an hour late, and a gap-deferred send later still, so a band only
     * an hour wide would leave either one nowhere to send.
     */
    @Test
    fun `the follow-up band keeps two hours before midnight`() {
        NudgeSchedule.firstHourRange.forEach { chosen ->
            assertTrue("chosen hour $chosen", 24 - NudgeSchedule.followUpHour(chosen) >= 2)
        }
    }

    /** A stored value from outside the range is read as the nearest valid hour, never used as-is. */
    @Test
    fun `an out-of-range hour is clamped`() {
        assertEquals(NudgeSchedule.firstHourRange.first, NudgeSchedule.clampFirstHour(0))
        assertEquals(NudgeSchedule.firstHourRange.last, NudgeSchedule.clampFirstHour(23))
    }

    @Test
    fun `the default hour is choosable`() {
        assertTrue(NudgeSchedule.DEFAULT_FIRST_HOUR in NudgeSchedule.firstHourRange)
    }

    // --- nextCheckpointAfter ---

    @Test
    fun `before the chosen hour the next one is today's first`() {
        val from = at(utc, day, first - 2)
        assertEquals(at(utc, day, first), NudgeSchedule.nextCheckpointAfter(from, utc, first))
    }

    @Test
    fun `between the two the next one is the follow-up`() {
        val from = at(utc, day, first, minute = 30)
        assertEquals(at(utc, day, followUp), NudgeSchedule.nextCheckpointAfter(from, utc, first))
    }

    /**
     * Strictly after, and that is load-bearing: [com.checkin.app.notify.nudge.NudgeAlarmReceiver]
     * re-arms from inside the pass the checkpoint just triggered, so an inclusive comparison would
     * schedule the instant that had only just fired and spin on it.
     */
    @Test
    fun `an instant exactly on a checkpoint yields the following one`() {
        assertEquals(
            at(utc, day, followUp),
            NudgeSchedule.nextCheckpointAfter(at(utc, day, first), utc, first),
        )
        assertEquals(
            at(utc, day.plusDays(1), first),
            NudgeSchedule.nextCheckpointAfter(at(utc, day, followUp), utc, first),
        )
    }

    @Test
    fun `after the follow-up it rolls to tomorrow's first`() {
        val from = at(utc, day, 23, minute = 59)
        assertEquals(at(utc, day.plusDays(1), first), NudgeSchedule.nextCheckpointAfter(from, utc, first))
    }

    /** Changing the hour moves the next alarm with it — the reason both choosers re-arm at once. */
    @Test
    fun `a different chosen hour arms a different instant`() {
        val from = at(utc, day, 6)
        assertEquals(at(utc, day, 15), NudgeSchedule.nextCheckpointAfter(from, utc, 15))
    }

    /** The alarm is armed in local wall time, so the zone has to reach the calculation. */
    @Test
    fun `the zone decides which instant a checkpoint is`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val from = at(kolkata, day, first - 1)

        assertEquals(at(kolkata, day, first), NudgeSchedule.nextCheckpointAfter(from, kolkata, first))
    }

    /**
     * Through the calendar, not by adding hours to an epoch: on a spring-forward day the wall clock
     * loses an hour, so arithmetic on millis would drift every checkpoint after the transition.
     */
    @Test
    fun `a DST day still puts each checkpoint at its stated local hour`() {
        val newYork = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8)
        val from = at(newYork, springForward, 1)

        assertEquals(at(newYork, springForward, first), NudgeSchedule.nextCheckpointAfter(from, newYork, first))
        assertEquals(
            at(newYork, springForward, followUp),
            NudgeSchedule.nextCheckpointAfter(at(newYork, springForward, first), newYork, first),
        )
    }

    // --- nextAlarmAt ---

    @Test
    fun `with no retry the alarm is the next checkpoint`() {
        val from = at(utc, day, first, minute = 30)
        assertEquals(at(utc, day, followUp), NudgeSchedule.nextAlarmAt(from, utc, first, retryAtMs = null))
    }

    /** A gap-deferred follow-up is retried when the gap clears rather than left for tomorrow. */
    @Test
    fun `a retry before the next checkpoint wins`() {
        val from = at(utc, day, followUp)
        val retry = at(utc, day, followUp, minute = 20)
        assertEquals(retry, NudgeSchedule.nextAlarmAt(from, utc, first, retry))
    }

    @Test
    fun `a retry after the next checkpoint is ignored`() {
        val from = at(utc, day, first, minute = 30)
        val retry = at(utc, day, followUp, minute = 5)
        assertEquals(at(utc, day, followUp), NudgeSchedule.nextAlarmAt(from, utc, first, retry))
    }

    /** A retry not strictly ahead could re-arm the instant that just fired and spin on it. */
    @Test
    fun `a retry at or before now is ignored`() {
        val from = at(utc, day, followUp, minute = 10)
        assertEquals(
            at(utc, day.plusDays(1), first),
            NudgeSchedule.nextAlarmAt(from, utc, first, retryAtMs = from),
        )
    }

    /** Re-arming repeatedly must converge rather than walk the alarm forward a day at a time. */
    @Test
    fun `re-arming from the result is stable within the day`() {
        val from = at(utc, day, 6)
        val next = NudgeSchedule.nextCheckpointAfter(from, utc, first)
        val again = NudgeSchedule.nextCheckpointAfter(next - 1, utc, first)

        assertEquals(next, again)
        assertTrue(NudgeSchedule.nextCheckpointAfter(next, utc, first) > next)
    }
}
