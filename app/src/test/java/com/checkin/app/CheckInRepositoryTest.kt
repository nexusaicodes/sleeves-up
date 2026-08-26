package com.checkin.app

import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CheckInRepositoryTest {

    @Test
    fun `checkIn attributes the session to the check-in local date`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1_700_000_000_000L, LocalDate.of(2026, 6, 15)))

        val id = repo.checkIn().id
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

        val first = repo.checkIn()
        val second = repo.checkIn()

        assertEquals(first.id, second.id)
        assertEquals(1, dao.sessions.size)
    }

    @Test
    fun `checkIn opens a new session once the previous one is closed`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        val first = repo.checkIn()
        repo.checkOut(first.id, ClosedBy.IN_APP)
        val second = repo.checkIn()

        assertEquals(2, dao.sessions.size)
        assertEquals(first.id + 1, second.id)
    }

    @Test
    fun `checkOut records duration but keeps the check-in day even across midnight`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val checkInDay = LocalDate.of(2026, 6, 15)
        val id = CheckInRepository(dao, FakeTimeSource(1000L, checkInDay)).checkIn().id

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
        val id = CheckInRepository(dao, FakeTimeSource(1000L, day)).checkIn().id

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
        val id = CheckInRepository(dao, FakeTimeSource(5000L, day)).checkIn().id

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
        val session = repo.checkIn()

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
        val session = repo.checkIn()

        assertEquals(ClosedBy.TIMER_NOTIFICATION, repo.checkOut(session.id, ClosedBy.TIMER_NOTIFICATION)!!.closedBy)
        assertEquals(
            ClosedBy.REMINDER_NOTIFICATION,
            repo.checkOutAt(session.id, 9000L, ClosedBy.REMINDER_NOTIFICATION)!!.closedBy,
        )
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

        val session = repo.checkIn()

        assertNull(session.closedBy)
        assertNull(dao.getSessionById(session.id)!!.closedBy)
    }
}
