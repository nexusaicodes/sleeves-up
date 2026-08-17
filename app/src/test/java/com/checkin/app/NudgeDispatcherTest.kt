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

    private fun dispatcher(clock: FixedTime = time): NudgeDispatcher = NudgeDispatcher(
        strings = StringResolver { "copy-$it" },
        repository = CheckInRepository(FakeCheckInSessionDao(), clock),
        install = FakeEngagementInstall(),
        notifier = notifier,
        log = log,
        timeSource = clock,
    )

    @Test
    fun `an eligible nudge is posted and logged`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, sent)
        assertEquals(1, notifier.shown.size)
        // The log is the only record of a send, and the daily cap counts from it.
        assertEquals(1, log.shownCountSince(0L))
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
        assertEquals(0, log.shownCountSince(0L))
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
        assertEquals(0, log.shownCountSince(0L))
    }

    /**
     * The checkpoints carry distinct ids, so without this an evening nudge would stack under a
     * morning one still in the tray — two notifications saying the user hasn't checked in, which
     * reads as a stuck loop rather than as one message that came back.
     */
    @Test
    fun `posting a nudge retires the day's other checkpoints`() = runTest {
        dispatcher().forceSend(Nudge.NOT_CHECKED_IN_EVENING, variant = 0)

        val expected = Nudge.entries.filter { it != Nudge.NOT_CHECKED_IN_EVENING }.map { it.notificationId }
        assertEquals(expected.toSet(), notifier.cancelled.toSet())
        assertTrue(Nudge.NOT_CHECKED_IN_EVENING.notificationId !in notifier.cancelled)
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
