package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeCatalog
import com.checkin.app.notify.nudge.NudgeDispatcher
import com.checkin.app.notify.nudge.NudgeSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The dispatcher's failure mode is silent: a send recorded for a notification that never reached
 * the tray spends one of the day's two slots, so the checkpoint that would have been shown is
 * suppressed by a message nobody saw. These pin that the log only records what the platform took.
 *
 * Copy resolution sits behind [StringResolver], which is what keeps the dispatcher reachable from a
 * JVM-only suite — a `Context` for `getString` would put it out of reach.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NudgeDispatcherTest {

    // The morning checkpoint, local, on a day the user hasn't checked in. Derived from the schedule
    // rather than written out, so retuning it doesn't silently stop these tests exercising the path
    // they were written for.
    private val today = LocalDate.of(2026, 6, 15)
    private val triggerHour = today.atTime(NudgeSchedule.Checkpoint.MORNING.hour, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val time = FakeTimeSource(triggerHour, today)
    private val notifier = FakeNotifier()
    private val log = FakeNudgeSendLog()
    private val dao = FakeCheckInSessionDao()

    private fun dispatcher(clock: FakeTimeSource = time): NudgeDispatcher = NudgeDispatcher(
        strings = StringResolver { "copy-$it" },
        repository = CheckInRepository(dao, clock),
        notifier = notifier,
        log = { log },
        timeSource = clock,
    )

    private suspend fun shownCount() = log.sentSince(0L).size

    @Test
    fun `an eligible nudge is posted and logged`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, sent)
        assertEquals(1, notifier.shown.size)
        // The log is the only record of a send, and the daily cap counts from it.
        assertEquals(1, shownCount())
    }

    /**
     * POST_NOTIFICATIONS is revocable at any time, and the daily cap counts from the log — so a
     * refused post that still recorded would burn one of the day's slots on a notification nobody
     * saw, and the later checkpoint that slot belonged to would never fire.
     */
    @Test
    fun `a refused post records nothing`() = runTest {
        notifier.refuse = true

        val sent = dispatcher().runOnce()

        assertNull(sent)
        assertEquals(0, shownCount())
    }

    /**
     * Ineligibility is decided by the rules alone — there is no opt-out pref to switch off, since a
     * notification's opt-out is its channel and a blocked one is refused at [FakeNotifier] instead.
     * An hour before the first checkpoint is the cheapest genuine ineligibility. The load-bearing
     * assertion is the log one: nothing eligible must record nothing, or the daily cap counts a send
     * that never happened and burns one of the day's two slots.
     */
    @Test
    fun `nothing is posted when no nudge is eligible`() = runTest {
        val beforeFirstCheckpoint = today.atTime(NudgeSchedule.Checkpoint.MORNING.hour - 1, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sent = dispatcher(FakeTimeSource(beforeFirstCheckpoint, today)).runOnce()

        assertNull(sent)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(0, shownCount())
    }

    /**
     * The checkpoints carry distinct ids, so without this an evening nudge would stack under a
     * morning one still in the tray — two notifications saying the user hasn't checked in, which
     * reads as a stuck loop rather than as one message that came back.
     */
    @Test
    fun `a dispatched nudge retires the day's other checkpoints`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, sent)
        val expected = Nudge.entries.filter { it != Nudge.NOT_CHECKED_IN_MORNING }.map { it.notificationId }
        assertEquals(expected.toSet(), notifier.cancelled.toSet())
        assertTrue(Nudge.NOT_CHECKED_IN_MORNING.notificationId !in notifier.cancelled)
    }

    /**
     * The regression this whole rule exists for. A checkpoint band is hours wide and the hourly
     * worker asks inside it, so an 11:00 pass would repost the 10:00 nudge under the same id with the
     * same copy — re-alerting on a high-importance channel — and spend the second of the day's two
     * slots, leaving the afternoon and evening copy unreachable.
     */
    @Test
    fun `a second pass inside the same band sends nothing`() = runTest {
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, dispatcher().runOnce())

        val laterInTheBand = today.atTime(NudgeSchedule.Checkpoint.AFTERNOON.hour - 1, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertNull(dispatcher(FakeTimeSource(laterInTheBand, today)).runOnce())
        assertEquals(1, shownCount())
        assertEquals(1, notifier.shown.size)
    }

    /**
     * A session still open from an earlier day is not evidence the user is present — it is one whose
     * day-boundary close was lost to a force stop or a package replace. Suppressing on it left such a
     * session silencing every nudge indefinitely, on exactly the device that had stopped being
     * opened, and the nudge it silenced was the one that would have surfaced the problem.
     */
    @Test
    fun `an open session from an earlier day does not suppress`() = runTest {
        dao.seedOpen(dateKey = today.minusDays(1).toString(), startedAt = triggerHour - 24 * 60 * 60 * 1000L)

        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, dispatcher().runOnce())
    }

    /** The mirror image: a session opened today is a check-in, and the day's nudges are done. */
    @Test
    fun `a session opened today suppresses`() = runTest {
        dao.seedOpen(dateKey = today.toString(), startedAt = triggerHour)

        assertNull(dispatcher().runOnce())
        assertEquals(0, shownCount())
    }

    /** The spec is what reaches the tray; a wrong id or channel is invisible until a device shows it. */
    @Test
    fun `the posted spec carries the nudge's own id and channel`() = runTest {
        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING.notificationId, spec.id)
        assertEquals(NotificationChannels.NUDGE, spec.channelId)
    }

    /**
     * The catalog is the only source of a nudge's wording — there is no variant bucket and no
     * override — so a dispatcher that resolved copy any other way would show a string no test reads.
     */
    @Test
    fun `the posted copy is the nudge's registered copy`() = runTest {
        val copy = NudgeCatalog.copyFor(Nudge.NOT_CHECKED_IN_MORNING)

        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals("copy-${copy.titleRes}", spec.title)
        assertEquals("copy-${copy.bodyRes}", spec.body)
    }

    /** Retiring is what stops a stale nudge sending the user through the gate to an open session. */
    @Test
    fun `retireAll cancels every nudge id`() = runTest {
        dispatcher().retireAll()

        assertEquals(Nudge.entries.map { it.notificationId }.toSet(), notifier.cancelled.toSet())
    }
}
