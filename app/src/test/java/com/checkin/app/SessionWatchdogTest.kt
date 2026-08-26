package com.checkin.app

import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.StringResolver
import com.checkin.app.service.SessionLifecycleRunner
import com.checkin.app.service.SessionWatchdog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * An open session with no service timing it is a reachable state — `START_STICKY` is best effort —
 * and an invisible one: the Check-In screen renders its running timer straight from the row, so the
 * app reads as healthy while the notification and any chance of noticing are both gone.
 *
 * The **alarms** are repaired separately from the service, because they are lost separately: a force
 * stop and a package replace cancel a package's alarms while a plain process kill does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionWatchdogTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val now = today.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val time = FakeTimeSource(now, today)
    private val dao = FakeCheckInSessionDao()
    private val repository = CheckInRepository(dao, time)
    private val controller = FakeServiceController()
    private val alarms = FakeSessionAlarms()

    private val reminder = SessionLifecycleRunner(
        repository = repository,
        notifier = FakeNotifier(),
        strings = StringResolver { "copy-$it" },
        alarms = alarms,
        timeSource = time,
    )

    private fun watchdog(serviceRunning: Boolean) =
        SessionWatchdog(repository, controller, reminder, time) { serviceRunning }

    @Test
    fun `an open session with no service is revived`() = runTest {
        val session = repository.checkIn()

        val acted = watchdog(serviceRunning = false).reviveIfNeeded()

        assertTrue(acted)
        assertEquals(listOf(session.id), controller.revived)
    }

    /**
     * The revive must never go through the check-in path. That one takes the session's timing from
     * the intent and re-anchors the reminder cadence from it, which is right for a session that has
     * not started and wrong for one already running — the next reminder would land a full interval
     * after the revive rather than after the session.
     */
    @Test
    fun `a revive never uses the check-in path`() = runTest {
        repository.checkIn()

        watchdog(serviceRunning = false).reviveIfNeeded()

        assertTrue("revive must not start a fresh timer", controller.started.isEmpty())
        assertEquals(1, controller.revived.size)
    }

    @Test
    fun `a live service is left alone`() = runTest {
        repository.checkIn()

        val acted = watchdog(serviceRunning = true).reviveIfNeeded()

        assertFalse(acted)
        assertTrue(controller.revived.isEmpty())
        assertEquals(0, controller.reviveAttempts)
    }

    /**
     * The whole point of splitting the two repairs. A package replace cancels the alarms and leaves
     * the service free to restart itself, so a watchdog that checked the service first would find
     * nothing wrong and walk away from a session with no day-boundary close standing.
     */
    @Test
    fun `alarms are ensured even when the service is healthy`() = runTest {
        repository.checkIn()

        watchdog(serviceRunning = true).reviveIfNeeded()

        assertEquals(1, alarms.reminders.size)
        assertEquals(1, alarms.dayBoundaries.size)
    }

    /** No open row means nothing to time — starting a service here would post an orphan timer. */
    @Test
    fun `no open session means no revive`() = runTest {
        val acted = watchdog(serviceRunning = false).reviveIfNeeded()

        assertFalse(acted)
        assertTrue(controller.revived.isEmpty())
    }

    /** Alarms outliving their session are dropped rather than left to wake the device and find out. */
    @Test
    fun `no open session drops any alarms still standing`() = runTest {
        alarms.seedArmed(reminderAt = now + 1, boundaryAt = now + 2)

        watchdog(serviceRunning = false).reviveIfNeeded()

        assertEquals(1, alarms.cancelCount)
        assertEquals(0L, alarms.dayBoundaryAt)
    }

    @Test
    fun `a session already checked out is not revived`() = runTest {
        val session = repository.checkIn()
        repository.checkOut(session.id, ClosedBy.IN_APP)

        val acted = watchdog(serviceRunning = false).reviveIfNeeded()

        assertFalse(acted)
        assertTrue(controller.revived.isEmpty())
    }

    /**
     * Starting a foreground service from the background is restricted, so a refusal is an ordinary
     * outcome for the hourly caller. It is absorbed rather than thrown, and the attempt still counts
     * as having acted — the next of the three callers is what retries.
     */
    @Test
    fun `a refused start is absorbed rather than thrown`() = runTest {
        repository.checkIn()
        controller.startAllowed = false

        val acted = watchdog(serviceRunning = false).reviveIfNeeded()

        assertTrue("the attempt still counts as having acted", acted)
        assertEquals("the revive was attempted", 1, controller.reviveAttempts)
        assertTrue("but was refused", controller.revived.isEmpty())
    }

    /**
     * Every caller is a fire-and-forget launch on a scope with no exception handler, so a throw
     * escaping here would reach the default handler and kill the process — a poor outcome for a
     * mechanism whose whole job is recovering from a process that already died once.
     */
    @Test
    fun `a throwing revive is absorbed rather than propagated`() = runTest {
        repository.checkIn()
        val exploding = SessionWatchdog(repository, controller, reminder, time) {
            error("service state unavailable")
        }

        val acted = exploding.reviveIfNeeded()

        assertFalse(acted)
        assertTrue(controller.revived.isEmpty())
    }
}
