package com.checkin.app

import com.checkin.app.notify.nudge.NudgeCoordinator
import com.checkin.app.notify.nudge.NudgeSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every entry point runs on the application scope, which has no exception handler: a throw kills the
 * process, and a failure that arms nothing ends the checkpoint alarm's chain for good.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NudgeCoordinatorTest {

    private val time = FakeTimeSource(1_000L, LocalDate.of(2026, 6, 15))
    private val settings = FakeReminderSettings()
    private val fallbackAt = NudgeSchedule.nextBandAfter(1_000L, ZoneId.of("UTC"), settings.earliestMinute())

    private fun coordinator(
        alarms: FakeNudgeAlarms,
        scope: CoroutineScope,
        runOnce: suspend () -> Unit = {},
        rearm: suspend () -> Unit = {},
        firstRunPending: Boolean = false,
    ) = NudgeCoordinator(runOnce, rearm, alarms, settings, time, scope) { firstRunPending }

    @Test
    fun `a pass that reaches the dispatcher arms nothing of its own`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms()
        var passes = 0

        coordinator(alarms, backgroundScope, runOnce = { passes++ }).runPass()

        assertEquals(1, passes)
        assertTrue(alarms.armed.isEmpty())
    }

    @Test
    fun `a dispatcher that cannot be built arms the fallback band`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms()

        coordinator(alarms, backgroundScope, runOnce = { error("database will not open") }).runPass()

        assertEquals(listOf(fallbackAt), alarms.armed)
    }

    @Test
    fun `a failed re-arm arms the fallback band too`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms()

        coordinator(alarms, backgroundScope, rearm = { error("database will not open") }).rearmNow()

        assertEquals(listOf(fallbackAt), alarms.armed)
    }

    @Test
    fun `a fallback that cannot be armed throws nothing`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms(throwOnArm = true)

        coordinator(alarms, backgroundScope, runOnce = { error("database will not open") }).runPass()

        assertEquals(listOf(fallbackAt), alarms.armed)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not mistaken for a failure`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms()

        coordinator(alarms, backgroundScope, runOnce = { throw CancellationException("stopped") }).runPass()
    }

    @Test
    fun `the launched re-arm runs on the given scope and stays inside it`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms()

        coordinator(alarms, backgroundScope, rearm = { error("database will not open") }).launchRearm()

        assertEquals(listOf(fallbackAt), alarms.armed)
    }

    @Test
    fun `before first run ends no pass runs and nothing is armed`() = runTest(UnconfinedTestDispatcher()) {
        val alarms = FakeNudgeAlarms()
        var calls = 0
        val pending = coordinator(
            alarms,
            backgroundScope,
            runOnce = { calls++ },
            rearm = { calls++ },
            firstRunPending = true,
        )

        pending.runPass()
        pending.rearmNow()
        pending.launchRearm()
        pending.armFallback()

        assertEquals(0, calls)
        assertTrue(alarms.armed.isEmpty())
    }

    @Test
    fun `the gate is read at each call, so first run's end lets the next re-arm through`() =
        runTest(UnconfinedTestDispatcher()) {
            val alarms = FakeNudgeAlarms()
            var pending = true
            var rearms = 0
            val coordinator =
                NudgeCoordinator({}, { rearms++ }, alarms, settings, time, backgroundScope) { pending }

            coordinator.rearmNow()
            pending = false
            coordinator.rearmNow()

            assertEquals(1, rearms)
        }
}
