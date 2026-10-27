package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.CheckInResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CheckInRepositoryTest {

    @Test
    fun `checkIn attributes the session to the check-in local date`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1_700_000_000_000L, LocalDate.of(2026, 6, 15)))

        val id = repo.open().id
        val session = dao.getSessionById(id)!!

        assertEquals("2026-06-15", session.dateKey)
        assertEquals(1_700_000_000_000L, session.startedAt)
        assertNull(session.stoppedAt)
    }

    /**
     * Two gated paths can reach a check-in — the Check-In screen and a nudge tap — and either can
     * resolve while the other's gate is still up. A second open row would never be closed and its
     * hours would never reach `duration`, so the invariant is enforced in the one writer.
     */
    @Test
    fun `checkIn returns the open session rather than opening a second`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        val first = repo.open()
        val second = repo.checkIn(Habit.SEED_ID)

        assertEquals(CheckInResult.AlreadyOpen(first), second)
        assertEquals(1, dao.sessions.size)
    }

    @Test
    fun `checkIn opens a new session once the previous one is closed`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        val first = repo.open()
        repo.checkOut(first.id, ClosedBy.IN_APP)
        val second = repo.open()

        assertEquals(2, dao.sessions.size)
        assertEquals(first.id + 1, second.id)
    }

    @Test
    fun `checkOut records duration but keeps the check-in day even across midnight`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val checkInDay = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FakeTimeSource(1000L, checkInDay)).open().id

        // Check out the next calendar day: attribution stays on the check-in day (immutable).
        CheckInRepository(dao, FakeTimeSource(6000L, checkInDay.plusDays(1))).checkOut(id, ClosedBy.IN_APP)
        val session = dao.getSessionById(id)!!

        assertEquals(5000L, session.duration)
        assertEquals("2026-06-15", session.dateKey)
    }

    /**
     * The day-boundary close stamps the instant the day ended, not the instant its (inexact) alarm
     * landed. An alarm delivered hours late must not hand a forgotten session hours on a day it does
     * not belong to.
     */
    @Test
    fun `checkOutAt stamps the given instant rather than now`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val day = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FakeTimeSource(1000L, day)).open().id

        // "Now" is 60_000 — the alarm landed very late; the boundary it closes at is 9000.
        CheckInRepository(dao, FakeTimeSource(60_000L, day)).checkOutAt(id, 9000L, ClosedBy.IN_APP)
        val session = dao.getSessionById(id)!!

        assertEquals(9000L, session.stoppedAt)
        assertEquals(8000L, session.duration)
    }

    /**
     * A stop before the start is a changed system clock or a corrupt row. Flooring at zero keeps one
     * bad row from poisoning every total that sums durations.
     */
    @Test
    fun `checkOutAt floors a backwards duration at zero`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val day = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FakeTimeSource(5000L, day)).open().id

        CheckInRepository(dao, FakeTimeSource(5000L, day)).checkOutAt(id, 1000L, ClosedBy.IN_APP)

        assertEquals(0L, dao.getSessionById(id)!!.duration)
    }

    /**
     * The ending the CSV exports, and the writer that records the one nobody chose.
     *
     * It says what ended the session, never when: the day boundary's instant is computed rather than
     * observed, and that has no bearing on which value is written.
     */
    @Test
    fun `checkOutAt records that the boundary closed the session`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val session = repo.open()

        val closed = repo.checkOutAt(session.id, 5000L, ClosedBy.DAY_BOUNDARY)!!

        assertEquals(ClosedBy.DAY_BOUNDARY, closed.closedBy)
        assertEquals(ClosedBy.DAY_BOUNDARY, dao.getSessionById(session.id)!!.closedBy)
        // Recording it changes nothing else about the row.
        assertEquals(4000L, closed.duration)
        assertEquals("2026-06-15", closed.dateKey)
    }

    /** Each surface records itself, so the export can tell one gated check-out from another. */
    @Test
    fun `each gated surface records its own ending`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val first = repo.open()
        assertEquals(ClosedBy.TIMER_NOTIFICATION, repo.checkOut(first.id, ClosedBy.TIMER_NOTIFICATION)!!.closedBy)

        val second = repo.open()
        assertEquals(
            ClosedBy.REMINDER_NOTIFICATION,
            repo.checkOutAt(second.id, 9000L, ClosedBy.REMINDER_NOTIFICATION)!!.closedBy,
        )
    }

    /**
     * A session is closed once. The late day-boundary alarm and a tap on Check Out can both read the
     * row as open; the second write must not restamp the first, on a row nothing can edit.
     */
    @Test
    fun `a second close leaves the first standing`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val session = repo.open()

        val first = repo.checkOutAt(session.id, 5000L, ClosedBy.DAY_BOUNDARY)
        val second = repo.checkOutAt(session.id, 9000L, ClosedBy.IN_APP)

        assertNotNull(first)
        assertNull(second)
        val stored = dao.getSessionById(session.id)!!
        assertEquals(5000L, stored.stoppedAt)
        assertEquals(4000L, stored.duration)
        assertEquals(ClosedBy.DAY_BOUNDARY, stored.closedBy)
    }

    /** The guard is in the write, so it holds even for a caller that read the row while it was open. */
    @Test
    fun `the conditional write refuses a row already closed`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val session = repo.open()
        repo.checkOutAt(session.id, 5000L, ClosedBy.DAY_BOUNDARY)

        assertEquals(0, dao.closeIfOpen(session.id, 9000L, 8000L, ClosedBy.IN_APP))
        assertEquals(5000L, dao.getSessionById(session.id)!!.stoppedAt)
    }

    /**
     * Nullable rather than defaulted: an open session has been ended by nothing, and any value here
     * would be a claim about a stop that has not happened. The aggregates count endings, so a
     * default would also put a running session into one of the CSV's columns.
     */
    @Test
    fun `an open session has recorded no ending`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        val session = repo.open()

        assertNull(session.closedBy)
        assertNull(dao.getSessionById(session.id)!!.closedBy)
    }

    /**
     * One open session across all habits: a check-in to B while A is open must write nothing and
     * hand back A's row as what it is — never as though it were B's, or the caller would start a
     * timer and arm alarms for the wrong record.
     */
    @Test
    fun `checkIn to another habit while one is open writes nothing`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val gym = repo.open(habitId = GYM)

        assertEquals(CheckInResult.OtherHabitOpen(gym), repo.checkIn(WRITING))
        assertEquals(1, dao.sessions.size)
    }

    /** Two writers resolving together — the Check-In screen and a nudge tap — must open one row. */
    @Test
    fun `concurrent check-ins open one session`() = runBlocking {
        val fake = FakeCheckInSessionDao()
        val interleaving = object : CheckInSessionDao by fake {
            override suspend fun getActiveSession(): CheckInSession? = fake.getActiveSession().also { yield() }
        }
        val repo = CheckInRepository(interleaving, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        val results = listOf(async { repo.checkIn(GYM) }, async { repo.checkIn(WRITING) }).awaitAll()

        assertEquals(1, fake.sessions.size)
        assertEquals(1, results.count { it is CheckInResult.Opened })
        assertTrue(results.any { it is CheckInResult.OtherHabitOpen })
    }

    @Test
    fun `a check-in is filed under the habit it was made for`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        assertEquals(WRITING, repo.open(habitId = WRITING).habitId)
    }

    // Two-habit leak tests: every scoped read, seeded with both habits on the same days, must see
    // only the habit it was asked about. Nothing in the app pools two habits' records.

    @Test
    fun `daily aggregates never include another habit's sessions`() = runBlocking {
        val repo = twoHabitRepository()

        val gym = repo.getDailySummaries(GYM, "2026-06-01", "2026-06-30")
        val writing = repo.dailyAggregatesFlow(WRITING, "2026-06-01", "2026-06-30").first()

        assertEquals(setOf("2026-06-10"), gym.keys)
        assertEquals(GYM_MS, gym.getValue("2026-06-10").totalDurationMs)
        assertEquals(1, gym.getValue("2026-06-10").sessionCount)
        assertEquals(listOf("2026-06-10", "2026-06-12"), writing.map { it.dateKey })
        assertEquals(WRITING_MS, writing.first().totalDurationMs)
    }

    @Test
    fun `a day's sessions never include another habit's`() = runBlocking {
        val repo = twoHabitRepository()

        assertEquals(listOf(GYM), repo.getSessionsByDate(GYM, "2026-06-10").map { it.habitId })
        assertEquals(listOf(WRITING), repo.sessionsForDateFlow(WRITING, "2026-06-10").first().map { it.habitId })
    }

    @Test
    fun `session starts never include another habit's`() = runBlocking {
        val repo = twoHabitRepository()

        assertEquals(listOf(GYM_START), repo.sessionStartsFlow(GYM, "2026-06-01", "2026-06-30").first())
    }

    /** Each habit's record begins at its own first session, not at whichever habit started first. */
    @Test
    fun `tracking start is each habit's own first day`() = runBlocking {
        val repo = twoHabitRepository()

        assertEquals(LocalDate.of(2026, 6, 10), repo.trackingStart(GYM))
        assertEquals(LocalDate.of(2026, 6, 8), repo.trackingStartFlow(WRITING).first())
        assertNull(repo.trackingStart(UNUSED))
    }

    /** The open-session reads stay unscoped: that is what keeps one open session a single query. */
    @Test
    fun `the open session is found whichever habit holds it`() = runBlocking {
        val repo = twoHabitRepository()

        val open = repo.getActiveSession()
        assertTrue(open != null && open.habitId == WRITING)
        assertEquals(open, repo.activeSessionFlow().first())
    }

    /** Gym on the 10th; writing on the 8th (open), 10th and 12th — overlapping days on purpose. */
    private fun twoHabitRepository(): CheckInRepository {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-10", startedAt = GYM_START, durationMs = GYM_MS, habitId = GYM)
        dao.seedCompleted("2026-06-10", startedAt = 2_000L, durationMs = WRITING_MS, habitId = WRITING)
        dao.seedCompleted("2026-06-12", startedAt = 3_000L, durationMs = WRITING_MS, habitId = WRITING)
        dao.seedOpen("2026-06-08", startedAt = 500L, habitId = WRITING)
        return CheckInRepository(dao, FakeTimeSource(10_000L, LocalDate.of(2026, 6, 15)))
    }

    private companion object {
        const val GYM = Habit.SEED_ID
        const val WRITING = 2L
        const val UNUSED = 3L
        const val GYM_START = 1_000L
        const val GYM_MS = 40 * 60_000L
        const val WRITING_MS = 90 * 60_000L
    }
}

/** A check-in that must open a session; fails the test outright when it does not. */
internal suspend fun CheckInRepository.open(habitId: Long = Habit.SEED_ID): CheckInSession =
    (checkIn(habitId) as CheckInResult.Opened).session
