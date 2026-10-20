package com.checkin.app

import com.checkin.app.data.NameProblem
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.ArchiveResult
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitEdit
import com.checkin.app.data.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HabitRepositoryTest {

    private val sessions = FakeCheckInSessionDao()
    private val habitDao = FakeHabitDao()
    private val appState = FakeAppStateDao()
    private var uuids = 0
    private val habits =
        HabitRepository(habitDao, appState, sessions, FakeTimeSource(5_000L, DAY)) { "uuid-${++uuids}" }
    private val checkIns = CheckInRepository(sessions, FakeTimeSource(5_000L, DAY))

    @Test
    fun `a new habit gets its own uuid and a channel built from it`() = runTest {
        val gym = (habits.create("  Gym  ") as HabitEdit.Saved).habit

        assertEquals("Gym", gym.name)
        assertEquals("uuid-1", gym.uuid)
        assertEquals(Habit.CHANNEL_PREFIX + "uuid-1", gym.channelId)
        assertEquals(5_000L, gym.createdAt)
    }

    @Test
    fun `a name another active habit has is refused at the write`() = runTest {
        habits.create("Gym")

        assertEquals(HabitEdit.Rejected(NameProblem.DUPLICATE), habits.create("gym"))
        assertEquals(HabitEdit.Rejected(NameProblem.BLANK), habits.rename(Habit.SEED_ID, "   "))
        assertEquals(2, habitDao.habits.size)
    }

    @Test
    fun `selecting stores the choice`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit

        assertTrue(habits.select(gym.id))
        assertEquals(gym.id, habits.selectedHabitIdFlow().first())
    }

    /** The switcher lock, enforced in the repository too, since a nudge tap selects as well. */
    @Test
    fun `nothing else can be selected while a session is open`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        checkIns.open(Habit.SEED_ID)

        assertFalse(habits.select(gym.id))
        assertEquals(Habit.SEED_ID, appState.selected.value)
    }

    /** While a session runs, every screen is about its habit, whatever the stored choice says. */
    @Test
    fun `the open session's habit is the one shown`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        appState.selected.value = Habit.SEED_ID
        checkIns.open(gym.id)

        assertEquals(gym.id, habits.selectedHabitIdFlow().first())
    }

    @Test
    fun `a stored selection no longer active falls back to the first active habit`() = runTest {
        appState.selected.value = 99L

        assertEquals(Habit.SEED_ID, habits.selectedHabitIdFlow().first())
    }

    /**
     * Every ViewModel rebuilds its whole graph of Room flows off this, so a write to `sessions` that
     * leaves the selection where it was must not emit — the dropped-invalidation window `dayTrigger`
     * is deduped to close.
     */
    @Test
    fun `the selection does not re-emit when a write leaves it unchanged`() = runTest(UnconfinedTestDispatcher()) {
        val emitted = mutableListOf<Long>()
        val job = launch { habits.selectedHabitIdFlow().toList(emitted) }

        val session = checkIns.open(Habit.SEED_ID)
        checkIns.checkOut(session.id, ClosedBy.IN_APP)
        habits.create("Gym")

        assertEquals(listOf(Habit.SEED_ID), emitted)
        job.cancel()
    }

    @Test
    fun `the habit with the open session cannot be archived`() = runTest {
        habits.create("Gym")
        checkIns.open(Habit.SEED_ID)

        assertEquals(ArchiveResult.RUNNING, habits.archive(Habit.SEED_ID))
        assertFalse(habitDao.byId(Habit.SEED_ID)!!.archived)
    }

    @Test
    fun `the last active habit cannot be archived`() = runTest {
        assertEquals(ArchiveResult.LAST_ACTIVE, habits.archive(Habit.SEED_ID))
    }

    @Test
    fun `archiving the selected habit moves the selection to one still active`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        habits.select(gym.id)

        assertEquals(ArchiveResult.ARCHIVED, habits.archive(gym.id))
        assertEquals(Habit.SEED_ID, appState.selected.value)
        assertFalse(habits.select(gym.id))
    }

    @Test
    fun `archiving another habit leaves the selection alone`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        val writing = (habits.create("Writing") as HabitEdit.Saved).habit
        habits.select(writing.id)

        habits.archive(gym.id)

        assertEquals(writing.id, appState.selected.value)
    }

    /** Archiving hides a record; it never touches one. */
    @Test
    fun `archiving keeps the habit's sessions`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        sessions.seedCompleted("2026-06-10", startedAt = 1_000L, durationMs = 60_000L, habitId = gym.id)

        habits.archive(gym.id)

        assertEquals(1, sessions.sessions.count { it.habitId == gym.id })
    }

    @Test
    fun `restoring into a name another active habit has taken is refused`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        habits.archive(gym.id)
        habits.create("Gym")

        assertEquals(HabitEdit.Rejected(NameProblem.DUPLICATE), habits.restore(gym.id))
        assertTrue(habitDao.byId(gym.id)!!.archived)
    }

    @Test
    fun `restoring brings a habit back with its channel id unchanged`() = runTest {
        val gym = (habits.create("Gym") as HabitEdit.Saved).habit
        habits.archive(gym.id)

        val restored = (habits.restore(gym.id) as HabitEdit.Saved).habit

        assertFalse(restored.archived)
        assertEquals(gym.channelId, habitDao.byId(gym.id)!!.channelId)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 6, 15)
    }
}
