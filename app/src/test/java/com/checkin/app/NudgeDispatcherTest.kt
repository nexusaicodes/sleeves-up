package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeCatalog
import com.checkin.app.notify.nudge.NudgeConfig
import com.checkin.app.notify.nudge.NudgeDispatcher
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.NudgeSending
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The dispatcher's failure mode is silent: a send recorded for a notification that never reached
 * the tray spends one of the day's two slots, so the checkpoint that would have been shown is
 * suppressed by a message nobody saw. These pin that the log only records what the platform took.
 *
 * Copy resolution sits behind [StringResolver], which is what keeps the dispatcher reachable from a
 * JVM-only suite — a `Context` for `getString` would put it out of reach.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NudgeDispatcherTest {

    // The user's chosen hour, local, on a day they haven't checked in. Deliberately not the default,
    // so a dispatcher that ignored the stored hour would fail here rather than pass by coincidence.
    private val chosenHour = 8
    private val reminderSettings = FakeReminderSettings(chosenHour)
    private val today = LocalDate.of(2026, 6, 15)
    private val triggerHour = today.atTime(chosenHour, 0)
        .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private val time = FakeTimeSource(triggerHour, today)
    private val notifier = FakeNotifier()
    private val log = FakeNudgeSendLog()
    private val dao = FakeCheckInSessionDao()
    private val alarms = FakeNudgeAlarms()

    private fun dispatcher(clock: FakeTimeSource = time): NudgeDispatcher = NudgeDispatcher(
        strings = StringResolver { "copy-$it" },
        repository = CheckInRepository(dao, clock),
        notifier = notifier,
        sendLog = { log },
        timeSource = clock,
        reminderSettings = reminderSettings,
        alarms = alarms,
    )

    private suspend fun shownCount() = log.attemptsSince(0L).count { it.posted }

    @Test
    fun `an eligible nudge is posted and logged`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, sent)
        assertEquals(1, notifier.shown.size)
        // The log is the only record of a send, and the daily cap counts from it.
        assertEquals(1, shownCount())
    }

    /**
     * POST_NOTIFICATIONS is revocable at any time, and the daily cap counts from the log — so a
     * refused post recorded as shown would burn one of the day's slots on a notification nobody
     * saw, and the later checkpoint that slot belonged to would never fire. It is still recorded,
     * flagged, so the checkpoint is spent: see the rearm test below for why.
     */
    @Test
    fun `a refused post is recorded as unposted`() = runTest {
        notifier.refuse = true

        val sent = dispatcher().runOnce()

        assertNull(sent)
        assertEquals(0, shownCount())
        assertEquals(NudgeSending(Nudge.NOT_CHECKED_IN_FIRST.name, triggerHour, posted = false), log.sends.single())
    }

    /**
     * Ineligibility is decided by the rules alone — there is no opt-out pref to switch off, since a
     * notification's opt-out is its channel and a blocked one is refused at [FakeNotifier] instead.
     * An hour before the first checkpoint is the cheapest genuine ineligibility. The load-bearing
     * assertion is the log one: nothing eligible must record nothing, or the daily cap counts a send
     * that never happened and burns one of the day's two slots.
     */
    @Test
    fun `nothing is posted when no nudge is eligible`() = runTest {
        val beforeFirstCheckpoint = today.atTime(chosenHour - 1, 0)
            .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

        val sent = dispatcher(FakeTimeSource(beforeFirstCheckpoint, today)).runOnce()

        assertNull(sent)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(0, shownCount())
    }

    /**
     * The checkpoints carry distinct ids, so without this the follow-up would stack under the first
     * one still in the tray — two notifications saying the user hasn't checked in, which reads as a
     * stuck loop rather than as one message that came back. Ids an earlier build posted under are
     * retired too, or a stale evening nudge would sit beside today's.
     */
    @Test
    fun `a dispatched nudge retires the day's other checkpoints`() = runTest {
        val sent = dispatcher().runOnce()

        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, sent)
        val expected = Nudge.entries.filter { it != Nudge.NOT_CHECKED_IN_FIRST }.map { it.notificationId } +
            NotificationIds.RETIRED_NUDGES
        assertEquals(expected.toSet(), notifier.cancelled.toSet())
        assertTrue(Nudge.NOT_CHECKED_IN_FIRST.notificationId !in notifier.cancelled)
    }

    /**
     * The regression this whole rule exists for. A checkpoint band is hours wide and the hourly
     * worker asks inside it, so a pass an hour later would repost the first nudge under the same id
     * with the same copy — re-alerting on a high-importance channel — and spend the second of the
     * day's two slots, leaving the follow-up unreachable.
     */
    @Test
    fun `a second pass inside the same band sends nothing`() = runTest {
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, dispatcher().runOnce())

        val laterInTheBand = today.atTime(NudgeSchedule.followUpHour(chosenHour) - 1, 0)
            .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

        assertNull(dispatcher(FakeTimeSource(laterInTheBand, today)).runOnce())
        assertEquals(1, shownCount())
        assertEquals(1, notifier.shown.size)
    }

    /**
     * A session still open from an earlier day is not evidence the user is present — it is one whose
     * day-boundary close was lost to a force stop or a package replace. Suppressing on it left such a
     * session silencing every nudge indefinitely, on exactly the device that had stopped being
     * opened, and the nudge it silenced was the one that would have surfaced the problem.
     */
    @Test
    fun `an open session from an earlier day does not suppress`() = runTest {
        dao.seedOpen(dateKey = today.minusDays(1).toString(), startedAt = triggerHour - 24 * 60 * 60 * 1000L)

        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, dispatcher().runOnce())
    }

    /** The mirror image: a session opened today is a check-in, and the day's nudges are done. */
    @Test
    fun `a session opened today suppresses`() = runTest {
        dao.seedOpen(dateKey = today.toString(), startedAt = triggerHour)

        assertNull(dispatcher().runOnce())
        assertEquals(0, shownCount())
    }

    /** The spec is what reaches the tray; a wrong id or channel is invisible until a device shows it. */
    @Test
    fun `the posted spec carries the nudge's own id and channel`() = runTest {
        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST.notificationId, spec.id)
        assertEquals(NotificationChannels.NUDGE, spec.channelId)
    }

    /**
     * The catalog is the only source of a nudge's wording — there is no variant bucket and no
     * override — so a dispatcher that resolved copy any other way would show a string no test reads.
     */
    @Test
    fun `the posted copy is the nudge's registered copy`() = runTest {
        val copy = NudgeCatalog.copyFor(Nudge.NOT_CHECKED_IN_FIRST)

        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals("copy-${copy.titleRes}", spec.title)
        assertEquals("copy-${copy.bodyRes}", spec.body)
    }

    /**
     * Retiring is what stops a stale nudge sending the user through the gate to an open session —
     * including one posted by an earlier build under an id nothing posts under now.
     */
    @Test
    fun `retireAll cancels every nudge id, retired ones included`() = runTest {
        dispatcher().retireAll()

        assertEquals(
            (Nudge.entries.map { it.notificationId } + NotificationIds.RETIRED_NUDGES).toSet(),
            notifier.cancelled.toSet(),
        )
    }

    /** The hour is read at every pass, so a change takes effect without rebuilding anything. */
    @Test
    fun `the snapshot follows the stored hour`() = runTest {
        reminderSettings.setFirstReminderHour(chosenHour + 1)

        assertNull(dispatcher().runOnce())
    }

    /**
     * On the day of an update, a send recorded under a name an earlier build used must still mark
     * its successor as sent — otherwise the reminder goes out twice and the cap then refuses the
     * follow-up.
     */
    @Test
    fun `a send recorded under a legacy name still counts as that checkpoint`() = runTest {
        log.sends += NudgeSending("NOT_CHECKED_IN_MORNING", triggerHour - 2 * 60 * 60 * 1000L, posted = true)

        assertNull(dispatcher().runOnce())
    }

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today): Long =
        date.atTime(hour, minute).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private val followUpAt get() = at(NudgeSchedule.followUpHour(chosenHour))

    /** A pass arms from the ledger as it stands after the pass, its own send counted: the next band. */
    @Test
    fun `a pass arms the next band`() = runTest {
        dispatcher().runOnce()

        assertEquals(listOf(followUpAt), alarms.armed)
    }

    /**
     * A refused nudge was due now, so arming for it again would fire, be refused again, and repeat.
     * It is in the ledger as spent and waits for the next band.
     */
    @Test
    fun `a refused post arms the next band rather than now`() = runTest {
        notifier.refuse = true

        dispatcher().runOnce()

        assertEquals(listOf(followUpAt), alarms.armed)
    }

    /**
     * The update-day case: the first reminder went out, the package was replaced after the
     * follow-up hour, and the alarm went with it. The next checkpoint after now is tomorrow's first,
     * so a plain arm would drop today's follow-up. It is due, but a rearm is usually the user opening
     * the app, so it is put off rather than posted over the screen they opened.
     */
    @Test
    fun `rearm puts off an open band that is unsent`() = runTest {
        log.record(Nudge.NOT_CHECKED_IN_FIRST, triggerHour, posted = true)
        val now = followUpAt + 30 * 60 * 1000L

        dispatcher(FakeTimeSource(now, today)).rearm()

        assertEquals(listOf(now + NudgeConfig().dueNowDelayMs), alarms.armed)
    }

    /**
     * A refusal recorded by an earlier process is still a refusal: with only posts recorded, every
     * process start read the band as unsent and woke the device for a pass into a blocked channel.
     */
    @Test
    fun `rearm does not retry a checkpoint whose post was refused`() = runTest {
        log.record(Nudge.NOT_CHECKED_IN_FIRST, triggerHour, posted = false)

        dispatcher(FakeTimeSource(triggerHour + 10 * 60 * 1000L, today)).rearm()

        assertEquals(listOf(followUpAt), alarms.armed)
    }

    /** A refusal spends its checkpoint but not the gap, so the follow-up arrives on time. */
    @Test
    fun `a refused attempt does not hold back the next checkpoint`() = runTest {
        log.record(Nudge.NOT_CHECKED_IN_FIRST, followUpAt - 10 * 60 * 1000L, posted = false)

        assertEquals(Nudge.NOT_CHECKED_IN_FOLLOW_UP, dispatcher(FakeTimeSource(followUpAt, today)).runOnce())
    }

    /**
     * A pass that throws before recording leaves its nudge due. Arming for now would refire into the
     * same failure at the alarm's minimum futurity for the rest of the band; it is put off instead.
     */
    @Test
    fun `a pass that throws arms later rather than now`() = runTest {
        val throwing = object : com.checkin.app.notify.nudge.NudgeSendLog by log {
            override suspend fun record(nudge: Nudge, atMillis: Long, posted: Boolean) = error("disk full")
        }
        val dispatcher = NudgeDispatcher(
            strings = StringResolver { "copy-$it" },
            repository = CheckInRepository(dao, time),
            notifier = notifier,
            sendLog = { throwing },
            timeSource = time,
            reminderSettings = reminderSettings,
            alarms = alarms,
        )

        assertNull(dispatcher.runOnce())
        assertEquals(listOf(triggerHour + NudgeConfig().dueNowDelayMs), alarms.armed)
    }

    /**
     * The day comes from the same clock read as the hour. Read separately, a pass in the last
     * millisecond of the day could take the date from after midnight: tomorrow's empty ledger, with
     * today's follow-up band — and the follow-up already sent would be sent again.
     */
    @Test
    fun `a pass straddling midnight reads one day`() = runTest {
        log.record(Nudge.NOT_CHECKED_IN_FOLLOW_UP, followUpAt, posted = true)
        val lastMillisecond = at(0, date = today.plusDays(1)) - 1
        val straddling = FakeTimeSource(lastMillisecond, today.plusDays(1))

        assertNull(dispatcher(straddling).runOnce())
    }

    /** The alarm is armed for the moment the gap clears, not left for tomorrow's first checkpoint. */
    @Test
    fun `rearm retries a follow-up the gap is holding back`() = runTest {
        val firstLandedLate = followUpAt - 30 * 60 * 1000L
        log.record(Nudge.NOT_CHECKED_IN_FIRST, firstLandedLate, posted = true)

        dispatcher(FakeTimeSource(followUpAt, today)).rearm()

        assertEquals(listOf(firstLandedLate + NudgeConfig().minGapMs), alarms.armed)
    }

    /** A check-in ends the day's nudges, so the alarm goes to tomorrow rather than waking for nothing. */
    @Test
    fun `rearm after a check-in arms tomorrow's first checkpoint`() = runTest {
        dao.seedOpen(dateKey = today.toString(), startedAt = triggerHour)

        dispatcher().rearm()

        assertEquals(listOf(at(chosenHour, date = today.plusDays(1))), alarms.armed)
    }

    /** Arming reads the stored hour under the lock, so a new hour is armed for, not the old one. */
    @Test
    fun `rearm arms for the hour stored before it`() = runTest {
        val beforeEither = at(chosenHour - 2)
        reminderSettings.setFirstReminderHour(chosenHour + 3)

        dispatcher(FakeTimeSource(beforeEither, today)).rearm()

        assertEquals(listOf(at(chosenHour + 3)), alarms.armed)
    }

    /**
     * A ledger that cannot be read must not end the chain: the plain next checkpoint is armed, which
     * knows nothing of the day's sends but keeps the alarm alive.
     */
    @Test
    fun `an unreadable ledger still arms the next checkpoint`() = runTest {
        val broken = NudgeDispatcher(
            strings = StringResolver { "copy-$it" },
            repository = CheckInRepository(dao, time),
            notifier = notifier,
            sendLog = { error("database will not open") },
            timeSource = time,
            reminderSettings = reminderSettings,
            alarms = alarms,
        )

        assertNull(broken.runOnce())
        broken.rearm()

        assertEquals(listOf(followUpAt, followUpAt), alarms.armed)
    }

    /** A refused arm is absorbed, so it cannot take the pass — or its caller — down with it. */
    @Test
    fun `a throwing arm does not fail the pass`() = runTest {
        alarms.throwOnArm = true

        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, dispatcher().runOnce())
    }
}
