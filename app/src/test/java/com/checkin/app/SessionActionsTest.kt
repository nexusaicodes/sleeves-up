package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.CheckInResult
import com.checkin.app.notify.StringResolver
import com.checkin.app.service.SessionActions
import com.checkin.app.service.SessionLifecycleRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SessionActionsTest {

    private class Rig(scope: CoroutineScope, onCheckedOut: suspend (CheckInSession) -> Unit = {}) {
        val time = FakeTimeSource(1_000L, LocalDate.of(2026, 6, 15))
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, time)
        val service = FakeServiceController()
        val alarms = FakeSessionAlarms()
        val nudges = FakePostedNudges()
        val celebrated = mutableListOf<CheckInSession>()
        val actions = SessionActions(
            repository = repo,
            serviceController = service,
            lifecycle = SessionLifecycleRunner(
                repo,
                FakeNotifier(),
                StringResolver {
                    "copy-$it"
                },
                alarms,
                FakeSessionZones(),
                time,
            ),
            postedNudges = nudges,
            scope = scope,
            onCheckedOut = {
                celebrated += it
                onCheckedOut(it)
            },
        )
    }

    private fun TestScope.rig(onCheckedOut: suspend (CheckInSession) -> Unit = {}) = Rig(backgroundScope, onCheckedOut)

    @Test
    fun `a check-in starts the timer, arms both alarms and retires the habit's nudge`() =
        runTest(UnconfinedTestDispatcher()) {
            val rig = rig()

            val result = rig.actions.open(Habit.SEED_ID)

            assertTrue(result is CheckInResult.Opened)
            assertEquals(1, rig.service.started.size)
            assertEquals(1, rig.alarms.reminders.size)
            assertEquals(1, rig.alarms.dayBoundaries.size)
            assertEquals(listOf(Habit.SEED_ID), rig.nudges.retired)
        }

    /** Re-arming an open session would reset its reminder count; nothing new was opened. */
    @Test
    fun `a check-in over an open session arms nothing`() = runTest(UnconfinedTestDispatcher()) {
        val rig = rig()
        rig.actions.open(Habit.SEED_ID)
        val armedBefore = rig.alarms.reminders.size

        val result = rig.actions.open(Habit.SEED_ID)

        assertTrue(result is CheckInResult.AlreadyOpen)
        assertEquals(armedBefore, rig.alarms.reminders.size)
        assertEquals(1, rig.service.started.size)
    }

    @Test
    fun `a check-out closes the row, cancels the alarms, stops the service and celebrates`() =
        runTest(UnconfinedTestDispatcher()) {
            val rig = rig()
            rig.actions.open(Habit.SEED_ID)
            val cancelsBefore = rig.alarms.cancelCount

            val closed = rig.actions.close(ClosedBy.IN_APP)

            assertNotNull(closed)
            assertEquals(ClosedBy.IN_APP, closed!!.closedBy)
            assertEquals(cancelsBefore + 1, rig.alarms.cancelCount)
            assertEquals(1, rig.service.stopCount)
            assertEquals(listOf(closed), rig.celebrated)
        }

    /**
     * Whoever closed the row cancelled and stopped. A cancel from a call that closed nothing could
     * land on the alarms of a session opened since.
     */
    @Test
    fun `a check-out that closes nothing touches nothing`() = runTest(UnconfinedTestDispatcher()) {
        val rig = rig()
        rig.actions.open(Habit.SEED_ID)
        rig.actions.close(ClosedBy.IN_APP)
        rig.actions.open(Habit.SEED_ID)
        val cancelsBefore = rig.alarms.cancelCount
        // Another closer — the day boundary — gets there first.
        rig.repo.checkOutAt(rig.repo.getActiveSession()!!.id, 2_000L, ClosedBy.DAY_BOUNDARY)

        val closed = rig.actions.close(ClosedBy.TIMER_NOTIFICATION)

        assertNull(closed)
        assertEquals(cancelsBefore, rig.alarms.cancelCount)
        assertEquals(1, rig.service.stopCount)
        assertEquals(1, rig.celebrated.size)
    }

    /** The launches run on the application scope, which has no handler: a throw would end the process. */
    @Test
    fun `a failing check-out in the background throws nothing out of its scope`() =
        runTest(UnconfinedTestDispatcher()) {
            val rig = rig(onCheckedOut = { error("celebration failed") })
            rig.actions.open(Habit.SEED_ID)

            rig.actions.checkOut(ClosedBy.IN_APP)

            assertEquals(1, rig.celebrated.size)
            assertNotNull(rig.dao.getSessionById(rig.celebrated.single().id)!!.stoppedAt)
        }

    @Test
    fun `the background check-in runs on the given scope`() = runTest(UnconfinedTestDispatcher()) {
        val rig = rig()

        rig.actions.checkIn(Habit.SEED_ID)

        assertNotNull(rig.repo.getActiveSession())
        assertEquals(1, rig.alarms.dayBoundaries.size)
    }
}
