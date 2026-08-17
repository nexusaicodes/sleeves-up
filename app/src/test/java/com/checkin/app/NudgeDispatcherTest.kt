package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.notify.engagement.NudgeDispatcher
import com.checkin.app.notify.engagement.NudgeSchedule
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The dispatcher's failure mode is silent: a nudge logged but never posted looks identical in the
 * data to one nobody acted on, so the conversion rate quietly drops with nothing to point at. These
 * pin the invariant that the log only ever records what the platform actually accepted.
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

    private val time = FixedTime(triggerHour, today)
    private val notifier = FakeNotifier()
    private val log = FakeEngagementLog()
    private val dao = FakeCheckInSessionDao()

    private fun dispatcher(clock: FixedTime = time): NudgeDispatcher = NudgeDispatcher(
        strings = StringResolver { "copy-$it" },
        repository = CheckInRepository(dao, clock),
        install = FakeEngagementInstall(),
        notifier = notifier,
        log = log,
        timeSource = clock,
    )

    private suspend fun shownCount() = log.shownNudgesSince(0L).size

    @Test
    fun `an eligible nudge is posted and logged`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, sent)
        assertEquals(1, notifier.shown.size)
        // The log is the only record of a send, and the daily cap counts from it.
        assertEquals(1, shownCount())
    }

    /**
     * POST_NOTIFICATIONS is revocable at any time. A refused post that still logged SHOWN would put
     * an un-actionable event in the denominator and understate every conversion rate — and, since
     * the daily cap counts from the log, would burn one of the day's slots on a notification nobody
     * saw.
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

        val sent = dispatcher(FixedTime(beforeFirstCheckpoint, today)).runOnce()

        assertNull(sent)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(0, shownCount())
    }

    /**
     * The checkpoints carry distinct ids, so without this an evening nudge would stack under a
     * morning one still in the tray — two notifications saying the user hasn't checked in, which
     * reads as a stuck loop rather than as one message that came back.
     *
     * Asserted on the dispatch path rather than on [NudgeDispatcher.forceSend], because that is the
     * one that runs in production; a retirement proven only through the debug harness is a retirement
     * nothing pins.
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
     * Retirement is a scheduling decision, so it lives on the dispatch path and not in the shared
     * writer. Through the shared writer, previewing a checkpoint from the debug harness would clear a
     * genuine nudge the user had not answered yet — a harness that takes things out of the tray.
     */
    @Test
    fun `a forced send leaves the tray alone`() = runTest {
        dispatcher().forceSend(Nudge.NOT_CHECKED_IN_EVENING, variant = 0)

        assertTrue(notifier.cancelled.isEmpty())
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

        assertNull(dispatcher(FixedTime(laterInTheBand, today)).runOnce())
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

    /** The spec is what the tray and the dismissal receiver both read; a wrong field is invisible. */
    @Test
    fun `the posted spec carries the nudge's own id, channel and dismissal tag`() = runTest {
        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING.notificationId, spec.id)
        assertEquals(NotificationChannels.ENGAGEMENT, spec.channelId)
        assertEquals(EngagementSource.NUDGE, spec.tag?.source)
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING.name, spec.tag?.key)
    }

    /**
     * Bucketing is deterministic per install by design, so without an override the debug harness can
     * only ever preview whichever wording this device landed on.
     */
    @Test
    fun `a forced variant overrides the install's bucket`() = runTest {
        val dispatcher = dispatcher()
        val variantCount = NudgeCatalog.variants(Nudge.NOT_CHECKED_IN_MORNING).size

        repeat(variantCount) { dispatcher.forceSend(Nudge.NOT_CHECKED_IN_MORNING, variant = it) }

        val variants = log.events.value
            .filter { it.event == EngagementEventType.SHOWN.name }
            .map { it.variant }
        assertEquals((0 until variantCount).toList(), variants)
    }
}
