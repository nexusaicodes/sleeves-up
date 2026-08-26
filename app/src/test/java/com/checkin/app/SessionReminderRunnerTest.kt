package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.service.SessionReminderRunner
import com.checkin.app.service.SessionSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The reminder and the day-boundary close both run from an alarm, in whatever process the broadcast
 * lands in, with no UI and no service behind them. Every failure down there is silent — which is why
 * the behaviour is pinned here rather than left to be discovered as a wrong number.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionReminderRunnerTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)
    private val now = LocalDateTime.of(2026, 6, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()

    /** The midnight that ends the session's day — what a boundary close must always stamp. */
    private val boundary = LocalDateTime.of(2026, 6, 16, 0, 0).atZone(zone).toInstant().toEpochMilli()

    private val time = FakeTimeSource(now, today)
    private val dao = FakeCheckInSessionDao()
    private val repository = CheckInRepository(dao, time)
    private val notifier = FakeNotifier()
    private val alarms = FakeSessionAlarms()
    private val log = FakeEngagementLog()

    private fun runner() = SessionReminderRunner(
        repository = repository,
        notifier = notifier,
        strings = StringResolver { "copy-$it" },
        alarms = alarms,
        log = log,
        timeSource = time,
        zone = { zone },
    )

    // --- Arming ---

    @Test
    fun `arming schedules both the reminder and the session's own day boundary`() = runTest {
        repository.checkIn()

        runner().arm(now)

        assertEquals(SessionSchedule.nextReminderAt(now), alarms.lastReminder)
        assertEquals(boundary, alarms.lastDayBoundary)
    }

    /** Nothing open means nothing to remind about; arming must not leave a wake-up standing. */
    @Test
    fun `arming with no open session schedules nothing`() = runTest {
        runner().arm(now)

        assertNull(alarms.lastReminder)
        assertNull(alarms.lastDayBoundary)
    }

    /**
     * The boundary comes from the session's `date_key`, not from the anchor. A session revived after
     * a reboot on a later day must still close where its own day ended, not a day late.
     */
    @Test
    fun `a revive on a later day still targets the session's original boundary`() = runTest {
        repository.checkIn()
        val nextDay = LocalDateTime.of(2026, 6, 16, 8, 0).atZone(zone).toInstant().toEpochMilli()

        runner().arm(nextDay)

        assertEquals(boundary, alarms.lastDayBoundary)
        // Already past — the platform delivers a past-due alarm immediately, which is what closes a
        // session that outlived its boundary while the process was dead.
        assertTrue(alarms.lastDayBoundary!! < nextDay)
    }

    // --- Ensuring, after something cleared the alarms ---

    /**
     * A force stop and a package replace both cancel a package's alarms while leaving the row open.
     * Re-arming has to reinstate what was standing, not derive it again: re-deriving would push the
     * reminder a full interval past every repair, so a user who opens the app more often than the
     * cadence would never receive one.
     */
    @Test
    fun `ensuring reinstates the instants that were already armed`() = runTest {
        repository.checkIn()
        val armedReminder = now + 30 * 60 * 1000L
        alarms.seedArmed(reminderAt = armedReminder, boundaryAt = boundary)

        assertTrue(runner().ensureArmed(now))

        assertEquals(armedReminder, alarms.lastReminder)
        assertEquals(boundary, alarms.lastDayBoundary)
    }

    /** A reminder instant already missed is re-derived, or a device coming back buzzes immediately. */
    @Test
    fun `ensuring re-derives a reminder whose instant has passed`() = runTest {
        repository.checkIn()
        alarms.seedArmed(reminderAt = now - 1, boundaryAt = boundary)

        runner().ensureArmed(now)

        assertEquals(SessionSchedule.nextReminderAt(now), alarms.lastReminder)
    }

    /**
     * The opposite rule for the boundary, and deliberately so: the platform delivers a past-due alarm
     * immediately, which is exactly what closes a session that outlived its boundary while nothing
     * was armed to notice.
     */
    @Test
    fun `ensuring re-arms a boundary whose instant has passed rather than moving it`() = runTest {
        repository.checkIn()
        alarms.seedArmed(reminderAt = 0L, boundaryAt = boundary)
        val nextDay = LocalDateTime.of(2026, 6, 16, 8, 0).atZone(zone).toInstant().toEpochMilli()

        runner().ensureArmed(nextDay)

        assertEquals(boundary, alarms.lastDayBoundary)
    }

    /** The alert ladder belongs to the session, not to whichever process noticed the alarms were gone. */
    @Test
    fun `ensuring leaves the reminder count alone`() = runTest {
        repository.checkIn()
        alarms.remindersSent = 2

        runner().ensureArmed(now)

        assertEquals(2, alarms.remindersSent)
    }

    /** Nothing stored at all — an upgrade over a session that was already open. */
    @Test
    fun `ensuring derives both instants when nothing was stored`() = runTest {
        repository.checkIn()

        runner().ensureArmed(now)

        assertEquals(SessionSchedule.nextReminderAt(now), alarms.lastReminder)
        assertEquals(boundary, alarms.lastDayBoundary)
    }

    @Test
    fun `ensuring with no open session drops the alarms and reports false`() = runTest {
        alarms.seedArmed(reminderAt = now + 1, boundaryAt = boundary)

        assertFalse(runner().ensureArmed(now))

        assertTrue(alarms.cancelCount > 0)
        assertEquals(0L, alarms.dayBoundaryAt)
    }

    // --- The reminder ---

    @Test
    fun `a fired reminder posts and arms the next one`() = runTest {
        repository.checkIn()

        val outcome = runner().onReminderFired()

        assertTrue(outcome is SessionReminderRunner.Outcome.Reminded)
        assertEquals(NotificationIds.SESSION_REMINDER, notifier.shown.single().id)
        assertEquals(SessionSchedule.nextReminderAt(now), alarms.lastReminder)
        assertTrue(log.events.value.any { it.event == EngagementEventType.SHOWN.name })
    }

    /** The first alerts; the rest accumulate on the shade rather than buzzing every two hours. */
    @Test
    fun `only the first reminder of a session alerts`() = runTest {
        repository.checkIn()
        val runner = runner()

        runner.onReminderFired()
        runner.onReminderFired()
        runner.onReminderFired()

        assertFalse("the first reminder must alert", notifier.shown[0].silent)
        assertTrue(notifier.shown[1].silent)
        assertTrue(notifier.shown[2].silent)
    }

    /** A stale alarm over a session that already closed drops itself instead of firing again. */
    @Test
    fun `a reminder with no open session cancels`() = runTest {
        val outcome = runner().onReminderFired()

        assertEquals(SessionReminderRunner.Outcome.NoSession, outcome)
        assertTrue(notifier.shown.isEmpty())
        assertTrue(alarms.cancelCount > 0)
    }

    /**
     * A refused post is not an ignored reminder. The count must not advance, or restoring
     * notifications would resume mid-ladder and the first reminder the user can actually see would
     * arrive silently.
     */
    @Test
    fun `a refused post re-arms without consuming the alert`() = runTest {
        repository.checkIn()
        notifier.refuse = true
        val runner = runner()

        val outcome = runner.onReminderFired()

        assertEquals(SessionReminderRunner.Outcome.Refused, outcome)
        assertEquals(0, alarms.remindersSent)
        assertNotNull("a refusal must still leave a reminder armed", alarms.lastReminder)

        notifier.refuse = false
        runner.onReminderFired()
        assertFalse("the first visible reminder still alerts", notifier.shown.single().silent)
    }

    // --- The day boundary ---

    @Test
    fun `the boundary closes the session stamped at midnight, not at the fire time`() = runTest {
        val session = repository.checkIn()
        // The alarm lands late — well into the following day.
        val late = FakeTimeSource(boundary + 3 * 60 * 60 * 1000L, today.plusDays(1))
        val runner = SessionReminderRunner(
            repository = CheckInRepository(dao, late),
            notifier = notifier,
            strings = StringResolver { "copy-$it" },
            alarms = alarms,
            log = log,
            timeSource = late,
            zone = { zone },
        )

        val outcome = runner.onDayBoundaryFired()

        assertEquals(SessionReminderRunner.Outcome.Closed(boundary), outcome)
        val closed = dao.getSessionById(session.id)!!
        assertEquals(boundary, closed.stoppedAt)
        assertEquals(boundary - closed.startedAt, closed.duration)
        // The one writer that flags a close as the boundary's rather than the user's. The CSV is the
        // only thing that reads it; nothing about the row's duration or immutability changes.
        assertTrue(closed.autoClosed)
    }

    @Test
    fun `the boundary drops both alarms and the reminder notification`() = runTest {
        repository.checkIn()

        runner().onDayBoundaryFired()

        assertTrue(alarms.cancelCount > 0)
        assertTrue(NotificationIds.SESSION_REMINDER in notifier.cancelled)
    }

    /**
     * The close instant comes from what was armed, not from a fresh calculation, because a fresh one
     * reads the device's *current* zone while the alarm was set from the zone at check-in — the zone
     * the session's own `date_key` names.
     */
    @Test
    fun `the boundary closes at the instant it was armed for, not the current zone's midnight`() = runTest {
        val session = repository.checkIn()
        alarms.seedArmed(reminderAt = 0L, boundaryAt = boundary)
        // The device has moved five hours east: recomputing here would land well before the armed
        // instant and silently delete hours the user worked.
        val moved = SessionReminderRunner(
            repository = repository,
            notifier = notifier,
            strings = StringResolver { "copy-$it" },
            alarms = alarms,
            log = log,
            timeSource = FakeTimeSource(boundary + 60_000L, today.plusDays(1)),
            zone = { ZoneId.of("Asia/Karachi") },
        )

        moved.onDayBoundaryFired()

        assertEquals(boundary, dao.getSessionById(session.id)!!.stoppedAt)
    }

    /** Travelling the other way puts the recomputed midnight in the future; a stop instant never is. */
    @Test
    fun `the boundary never stamps a stop in the future`() = runTest {
        val session = repository.checkIn()
        val fireAt = boundary - 4 * 60 * 60 * 1000L
        alarms.seedArmed(reminderAt = 0L, boundaryAt = boundary)
        val early = SessionReminderRunner(
            repository = repository,
            notifier = notifier,
            strings = StringResolver { "copy-$it" },
            alarms = alarms,
            log = log,
            timeSource = FakeTimeSource(fireAt, today),
            zone = { zone },
        )

        early.onDayBoundaryFired()

        assertEquals(fireAt, dao.getSessionById(session.id)!!.stoppedAt)
    }

    /** A boundary alarm that outlived its session must not close whatever opened after it. */
    @Test
    fun `a boundary with no open session closes nothing`() = runTest {
        val outcome = runner().onDayBoundaryFired()

        assertEquals(SessionReminderRunner.Outcome.NoSession, outcome)
        assertTrue(dao.sessions.isEmpty())
    }
}
