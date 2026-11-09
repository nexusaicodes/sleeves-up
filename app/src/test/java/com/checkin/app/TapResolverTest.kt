package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitEdit
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.launch.TapNotices
import com.checkin.app.launch.TapResolver
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext

/**
 * What a tap from outside the app does to the rows and the signal, in order. [TapRouting][com.checkin.app.launch.TapRouting]
 * is tested alone in `TapRoutingTest`; this pins what is done with its answer: the reads before the
 * retire, the select before the raise, and that a failure raises and writes nothing.
 *
 * The signal is process-global, so every test clears it afterwards.
 */
class TapResolverTest {

    private val day = LocalDate.of(2026, 6, 15)
    private val time = FakeTimeSource(5_000L, day)
    private val sessionDao = FakeCheckInSessionDao()
    private val habitDao = FakeHabitDao()
    private val appState = FakeAppStateDao()
    private var uuids = 0
    private val habits = HabitRepository(habitDao, appState, sessionDao, time) { "uuid-${++uuids}" }
    private val sessions = CheckInRepository(sessionDao, time)
    private val nudges = FakePostedNudges()
    private val notices = RecordingNotices()

    @After
    fun tearDown() {
        PresenceCheckSignal.clear()
        PresenceCheckSignal.resolvingTaps.value = 0
    }

    private fun resolver(scope: CoroutineScope, sessions: CheckInRepository = this.sessions) =
        TapResolver(habits, sessions, nudges, time, scope, notices, EmptyCoroutineContext)

    private suspend fun create(name: String) = (habits.create(name) as HabitEdit.Saved).habit

    @Test
    fun `a nudge tap retires the nudge, selects its habit and raises its gate`() = runTest {
        val gym = create("Gym")

        resolver(this).nudgeTapped(gym.uuid)
        advanceUntilIdle()

        assertEquals(listOf(gym.id), nudges.retired)
        assertEquals(gym.id, appState.selected.value)
        assertEquals(PresenceCheckSignal.Request(Reason.CHECK_IN, gym.id), PresenceCheckSignal.request.value)
    }

    @Test
    fun `a nudge with no habit uuid is about the seed habit`() = runTest {
        resolver(this).nudgeTapped(habitUuid = null)
        advanceUntilIdle()

        assertEquals(listOf(Habit.SEED_ID), nudges.retired)
        assertEquals(PresenceCheckSignal.Request(Reason.CHECK_IN, Habit.SEED_ID), PresenceCheckSignal.request.value)
    }

    @Test
    fun `a nudge whose habit no longer exists does nothing`() = runTest {
        resolver(this).nudgeTapped("orphaned-uuid")
        advanceUntilIdle()

        assertTrue(nudges.retired.isEmpty())
        assertEquals(Reason.NONE, PresenceCheckSignal.request.value.reason)
        assertTrue(notices.events.isEmpty())
    }

    @Test
    fun `a nudge for one habit while another runs says so and opens no gate`() = runTest {
        val gym = create("Gym")
        sessions.open(Habit.SEED_ID)

        resolver(this).nudgeTapped(gym.uuid)
        advanceUntilIdle()

        // The nudge has still served its purpose, so it is retired.
        assertEquals(listOf(gym.id), nudges.retired)
        assertEquals(listOf("running:Seed"), notices.events)
        assertEquals(Reason.NONE, PresenceCheckSignal.request.value.reason)
    }

    @Test
    fun `a failed read leaves the nudge posted and raises nothing`() = runTest {
        val failing = CheckInRepository(ThrowingActiveSessionDao(sessionDao), time)

        resolver(this, sessions = failing).nudgeTapped(habitUuid = null)
        advanceUntilIdle()

        assertTrue(nudges.retired.isEmpty())
        assertEquals(listOf("not-resolved"), notices.events)
        assertEquals(Reason.NONE, PresenceCheckSignal.request.value.reason)
        assertEquals(0, PresenceCheckSignal.resolvingTaps.value)
    }

    @Test
    fun `the root is held from the moment of the tap until it resolves`() = runTest {
        resolver(this).widgetTapped()

        assertEquals(1, PresenceCheckSignal.resolvingTaps.value)
        advanceUntilIdle()
        assertEquals(0, PresenceCheckSignal.resolvingTaps.value)
    }

    @Test
    fun `the widget opens the only habit's gate, the picker for several, and check-out over an open session`() =
        runTest {
            resolver(this).widgetTapped()
            advanceUntilIdle()
            assertEquals(PresenceCheckSignal.Request(Reason.CHECK_IN, Habit.SEED_ID), PresenceCheckSignal.request.value)

            PresenceCheckSignal.clear()
            create("Gym")
            resolver(this).widgetTapped()
            advanceUntilIdle()
            assertTrue(PresenceCheckSignal.request.value.choosingHabit)

            PresenceCheckSignal.clear()
            sessions.open(Habit.SEED_ID)
            resolver(this).widgetTapped()
            advanceUntilIdle()
            assertEquals(Reason.CHECK_OUT_FROM_WIDGET, PresenceCheckSignal.request.value.reason)
        }

    @Test
    fun `a pick replaces the picker with that habit's gate`() = runTest {
        val gym = create("Gym")
        PresenceCheckSignal.choose(time.nowMillis())

        resolver(this).habitPicked(gym.id)
        advanceUntilIdle()

        assertEquals(PresenceCheckSignal.Request(Reason.CHECK_IN, gym.id), PresenceCheckSignal.request.value)
        assertEquals(gym.id, appState.selected.value)
    }

    @Test
    fun `a second pick while the first resolves is ignored`() = runTest {
        val gym = create("Gym")
        val resolver = resolver(this)

        resolver.habitPicked(gym.id)
        resolver.habitPicked(Habit.SEED_ID)
        advanceUntilIdle()

        assertEquals(PresenceCheckSignal.Request(Reason.CHECK_IN, gym.id), PresenceCheckSignal.request.value)
    }

    @Test
    fun `a check-in whose habit cannot be selected reports it and raises nothing`() = runTest {
        // Routing reads "nothing open", then a session opens before the select lands.
        val racing = object : CheckInSessionDao by sessionDao {
            var reads = 0
            override suspend fun getActiveSession(): CheckInSession? =
                if (reads++ == 0) null else sessionDao.getActiveSession()
        }
        val gym = create("Gym")
        val racingHabits = HabitRepository(habitDao, appState, racing, time) { "uuid-${++uuids}" }
        sessions.open(Habit.SEED_ID)

        TapResolver(racingHabits, CheckInRepository(racing, time), nudges, time, this, notices, EmptyCoroutineContext)
            .nudgeTapped(gym.uuid)
        advanceUntilIdle()

        assertEquals(listOf("not-resolved"), notices.events)
        assertEquals(Reason.NONE, PresenceCheckSignal.request.value.reason)
        assertEquals(Habit.SEED_ID, appState.selected.value)
    }

    private class RecordingNotices : TapNotices {
        val events = mutableListOf<String>()
        override fun runningElsewhere(runningName: String?) {
            events += "running:$runningName"
        }
        override fun notResolved() {
            events += "not-resolved"
        }
    }

    private class ThrowingActiveSessionDao(delegate: CheckInSessionDao) : CheckInSessionDao by delegate {
        override suspend fun getActiveSession(): CheckInSession? = error("database closed")
    }
}
