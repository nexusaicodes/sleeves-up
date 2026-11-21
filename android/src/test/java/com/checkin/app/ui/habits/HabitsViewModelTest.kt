package com.checkin.app.ui.habits

import com.checkin.app.data.NameProblem
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.ArchiveResult
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitEdit
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.testing.FakeAppStateDao
import com.checkin.app.testing.FakeCheckInSessionDao
import com.checkin.app.testing.FakeHabitDao
import com.checkin.app.testing.FakeReminderSettings
import com.checkin.app.testing.FakeTimeSource
import com.checkin.app.testing.MainDispatcherRule
import com.checkin.app.testing.open
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The Habits screen's writes: what each stores, what it reports back, and when it reports it. */
class HabitsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val time = FakeTimeSource(5_000L, LocalDate.of(2026, 6, 15))
    private val sessionDao = FakeCheckInSessionDao()
    private val habitDao = FakeHabitDao()
    private val appState = FakeAppStateDao()
    private var uuids = 0
    private val habits = HabitRepository(habitDao, appState, sessionDao, time) { "uuid-${++uuids}" }
    private val sessions = CheckInRepository(sessionDao, time)
    private val reminders = FakeReminderSettings()
    private var rearms = 0

    private fun viewModel() = HabitsViewModel(habits, sessions, reminders) { rearms++ }

    @Test
    fun `a new habit stores its time, re-arms, reports success and is not selected`() = runTest {
        var result: NameProblem? = NameProblem.BLANK

        viewModel().save(habitId = null, name = "Gym", minute = 7 * 60) { result = it }
        advanceUntilIdle()

        val gym = habitDao.activeHabits().single { it.name == "Gym" }
        assertNull(result)
        assertEquals(7 * 60, reminders.reminderMinute(gym.uuid))
        assertEquals(1, rearms)
        assertEquals(Habit.SEED_ID, appState.selected.value)
    }

    @Test
    fun `a name the write refuses is reported and nothing is stored`() = runTest {
        habits.create("Gym")
        var result: NameProblem? = null

        viewModel().save(habitId = null, name = "gym", minute = 7 * 60) { result = it }
        advanceUntilIdle()

        assertEquals(NameProblem.DUPLICATE, result)
        assertEquals(2, habitDao.activeHabits().size)
        assertEquals(0, rearms)
    }

    @Test
    fun `renaming a habit that is gone says so and reports nothing wrong with the name`() = runTest {
        var called = false
        var result: NameProblem? = NameProblem.BLANK
        val viewModel = viewModel()
        val events = mutableListOf<HabitsEvent>()
        // Unconfined, so each event lands as it is sent: advanceUntilIdle does not wait on background work.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.save(habitId = 999L, name = "Gym", minute = 7 * 60) {
            called = true
            result = it
        }
        advanceUntilIdle()

        assertTrue(called)
        assertNull(result)
        assertEquals(listOf<HabitsEvent>(HabitsEvent.SaveMissing), events)
        assertEquals(0, rearms)
    }

    @Test
    fun `an unchanged time on rename re-arms nothing`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        reminders.setReminderMinute(gym.uuid, 8 * 60)

        viewModel().save(habitId = gym.id, name = "Gym club", minute = 8 * 60)
        advanceUntilIdle()

        assertEquals("Gym club", habitDao.byId(gym.id)?.name)
        assertEquals(0, rearms)
    }

    @Test
    fun `selecting calls back only once the selection is stored`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        var selectedWhenCalled: Long? = null

        viewModel().select(gym.id) { selectedWhenCalled = appState.selected.value }
        advanceUntilIdle()

        assertEquals(gym.id, selectedWhenCalled)
    }

    @Test
    fun `archiving reports what happened, including each refusal`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        val viewModel = viewModel()
        val events = mutableListOf<HabitsEvent>()
        // Unconfined, so each event lands as it is sent: advanceUntilIdle does not wait on background work.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        sessions.open(gym.id)
        viewModel.archive(gym)
        advanceUntilIdle()
        sessions.checkOutAt(sessionDao.getActiveSession()!!.id, 6_000L, ClosedBy.IN_APP)
        viewModel.archive(gym)
        advanceUntilIdle()
        viewModel.archive(habitDao.byId(Habit.SEED_ID)!!)
        advanceUntilIdle()

        assertEquals(
            listOf(
                HabitsEvent.ArchiveRefused(ArchiveResult.RUNNING, "Gym"),
                HabitsEvent.Archived("Gym"),
                HabitsEvent.ArchiveRefused(ArchiveResult.LAST_ACTIVE, "Seed"),
            ),
            events,
        )
    }

    @Test
    fun `restoring a habit whose name is taken again is refused`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        habits.archive(gym.id)
        habits.create("Gym")
        val viewModel = viewModel()
        val events = mutableListOf<HabitsEvent>()
        // Unconfined, so each event lands as it is sent: advanceUntilIdle does not wait on background work.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.restore(habitDao.byId(gym.id)!!)
        advanceUntilIdle()

        assertEquals(listOf<HabitsEvent>(HabitsEvent.RestoreRefused("Gym")), events)
        assertTrue(habitDao.byId(gym.id)!!.archived)
    }

    @Test
    fun `the state names the running habit and every active one with its time`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        reminders.setReminderMinute(gym.uuid, 6 * 60)
        sessions.open(gym.id)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.loading }
        assertEquals(gym.id, state.running?.id)
        assertEquals(6 * 60, state.active.single { it.habit.id == gym.id }.reminderMinute)
    }
}
