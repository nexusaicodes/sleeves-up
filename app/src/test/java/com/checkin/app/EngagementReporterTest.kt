package com.checkin.app

import com.checkin.app.notify.engagement.DefaultEngagementReporter
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.log.EngagementEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngagementReporterTest {

    /**
     * Every kind, not whichever happens to be posted: this call site cannot tell which is, and the
     * checkpoints each carry their own id. A nudge left in the tray asks for a check-in that has
     * already happened — tapping it later runs the whole presence gate and then resolves to nothing.
     */
    @Test
    fun `checking in retires every nudge kind`() = runTest {
        val notifier = FakeNotifier()
        val reporter = DefaultEngagementReporter(notifier, FakeEngagementLog())

        reporter.onCheckedIn(atMillis = 1_000L)

        assertTrue(Nudge.entries.all { it.notificationId in notifier.cancelled })
    }

    @Test
    fun `a tapped nudge is retired straight away, not only once the check-in lands`() = runTest {
        val notifier = FakeNotifier()
        val reporter = DefaultEngagementReporter(notifier, FakeEngagementLog())

        reporter.onNudgeOpened(atMillis = 1_000L)

        assertTrue(Nudge.NOT_CHECKED_IN_MORNING.notificationId in notifier.cancelled)
    }

    /**
     * The tag names the notification outright, so the open is recorded without consulting the log for
     * what was shown most recently. Nothing was shown here at all: the last-shown fallback would find
     * no candidate and record nothing.
     */
    @Test
    fun `a tagged tap is attributed without inferring from what was shown last`() = runTest {
        val log = FakeEngagementLog()
        val reporter = DefaultEngagementReporter(FakeNotifier(), log)

        reporter.onNudgeOpened(atMillis = 1_000L, key = Nudge.NOT_CHECKED_IN_MORNING.name, variant = 1)

        val opened = log.events.value.single { it.event == EngagementEventType.OPENED.name }
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING.name, opened.key)
        assertEquals(1, opened.variant)
    }

    /**
     * A tagged tap carries its own identity, so it is not bounded by the attribution window the
     * fallback needs. Tapping a notification left in the tray overnight is still an open.
     */
    @Test
    fun `a tagged tap outside the attribution window is still recorded`() = runTest {
        val log = FakeEngagementLog()
        val reporter = DefaultEngagementReporter(FakeNotifier(), log)
        val day = 24 * 60 * 60 * 1000L
        log.record(Nudge.NOT_CHECKED_IN_MORNING, 0, EngagementEventType.SHOWN, atMillis = 0L)

        reporter.onNudgeOpened(atMillis = day, key = Nudge.NOT_CHECKED_IN_MORNING.name, variant = 0)

        assertEquals(1, log.events.value.count { it.event == EngagementEventType.OPENED.name })
    }

    /**
     * A notification posted by an older release carries no key, and its tap intent outlives the
     * update — so the inference path stays reachable.
     */
    @Test
    fun `an untagged tap falls back to the nudge shown most recently`() = runTest {
        val log = FakeEngagementLog()
        val reporter = DefaultEngagementReporter(FakeNotifier(), log)
        log.record(Nudge.NOT_CHECKED_IN_MORNING, 1, EngagementEventType.SHOWN, atMillis = 1_000L)

        reporter.onNudgeOpened(atMillis = 2_000L, key = null, variant = 0)

        val opened = log.events.value.single { it.event == EngagementEventType.OPENED.name }
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING.name, opened.key)
        // The variant comes from the showing, not from the caller's default.
        assertEquals(1, opened.variant)
    }

    /** A key naming a retired experiment is no more usable than none at all. */
    @Test
    fun `a tap tagged with an unknown nudge falls back`() = runTest {
        val log = FakeEngagementLog()
        val reporter = DefaultEngagementReporter(FakeNotifier(), log)

        reporter.onNudgeOpened(atMillis = 1_000L, key = "RETIRED_EXPERIMENT", variant = 0)

        assertTrue(log.events.value.none { it.event == EngagementEventType.OPENED.name })
    }
}
