package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        repo.checkOut(first.id)
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
        CheckInRepository(dao, FakeTimeSource(6000L, checkInDay.plusDays(1))).checkOut(id)
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
        CheckInRepository(dao, FakeTimeSource(60_000L, day)).checkOutAt(id, 9000L)
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

        CheckInRepository(dao, FakeTimeSource(5000L, day)).checkOutAt(id, 1000L)

        assertEquals(0L, dao.getSessionById(id)!!.duration)
    }

    /**
     * The flag the CSV exports, and the only writer that sets it.
     *
     * A gated check-out is the user's own act, so it stays false however the instant was derived —
     * the flag records who ended the session, never when.
     */
    @Test
    fun `checkOutAt records that the boundary closed the session`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val session = repo.checkIn()

        val closed = repo.checkOutAt(session.id, 5000L, autoClosed = true)!!

        assertTrue(closed.autoClosed)
        assertTrue(dao.getSessionById(session.id)!!.autoClosed)
        // Recording it changes nothing else about the row.
        assertEquals(4000L, closed.duration)
        assertEquals("2026-06-15", closed.dateKey)
    }

    @Test
    fun `a gated check-out is not recorded as auto-closed`() = runBlocking {
        val dao = FakeCheckInSessionDao()
        val repo = CheckInRepository(dao, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        val session = repo.checkIn()

        assertFalse(repo.checkOut(session.id)!!.autoClosed)
        assertFalse(repo.checkOutAt(session.id, 9000L)!!.autoClosed)
    }
}
