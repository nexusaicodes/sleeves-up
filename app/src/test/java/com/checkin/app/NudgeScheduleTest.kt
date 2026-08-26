package com.checkin.app

import com.checkin.app.notify.nudge.NudgeSchedule
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

    private fun at(zone: ZoneId, date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // --- checkpointAt ---

    @Test
    fun `each checkpoint hour maps to itself`() {
        NudgeSchedule.Checkpoint.entries.forEach { checkpoint ->
            assertEquals(checkpoint, NudgeSchedule.checkpointAt(checkpoint.hour))
        }
    }

    /**
     * Bands, not thresholds. If this ever returned the *first* matching checkpoint instead of the
     * last, every hour past the morning one would resolve to MORNING and two thirds of the copy would
     * become unreachable — silently, since nothing else would change.
     */
    @Test
    fun `an hour inside a band maps to that band's checkpoint`() {
        val afternoon = NudgeSchedule.Checkpoint.AFTERNOON
        val evening = NudgeSchedule.Checkpoint.EVENING

        assertEquals(NudgeSchedule.Checkpoint.MORNING, NudgeSchedule.checkpointAt(afternoon.hour - 1))
        assertEquals(afternoon, NudgeSchedule.checkpointAt(evening.hour - 1))
        assertEquals(evening, NudgeSchedule.checkpointAt(23))
    }

    /**
     * The hours before the first checkpoint are what keep a nudge out of the small hours, and they do
     * it without a separate quiet-hours mechanism — the app deliberately has none.
     */
    @Test
    fun `hours before the first checkpoint map to nothing`() {
        for (hour in 0 until NudgeSchedule.Checkpoint.MORNING.hour) {
            assertNull("hour $hour should be outside every band", NudgeSchedule.checkpointAt(hour))
        }
    }

    /**
     * Both lookups walk [NudgeSchedule.Checkpoint.entries] in declaration order and assume it ascends
     * — `checkpointAt` takes the last match, `nextCheckpointAfter` the first instant still ahead. A
     * reordered enum would break both silently, so the invariant is pinned rather than assumed.
     */
    @Test
    fun `checkpoints are declared in ascending hour order`() {
        val hours = NudgeSchedule.Checkpoint.entries.map { it.hour }
        assertEquals(hours.sorted(), hours)
        assertEquals(hours.size, hours.toSet().size)
    }

    /** Every band is reachable, so no declared nudge can be orphaned by the mapping. */
    @Test
    fun `every checkpoint is reachable from some hour`() {
        val reached = (0..23).mapNotNull { NudgeSchedule.checkpointAt(it) }.toSet()
        assertEquals(NudgeSchedule.Checkpoint.entries.toSet(), reached)
    }

    // --- nextCheckpointAfter ---

    @Test
    fun `before the first checkpoint the next one is today's first`() {
        val from = at(utc, day, NudgeSchedule.Checkpoint.MORNING.hour - 2)
        assertEquals(at(utc, day, NudgeSchedule.Checkpoint.MORNING.hour), NudgeSchedule.nextCheckpointAfter(from, utc))
    }

    @Test
    fun `between two checkpoints the next one is the later of them`() {
        val from = at(utc, day, NudgeSchedule.Checkpoint.MORNING.hour, minute = 30)
        assertEquals(
            at(utc, day, NudgeSchedule.Checkpoint.AFTERNOON.hour),
            NudgeSchedule.nextCheckpointAfter(from, utc),
        )
    }

    /**
     * Strictly after, and that is load-bearing: [com.checkin.app.notify.nudge.NudgeAlarmReceiver]
     * re-arms from inside the pass the checkpoint just triggered, so an inclusive comparison would
     * schedule the instant that had only just fired and spin on it.
     */
    @Test
    fun `an instant exactly on a checkpoint yields the following one`() {
        val from = at(utc, day, NudgeSchedule.Checkpoint.MORNING.hour)
        assertEquals(
            at(utc, day, NudgeSchedule.Checkpoint.AFTERNOON.hour),
            NudgeSchedule.nextCheckpointAfter(from, utc),
        )
    }

    @Test
    fun `after the last checkpoint it rolls to tomorrow's first`() {
        val from = at(utc, day, 23, minute = 59)
        assertEquals(
            at(utc, day.plusDays(1), NudgeSchedule.Checkpoint.MORNING.hour),
            NudgeSchedule.nextCheckpointAfter(from, utc),
        )
    }

    /** The alarm is armed in local wall time, so the zone has to reach the calculation. */
    @Test
    fun `the zone decides which instant a checkpoint is`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val from = at(kolkata, day, NudgeSchedule.Checkpoint.MORNING.hour - 1)

        assertEquals(
            at(kolkata, day, NudgeSchedule.Checkpoint.MORNING.hour),
            NudgeSchedule.nextCheckpointAfter(from, kolkata),
        )
    }

    /**
     * Through the calendar, not by adding hours to an epoch: on a spring-forward day the wall clock
     * loses an hour, so arithmetic on millis would drift every checkpoint after the transition.
     */
    @Test
    fun `a DST day still puts each checkpoint at its stated local hour`() {
        val newYork = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8)
        val from = at(newYork, springForward, NudgeSchedule.Checkpoint.MORNING.hour - 1)

        val next = NudgeSchedule.nextCheckpointAfter(from, newYork)

        assertEquals(at(newYork, springForward, NudgeSchedule.Checkpoint.MORNING.hour), next)
    }

    /** Re-arming repeatedly must converge rather than walk the alarm forward a day at a time. */
    @Test
    fun `re-arming from the result is stable within the day`() {
        val from = at(utc, day, 8)
        val first = NudgeSchedule.nextCheckpointAfter(from, utc)
        val second = NudgeSchedule.nextCheckpointAfter(first - 1, utc)

        assertEquals(first, second)
        assertTrue(NudgeSchedule.nextCheckpointAfter(first, utc) > first)
    }
}
