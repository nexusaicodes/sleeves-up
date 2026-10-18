package com.checkin.app

import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitEdit
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeCatalog
import com.checkin.app.notify.nudge.NudgeConfig
import com.checkin.app.notify.nudge.NudgeDispatcher
import com.checkin.app.notify.nudge.NudgeSendLog
import com.checkin.app.notify.nudge.NudgeSending
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The dispatcher's failure mode is silent: a send recorded for a notification that never reached
 * the tray spaces every other habit's reminder off a message nobody saw, and one sent for the wrong
 * habit reminds the user about a habit they already kept. These pin that the log records exactly
 * what the platform took, for exactly the habit it was about.
 *
 * Copy resolution sits behind [StringResolver], which is what keeps the dispatcher reachable from a
 * JVM-only suite — a `Context` for `getString` would put it out of reach.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NudgeDispatcherTest {

    // The seed habit's chosen hour, local, on a day it has no session. Deliberately not the default,
    // so a dispatcher that ignored the stored hour would fail here rather than pass by coincidence.
    private val chosenHour = 8
    private val reminderSettings = FakeReminderSettings(FakeHabitDao.SEED_UUID to chosenHour * 60)
    private val today = LocalDate.of(2026, 6, 15)
    private val triggerHour = at(chosenHour)

    private val time = FakeTimeSource(triggerHour, today)
    private val notifier = FakeNotifier()
    private val log = FakeNudgeSendLog()
    private val dao = FakeCheckInSessionDao()
    private val habitDao = FakeHabitDao()
    private val habits = HabitRepository(habitDao, FakeAppStateDao(), dao, time) { "uuid-${habitDao.habits.size}" }
    private val alarms = FakeNudgeAlarms()
    private val channels = FakeHabitChannels()

    private val seed: Habit get() = habitDao.habits.first { it.id == Habit.SEED_ID }

    private fun dispatcher(
        clock: FakeTimeSource = time,
        sendLog: () -> NudgeSendLog = { log },
        strings: StringResolver = StringResolver { "copy-$it" },
    ): NudgeDispatcher = NudgeDispatcher(
        strings = strings,
        repository = CheckInRepository(dao, clock),
        habits = habits,
        notifier = notifier,
        sendLog = sendLog,
        timeSource = clock,
        reminderSettings = reminderSettings,
        alarms = alarms,
        channels = channels,
    )

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today): Long =
        date.atTime(hour, minute).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()

    private suspend fun shownCount() = log.attemptsSince(0L).count { it.posted }

    private suspend fun newHabit(name: String, hour: Int): Habit {
        val habit = (habits.create(name) as HabitEdit.Saved).habit
        reminderSettings.setReminderMinute(habit.uuid, hour * 60)
        return habit
    }

    @Test
    fun `an eligible nudge is posted and logged against its habit`() = runTest {
        val reminded = dispatcher().runOnce()

        assertEquals(listOf(Habit.SEED_ID), reminded.map { it.id })
        assertEquals(1, notifier.shown.size)
        assertEquals(NudgeSending(seed.uuid, Nudge.NOT_CHECKED_IN.name, triggerHour, posted = true), log.sends.single())
    }

    /**
     * POST_NOTIFICATIONS is revocable at any time, and the gap counts from the log — so a refused
     * post recorded as shown would hold back every other habit's reminder behind a notification
     * nobody saw. It is still recorded, flagged, so the habit's day is spent: see the rearm test below.
     */
    @Test
    fun `a refused post is recorded as unposted`() = runTest {
        notifier.refuse = true

        val reminded = dispatcher().runOnce()

        assertTrue(reminded.isEmpty())
        assertEquals(0, shownCount())
        assertEquals(
            NudgeSending(seed.uuid, Nudge.NOT_CHECKED_IN.name, triggerHour, posted = false),
            log.sends.single(),
        )
    }

    /**
     * Ineligibility is decided by the rules alone — there is no opt-out pref, since a habit's opt-out
     * is its channel and a blocked one is refused at [FakeNotifier] instead. The load-bearing
     * assertion is the log one: nothing eligible must record nothing, or the habit's day is spent on
     * a send that never happened.
     */
    @Test
    fun `nothing is posted when no nudge is eligible`() = runTest {
        val reminded = dispatcher(FakeTimeSource(at(chosenHour - 1), today)).runOnce()

        assertTrue(reminded.isEmpty())
        assertTrue(notifier.shown.isEmpty())
        assertTrue(log.sends.isEmpty())
    }

    /** A notification an earlier build posted is about the record the seed habit now holds. */
    @Test
    fun `a dispatched nudge retires the ids earlier builds posted under`() = runTest {
        dispatcher().runOnce()

        assertEquals(NotificationIds.RETIRED_NUDGES.toSet(), notifier.cancelled.toSet())
    }

    /**
     * The band is hours wide and the hourly worker asks inside it, so a later pass would repost the
     * nudge under the same id with the same copy, re-alerting on a high-importance channel.
     */
    @Test
    fun `a second pass the same day sends nothing`() = runTest {
        dispatcher().runOnce()

        assertTrue(dispatcher(FakeTimeSource(at(chosenHour + 5), today)).runOnce().isEmpty())
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

        assertEquals(1, dispatcher().runOnce().size)
    }

    /** The mirror image: a session opened today is a check-in, and that habit's nudge is done. */
    @Test
    fun `a session opened today suppresses`() = runTest {
        dao.seedOpen(dateKey = today.toString(), startedAt = triggerHour)

        assertTrue(dispatcher().runOnce().isEmpty())
        assertEquals(0, shownCount())
    }

    /** Checking in to one habit says nothing about another, so it does not quieten its reminder. */
    @Test
    fun `a session for one habit does not suppress another's nudge`() = runTest {
        val gym = newHabit("Gym", chosenHour)
        dao.seedCompleted(
            today.toString(),
            startedAt = triggerHour - 60_000L,
            durationMs = 30_000L,
            habitId = Habit.SEED_ID,
        )

        assertEquals(listOf(gym.id), dispatcher().runOnce().map { it.id })
    }

    /** The spec is what reaches the tray; a wrong id, channel or habit is invisible until a device shows it. */
    @Test
    fun `the posted spec carries the habit's own id, channel and tap target`() = runTest {
        dispatcher().runOnce()

        val spec = notifier.shown.single()
        assertEquals(Nudge.NOT_CHECKED_IN.notificationIdFor(Habit.SEED_ID), spec.id)
        assertEquals(seed.channelId, spec.channelId)
        assertEquals(LaunchExtras.CHECK_IN, spec.launchExtra)
        assertEquals(seed.uuid, spec.launchHabitUuid)
    }

    /** A post to a channel not yet created is discarded, and recorded as a refusal; it is ensured first. */
    @Test
    fun `the habit's channel is ensured before posting to it`() = runTest {
        dispatcher().runOnce()

        assertEquals(listOf(listOf(seed)), channels.synced)
    }

    /**
     * The catalog is the only source of a nudge's wording — there is no variant bucket and no
     * override — and the title names the habit, so a user with two can tell which one it is about.
     */
    @Test
    fun `the posted copy is the nudge's registered copy, naming the habit`() = runTest {
        val copy = NudgeCatalog.copyFor(Nudge.NOT_CHECKED_IN)
        val strings = StringResolver { if (it == copy.titleRes) "%1\$s: title" else "copy-$it" }

        dispatcher(strings = strings).runOnce()

        val spec = notifier.shown.single()
        assertEquals("Seed: title", spec.title)
        assertEquals("copy-${copy.bodyRes}", spec.body)
    }

    /** A habit still owed a name — an updated install not yet opened — is reminded as before the update. */
    @Test
    fun `an unnamed habit gets the unnamed title`() = runTest {
        val unnamed = FakeHabitDao(seedName = null)
        val dispatcher = NudgeDispatcher(
            strings = StringResolver { "copy-$it" },
            repository = CheckInRepository(dao, time),
            habits = HabitRepository(unnamed, FakeAppStateDao(), dao, time),
            notifier = notifier,
            sendLog = { log },
            timeSource = time,
            reminderSettings = reminderSettings,
            alarms = alarms,
            channels = channels,
        )

        dispatcher.runOnce()

        assertEquals(
            "copy-${NudgeCatalog.copyFor(Nudge.NOT_CHECKED_IN).unnamedTitleRes}",
            notifier.shown.single().title,
        )
    }

    /**
     * Retiring is what stops a stale nudge sending the user through the gate to an open session —
     * including one posted by an earlier build under an id nothing posts under now. Only that
     * habit's: another habit's reminder is still true.
     */
    @Test
    fun `retire cancels the habit's nudge ids and the retired ones`() = runTest {
        dispatcher().retire(2L)

        assertEquals(
            (Nudge.entries.map { it.notificationIdFor(2L) } + NotificationIds.RETIRED_NUDGES).toSet(),
            notifier.cancelled.toSet(),
        )
        assertTrue(Nudge.NOT_CHECKED_IN.notificationIdFor(Habit.SEED_ID) !in notifier.cancelled)
    }

    /** The hour is read at every pass, so a change takes effect without rebuilding anything. */
    @Test
    fun `the snapshot follows the stored hour`() = runTest {
        reminderSettings.setReminderMinute(seed.uuid, (chosenHour + 1) * 60)

        assertTrue(dispatcher().runOnce().isEmpty())
    }

    /**
     * On the day of an update, a send v3.0 recorded — under a key it used, with no habit — was about
     * the record the seed habit now holds, so it must mark that habit's day spent.
     */
    @Test
    fun `a pre-habits send counts as the seed habit's`() = runTest {
        log.sends += NudgeSending("", "NOT_CHECKED_IN_MORNING", triggerHour - 2 * 60 * 60 * 1000L, posted = true)

        assertTrue(dispatcher(FakeTimeSource(at(chosenHour + 3), today)).runOnce().isEmpty())
    }

    /** It is the seed habit's alone: a second habit was not reminded by it. */
    @Test
    fun `a pre-habits send does not spend another habit's day`() = runTest {
        val gym = newHabit("Gym", chosenHour)
        log.sends += NudgeSending("", "NOT_CHECKED_IN_MORNING", triggerHour - 2 * 60 * 60 * 1000L, posted = true)

        assertEquals(listOf(gym.id), dispatcher(FakeTimeSource(at(chosenHour + 3), today)).runOnce().map { it.id })
    }

    /** Archived habits send no nudges: the dispatcher reads active habits and nothing else. */
    @Test
    fun `an archived habit is never reminded`() = runTest {
        val gym = newHabit("Gym", chosenHour)
        habits.archive(gym.id)
        dao.seedOpen(today.toString(), startedAt = triggerHour, habitId = Habit.SEED_ID)

        assertTrue(dispatcher().runOnce().isEmpty())
        assertTrue(log.sends.isEmpty())
    }

    /**
     * Two habits at one hour arrive ninety minutes apart, not as a burst: the first post of a pass
     * moves the gap for every habit after it, and the alarm is armed for when it clears.
     */
    @Test
    fun `two habits due together are spaced by the gap`() = runTest {
        newHabit("Gym", chosenHour)

        val reminded = dispatcher().runOnce()

        assertEquals(listOf(Habit.SEED_ID), reminded.map { it.id })
        assertEquals(listOf(triggerHour + NudgeConfig().minGapMs), alarms.armed)
    }

    @Test
    fun `the held-back habit is reminded once the gap clears`() = runTest {
        val gym = newHabit("Gym", chosenHour)
        dispatcher().runOnce()

        val later = dispatcher(FakeTimeSource(triggerHour + NudgeConfig().minGapMs, today)).runOnce()

        assertEquals(listOf(gym.id), later.map { it.id })
    }

    /** A refusal spends its habit's day but not the gap, so the next habit's arrives on time. */
    @Test
    fun `a refused attempt does not hold back another habit`() = runTest {
        val gym = newHabit("Gym", chosenHour)
        log.record(seed.uuid, Nudge.NOT_CHECKED_IN, triggerHour - 10 * 60 * 1000L, posted = false)

        assertEquals(listOf(gym.id), dispatcher().runOnce().map { it.id })
    }

    /** A pass arms from the ledger as it stands after the pass, its own send counted: tomorrow's band. */
    @Test
    fun `a pass arms tomorrow once every habit is done`() = runTest {
        dispatcher().runOnce()

        assertEquals(listOf(at(chosenHour, date = today.plusDays(1))), alarms.armed)
    }

    /** The shared alarm is armed for whichever habit is due soonest. */
    @Test
    fun `the alarm is armed for the soonest habit`() = runTest {
        newHabit("Gym", chosenHour + 4)

        dispatcher().runOnce()

        assertEquals(listOf(at(chosenHour + 4)), alarms.armed)
    }

    /**
     * A refused nudge was due now, so arming for it again would fire, be refused again, and repeat.
     * It is in the ledger as spent and waits for tomorrow.
     */
    @Test
    fun `a refused post arms tomorrow rather than now`() = runTest {
        notifier.refuse = true

        dispatcher().runOnce()

        assertEquals(listOf(at(chosenHour, date = today.plusDays(1))), alarms.armed)
    }

    /**
     * The update-day case: the package was replaced inside the band, before the nudge went out, and
     * the alarm went with it. It is due, but a rearm is usually the user opening the app, so it is
     * put off rather than posted over the screen they opened.
     */
    @Test
    fun `rearm puts off an open band that is unsent`() = runTest {
        val now = at(chosenHour + 3, 30)

        dispatcher(FakeTimeSource(now, today)).rearm()

        assertEquals(listOf(now + NudgeConfig().dueNowDelayMs), alarms.armed)
    }

    /**
     * A refusal recorded by an earlier process is still a refusal: with only posts recorded, every
     * process start read the band as unsent and woke the device for a pass into a blocked channel.
     */
    @Test
    fun `rearm does not retry a nudge whose post was refused`() = runTest {
        log.record(seed.uuid, Nudge.NOT_CHECKED_IN, triggerHour, posted = false)

        dispatcher(FakeTimeSource(triggerHour + 10 * 60 * 1000L, today)).rearm()

        assertEquals(listOf(at(chosenHour, date = today.plusDays(1))), alarms.armed)
    }

    /**
     * A pass that throws before recording leaves its nudge due. Arming for now would refire into the
     * same failure at the alarm's minimum futurity for the rest of the band; it is put off instead.
     */
    @Test
    fun `a pass that throws arms later rather than now`() = runTest {
        val throwing = object : NudgeSendLog by log {
            override suspend fun record(habitUuid: String, nudge: Nudge, atMillis: Long, posted: Boolean) =
                error("disk full")
        }

        assertTrue(dispatcher(sendLog = { throwing }).runOnce().isEmpty())
        assertEquals(listOf(triggerHour + NudgeConfig().dueNowDelayMs), alarms.armed)
    }

    /**
     * The day comes from the same clock read as the hour. Read separately, a pass in the last
     * millisecond of the day could take the date from after midnight: tomorrow's empty ledger, with
     * today's band still open — and the nudge already sent would be sent again.
     */
    @Test
    fun `a pass straddling midnight reads one day`() = runTest {
        log.record(seed.uuid, Nudge.NOT_CHECKED_IN, at(20), posted = true)
        val lastMillisecond = at(0, date = today.plusDays(1)) - 1

        assertTrue(dispatcher(FakeTimeSource(lastMillisecond, today.plusDays(1))).runOnce().isEmpty())
    }

    /** A check-in ends the habit's day, so the alarm goes to tomorrow rather than waking for nothing. */
    @Test
    fun `rearm after a check-in arms tomorrow's band`() = runTest {
        dao.seedOpen(dateKey = today.toString(), startedAt = triggerHour)

        dispatcher().rearm()

        assertEquals(listOf(at(chosenHour, date = today.plusDays(1))), alarms.armed)
    }

    /** Reminders are chosen to the minute: a pass before it posts nothing, and the alarm is armed on it. */
    @Test
    fun `a reminder off the hour is due on its minute, not its hour`() = runTest {
        reminderSettings.setReminderMinute(seed.uuid, chosenHour * 60 + 40)

        assertTrue(dispatcher(FakeTimeSource(at(chosenHour, 39), today)).runOnce().isEmpty())
        assertEquals(listOf(at(chosenHour, 40)), alarms.armed)
        assertEquals(
            listOf(Habit.SEED_ID),
            dispatcher(FakeTimeSource(at(chosenHour, 40), today)).runOnce().map {
                it.id
            },
        )
    }

    /** Arming reads the stored time under the lock, so a new time is armed for, not the old one. */
    @Test
    fun `rearm arms for the time stored before it`() = runTest {
        reminderSettings.setReminderMinute(seed.uuid, (chosenHour + 3) * 60)

        dispatcher(FakeTimeSource(at(chosenHour - 2), today)).rearm()

        assertEquals(listOf(at(chosenHour + 3)), alarms.armed)
    }

    /**
     * A ledger that cannot be read must not end the chain: the fallback is armed — the earliest band
     * any habit could have, which knows nothing of the day's sends but keeps the alarm alive.
     */
    @Test
    fun `an unreadable ledger still arms the fallback`() = runTest {
        val broken =
            dispatcher(clock = FakeTimeSource(at(chosenHour - 2), today), sendLog = { error("database will not open") })

        assertTrue(broken.runOnce().isEmpty())
        broken.rearm()

        assertEquals(listOf(at(chosenHour), at(chosenHour)), alarms.armed)
    }

    /** A refused arm is absorbed, so it cannot take the pass — or its caller — down with it. */
    @Test
    fun `a throwing arm does not fail the pass`() = runTest {
        alarms.throwOnArm = true

        assertEquals(1, dispatcher().runOnce().size)
    }
}
