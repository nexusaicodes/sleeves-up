package com.checkin.app

import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeConfig
import com.checkin.app.notify.nudge.NudgeEligibility
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.NudgeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NudgeEligibilityTest {

    private val hour = 60 * 60 * 1000L

    /** A chosen hour other than the default, so nothing here passes by coinciding with it. */
    private val chosen = 8

    /**
     * One habit's state in which its nudge should fire; individual tests break one thing at a time.
     * Times are given as an hour plus an optional [minute] / [reminderMinute] past it.
     */
    private fun eligible(
        hourOfDay: Int = chosen,
        reminderHour: Int = chosen,
        hasCheckedInToday: Boolean = false,
        spentToday: Set<Nudge> = emptySet(),
        lastShownAtMs: Long? = null,
        config: NudgeConfig = NudgeConfig(),
        nowMillis: Long = 100 * hour,
        minute: Int = 0,
        reminderMinute: Int = 0,
    ) = NudgeSnapshot(
        nowMillis = nowMillis,
        minuteOfDay = hourOfDay * 60 + minute,
        reminderMinute = reminderHour * 60 + reminderMinute,
        hasCheckedInToday = hasCheckedInToday,
        spentToday = spentToday,
        lastShownAtMs = lastShownAtMs,
        config = config,
    )

    /**
     * The baseline carries no history of any kind — no first check-in, nothing sent before. That is
     * deliberate: these nudges tell a user they haven't checked in today, and a user who has never
     * checked in is exactly who they are for. A tracking-started gate would lock them away from them.
     */
    @Test
    fun `a snapshot with no history at all is eligible`() {
        assertEquals(Nudge.NOT_CHECKED_IN, NudgeEligibility.select(eligible()))
    }

    /** One band, from the chosen hour to midnight; an hour inside it is as good as its opening. */
    @Test
    fun `any hour from the chosen one to midnight is eligible`() {
        for (h in chosen..23) {
            assertEquals(
                "hour $h",
                Nudge.NOT_CHECKED_IN,
                NudgeEligibility.select(eligible(hourOfDay = h)),
            )
        }
    }

    /** Pins the boundary itself rather than the number, so a retune moves one constant. */
    @Test
    fun `nothing fires before the chosen hour`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = chosen - 1)))
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 0)))
    }

    /** The band follows the hour chosen for the habit, not a fixed clock. */
    @Test
    fun `the chosen hour moves the band`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 10, reminderHour = 15)))
        assertEquals(Nudge.NOT_CHECKED_IN, NudgeEligibility.select(eligible(hourOfDay = 15, reminderHour = 15)))
    }

    @Test
    fun `nothing fires once the habit has a session today`() {
        assertNull(NudgeEligibility.select(eligible(hasCheckedInToday = true)))
    }

    /**
     * Once a day per habit, however often the question is asked inside the band — the checkpoint
     * alarm, then the hourly worker. Without this a later pass reposts the same nudge under the same
     * id with the same copy, re-alerting on a high-importance channel. Moving the hour does not
     * reopen it either: the dedup is per nudge, not per hour.
     */
    @Test
    fun `a nudge already attempted today does not fire again`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 20, spentToday = setOf(Nudge.NOT_CHECKED_IN))))
        assertNull(
            NudgeEligibility.select(
                eligible(hourOfDay = 15, reminderHour = 15, spentToday = setOf(Nudge.NOT_CHECKED_IN)),
            ),
        )
    }

    /**
     * There is no app-level do-not-disturb window; the band simply runs to the end of the day, and
     * Android's per-channel settings are what a user silences the night with.
     */
    @Test
    fun `the last hour of the day is still eligible`() {
        assertEquals(Nudge.NOT_CHECKED_IN, NudgeEligibility.select(eligible(hourOfDay = 23)))
    }

    /**
     * The gap spans habits: two habits sharing an hour, or one whose inexact alarm slipped into
     * another's, would otherwise arrive as two high-importance messages back to back.
     */
    @Test
    fun `a nudge is refused until the minimum gap after any habit's has passed`() {
        val now = 100 * hour
        val gap = NudgeConfig().minGapMs

        assertNull(NudgeEligibility.select(eligible(hourOfDay = 12, lastShownAtMs = now - gap + 1, nowMillis = now)))
        assertEquals(
            Nudge.NOT_CHECKED_IN,
            NudgeEligibility.select(eligible(hourOfDay = 12, lastShownAtMs = now - gap, nowMillis = now)),
        )
    }

    /** A clock moved backwards must not read as "long enough ago" and unlock a burst. */
    @Test
    fun `a backwards clock does not unlock a nudge inside the gap`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 12, lastShownAtMs = 100 * hour, nowMillis = 10 * hour)))
    }

    // --- nextEligibleAt ---

    private val utc: ZoneId = ZoneId.of("UTC")
    private val day: LocalDate = LocalDate.of(2026, 6, 15)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): Long =
        date.atTime(hour, minute).atZone(utc).toInstant().toEpochMilli()

    /** A snapshot whose hour is derived from its instant, as the dispatcher builds one. */
    private fun live(
        nowMs: Long,
        spentToday: Set<Nudge> = emptySet(),
        lastShownAtMs: Long? = null,
        hasCheckedInToday: Boolean = false,
        reminderHour: Int = chosen,
    ) = eligible(
        hourOfDay = Instant.ofEpochMilli(nowMs).atZone(utc).hour,
        minute = Instant.ofEpochMilli(nowMs).atZone(utc).minute,
        reminderHour = reminderHour,
        hasCheckedInToday = hasCheckedInToday,
        spentToday = spentToday,
        lastShownAtMs = lastShownAtMs,
        nowMillis = nowMs,
    )

    @Test
    fun `before the band the alarm is its opening`() {
        assertEquals(at(chosen), NudgeEligibility.nextEligibleAt(live(at(chosen - 3)), utc))
    }

    /**
     * The case a plain "next band" arm loses: a re-arm landing inside the band with the nudge unsent.
     * The next band opening after now is tomorrow's; this habit's nudge is due now.
     */
    @Test
    fun `an open band that is unsent is due now`() {
        val now = at(chosen + 3, minute = 30)
        assertEquals(now, NudgeEligibility.nextEligibleAt(live(now), utc))
    }

    /** Another habit's reminder holds this one until the gap clears, not until tomorrow. */
    @Test
    fun `a nudge held back by the gap is due when the gap clears`() {
        val lastShown = at(chosen) - 30 * 60 * 1000L
        val snapshot = live(at(chosen), lastShownAtMs = lastShown)

        assertNull(NudgeEligibility.select(snapshot))
        assertEquals(lastShown + NudgeConfig().minGapMs, NudgeEligibility.nextEligibleAt(snapshot, utc))
    }

    @Test
    fun `nothing left today arms tomorrow's band`() {
        val tomorrow = at(chosen, date = day.plusDays(1))

        assertEquals(tomorrow, NudgeEligibility.nextEligibleAt(live(at(20), setOf(Nudge.NOT_CHECKED_IN)), utc))
        assertEquals(tomorrow, NudgeEligibility.nextEligibleAt(live(at(chosen), hasCheckedInToday = true), utc))
    }

    /**
     * A clock moved backwards puts the last send in the future. The gap then clears later than it
     * would have, and the alarm must wait for exactly that — the moment [NudgeEligibility.select]
     * next allows a send — rather than fire early into a pass that declines.
     */
    @Test
    fun `a clock moved backwards waits for the gap the rule will apply`() {
        val now = at(12)
        val lastShown = now + 3 * hour

        assertEquals(
            lastShown + NudgeConfig().minGapMs,
            NudgeEligibility.nextEligibleAt(live(now, lastShownAtMs = lastShown), utc),
        )
    }

    /**
     * The two questions must agree, or the alarm fires at an instant the rule then declines — silence
     * with nothing to point at — or the rule would send at an instant the alarm never wakes for. Swept
     * every ten minutes across a day in each state the day passes through, `nextEligibleAt` must be
     * now exactly when `select` returns a nudge, and never earlier than now otherwise.
     */
    @Test
    fun `nextEligibleAt is now exactly when select sends`() {
        val states: List<(Long) -> NudgeSnapshot> = listOf(
            { now -> live(now) },
            { now -> live(now, lastShownAtMs = at(chosen, 10)) },
            { now -> live(now, lastShownAtMs = at(22, 40)) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN), lastShownAtMs = at(chosen)) },
            { now -> live(now, hasCheckedInToday = true) },
        )
        for (state in states) {
            for (minute in 0 until 24 * 60 step 10) {
                val now = at(0) + minute * 60_000L
                val snapshot = state(now)
                val next = NudgeEligibility.nextEligibleAt(snapshot, utc)

                assertTrue("next before now at minute $minute", next >= now)
                assertEquals("minute $minute", NudgeEligibility.select(snapshot) != null, next == now)
            }
        }
    }

    /**
     * The same agreement at the ends of the hour range and across DST transitions, where [select]
     * reads a local hour and [NudgeEligibility.nextEligibleAt] reads instants. Santiago moves its
     * clocks *at* midnight, so the day's end is the instant in question; London moves them inside the
     * night. Swept over each local day's real instants, so a skipped or repeated hour is swept
     * exactly as the device lives it.
     */
    @Test
    fun `nextEligibleAt agrees with select at the range ends and across DST`() {
        val days = listOf(
            ZoneId.of("UTC") to LocalDate.of(2026, 6, 15),
            ZoneId.of("America/Santiago") to LocalDate.of(2026, 9, 5),
            ZoneId.of("America/Santiago") to LocalDate.of(2026, 4, 4),
            ZoneId.of("Europe/London") to LocalDate.of(2026, 3, 29),
            ZoneId.of("Europe/London") to LocalDate.of(2026, 10, 25),
        )
        // A time off the hour among them, since reminders are chosen to the minute.
        val minutes =
            listOf(NudgeSchedule.minuteRange.first, chosen * 60, chosen * 60 + 25, NudgeSchedule.minuteRange.last)
        for ((zone, date) in days) {
            minutes.forEach { assertAgreementOver(zone, date, it) }
        }
    }

    private fun assertAgreementOver(zone: ZoneId, date: LocalDate, reminderMinute: Int) {
        fun local(minuteOfDay: Int) =
            date.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant().toEpochMilli()
        val states: List<Pair<Set<Nudge>, Long?>> = listOf(
            emptySet<Nudge>() to null,
            emptySet<Nudge>() to local(reminderMinute + 10),
            setOf(Nudge.NOT_CHECKED_IN) to local(reminderMinute),
        )
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        for ((spent, lastShown) in states) {
            for (now in dayStart until dayEnd step 10 * 60_000L) {
                val snapshot = NudgeSnapshot(
                    nowMillis = now,
                    minuteOfDay = Instant.ofEpochMilli(now).atZone(zone).let { it.hour * 60 + it.minute },
                    reminderMinute = reminderMinute,
                    hasCheckedInToday = false,
                    spentToday = spent,
                    lastShownAtMs = lastShown,
                )
                val next = NudgeEligibility.nextEligibleAt(snapshot, zone)
                val label = "$zone $date minute=$reminderMinute spent=$spent at ${Instant.ofEpochMilli(now)}"

                assertTrue("next before now: $label", next >= now)
                assertEquals(label, NudgeEligibility.select(snapshot) != null, next == now)
            }
        }
    }

    // --- nextAlarmAt ---

    private val delay = NudgeConfig().dueNowDelayMs

    /**
     * The alarm never fires straight away. A due nudge is armed off the back of a process start or a
     * new hour — usually the user opening the app — and posting then lands over the screen they
     * opened to check in from.
     */
    @Test
    fun `a nudge already due is put off`() {
        val now = at(chosen + 2, minute = 30)
        val snapshot = live(now)

        assertEquals(now, NudgeEligibility.nextEligibleAt(snapshot, utc))
        assertEquals(now + delay, NudgeEligibility.nextAlarmAt(snapshot, utc))
    }

    /** Put off past midnight, the band gives way to tomorrow's. */
    @Test
    fun `a due nudge near midnight gives way to tomorrow`() {
        assertEquals(at(chosen, date = day.plusDays(1)), NudgeEligibility.nextAlarmAt(live(at(23, minute = 50)), utc))
    }

    /**
     * Only a due nudge moves: an on-time band opening and a gap-deferred nudge are armed exactly
     * where the rules allow them, and nothing is ever armed for now.
     */
    @Test
    fun `nextAlarmAt differs from nextEligibleAt only for a nudge already due`() {
        val states: List<(Long) -> NudgeSnapshot> = listOf(
            { now -> live(now) },
            { now -> live(now, lastShownAtMs = at(chosen + 1, 40)) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN), lastShownAtMs = at(chosen)) },
        )
        for (state in states) {
            for (minute in 0 until 24 * 60 step 10) {
                val now = at(0) + minute * 60_000L
                val snapshot = state(now)
                val eligible = NudgeEligibility.nextEligibleAt(snapshot, utc)
                val alarm = NudgeEligibility.nextAlarmAt(snapshot, utc)

                assertTrue("alarm not ahead of now at minute $minute", alarm > now)
                if (eligible > now) assertEquals("minute $minute", eligible, alarm)
            }
        }
    }

    // --- nextAlarmAcross ---

    /** The habits share one alarm, so it is armed for whichever of them is due soonest. */
    @Test
    fun `the shared alarm is the soonest of any habit's`() {
        val now = at(6)
        val gym = live(now, reminderHour = 18)
        val writing = live(now, reminderHour = 9)
        val doneToday = live(now, reminderHour = 7, hasCheckedInToday = true)

        assertEquals(at(9), NudgeEligibility.nextAlarmAcross(listOf(gym, writing, doneToday), utc))
    }

    /** Two habits at one hour: the one the gap holds back is armed for when it clears. */
    @Test
    fun `a habit held behind another's reminder is armed for the gap clearing`() {
        val now = at(9, 5)
        val reminded = live(now, setOf(Nudge.NOT_CHECKED_IN), lastShownAtMs = at(9), reminderHour = 9)
        val heldBack = live(now, lastShownAtMs = at(9), reminderHour = 9)

        assertEquals(
            at(9) + NudgeConfig().minGapMs,
            NudgeEligibility.nextAlarmAcross(listOf(reminded, heldBack), utc),
        )
    }

    @Test
    fun `with no habits there is nothing to arm for`() {
        assertNull(NudgeEligibility.nextAlarmAcross(emptyList(), utc))
    }
}
