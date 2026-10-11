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
    private val first = 8
    private val followUp = NudgeSchedule.followUpHour(first)

    /**
     * The state in which the first nudge should fire; individual tests break one thing at a time.
     */
    private fun eligible(
        hourOfDay: Int = first,
        firstReminderHour: Int = first,
        hasCheckedInToday: Boolean = false,
        spentToday: Set<Nudge> = emptySet(),
        shownToday: Int = 0,
        lastShownAtMs: Long? = null,
        config: NudgeConfig = NudgeConfig(),
        nowMillis: Long = 100 * hour,
    ) = NudgeSnapshot(
        nowMillis = nowMillis,
        hourOfDay = hourOfDay,
        firstReminderHour = firstReminderHour,
        hasCheckedInToday = hasCheckedInToday,
        spentToday = spentToday,
        shownToday = shownToday,
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
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, NudgeEligibility.select(eligible()))
    }

    /**
     * The whole reason the trigger is a band rather than a `>=` threshold. [NudgeEligibility] takes
     * the first match in declaration order, so were these thresholds every hour past the chosen one
     * would resolve to FIRST and the follow-up would be unreachable for good.
     */
    @Test
    fun `each checkpoint hour selects its own nudge`() {
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, NudgeEligibility.select(eligible(hourOfDay = first)))
        assertEquals(Nudge.NOT_CHECKED_IN_FOLLOW_UP, NudgeEligibility.select(eligible(hourOfDay = followUp)))
    }

    /** An hour inside a band, not on its boundary, still belongs to that band. */
    @Test
    fun `an hour between the two checkpoints belongs to the first`() {
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, NudgeEligibility.select(eligible(hourOfDay = followUp - 1)))
    }

    /** The bands follow the hour the user chose, not a fixed clock. */
    @Test
    fun `the chosen hour moves both bands`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 10, firstReminderHour = 15)))
        assertEquals(
            Nudge.NOT_CHECKED_IN_FIRST,
            NudgeEligibility.select(eligible(hourOfDay = 15, firstReminderHour = 15)),
        )
        assertEquals(
            Nudge.NOT_CHECKED_IN_FOLLOW_UP,
            NudgeEligibility.select(eligible(hourOfDay = 17, firstReminderHour = 15)),
        )
    }

    /**
     * Moving the hour later after the first reminder went out must not send it a second time: the
     * dedup is per nudge, not per hour, so the day's first reminder stays spent.
     */
    @Test
    fun `changing the hour mid-day does not resend a reminder already sent`() {
        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = 15,
                    firstReminderHour = 15,
                    spentToday = setOf(Nudge.NOT_CHECKED_IN_FIRST),
                    shownToday = 1,
                ),
            ),
        )
    }

    /**
     * There is no app-level do-not-disturb window; the last band simply runs to the end of the day,
     * and Android's per-channel settings are what a user silences the night with.
     */
    @Test
    fun `the last hour of the day is still eligible`() {
        assertEquals(Nudge.NOT_CHECKED_IN_FOLLOW_UP, NudgeEligibility.select(eligible(hourOfDay = 23)))
    }

    /** Pins the boundary itself rather than the number, so a retune moves one constant. */
    @Test
    fun `nothing fires before the first checkpoint`() {
        assertNull(NudgeEligibility.select(eligible(hourOfDay = first - 1)))
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 0)))
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, NudgeEligibility.select(eligible(hourOfDay = first)))
    }

    @Test
    fun `nothing fires once the daily cap is reached`() {
        assertEquals(
            Nudge.NOT_CHECKED_IN_FIRST,
            NudgeEligibility.select(eligible(shownToday = NudgeConfig().maxPerDay - 1)),
        )
        assertNull(NudgeEligibility.select(eligible(shownToday = NudgeConfig().maxPerDay)))
    }

    @Test
    fun `nothing fires once the user has already checked in today`() {
        assertNull(NudgeEligibility.select(eligible(hasCheckedInToday = true)))
    }

    /**
     * The band is hours wide and more than one thing asks inside it — the checkpoint alarm, then the
     * hourly worker. Without this, a pass an hour after the first reminder reposts it under the
     * same id with the same copy, which re-alerts on a high-importance channel and reads as a stuck
     * loop; and because it also spends a slot of the daily cap, the follow-up never fires.
     */
    @Test
    fun `a checkpoint already sent today does not fire again inside its band`() {
        val laterInTheBand = followUp - 1

        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = laterInTheBand,
                    spentToday = setOf(Nudge.NOT_CHECKED_IN_FIRST),
                    shownToday = 1,
                    lastShownAtMs = null,
                ),
            ),
        )
    }

    /** Only the band's own nudge is retired by having been sent — the follow-up still fires. */
    @Test
    fun `the follow-up still fires after the first was sent`() {
        assertEquals(
            Nudge.NOT_CHECKED_IN_FOLLOW_UP,
            NudgeEligibility.select(
                eligible(
                    hourOfDay = followUp,
                    spentToday = setOf(Nudge.NOT_CHECKED_IN_FIRST),
                    shownToday = 1,
                ),
            ),
        )
    }

    /**
     * The checkpoint hours look like they space deliveries out and do not: the alarm is inexact, so
     * Doze can hold the first one until just before the follow-up, which then lands minutes later.
     * Two high-importance messages that close together read as a malfunction, and they spend the whole
     * day's budget at once.
     */
    @Test
    fun `a second nudge is refused until the minimum gap has passed`() {
        val now = 100 * hour
        val gap = NudgeConfig().minGapMs

        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = followUp,
                    shownToday = 1,
                    lastShownAtMs = now - gap + 1,
                    nowMillis = now,
                ),
            ),
        )
        assertEquals(
            Nudge.NOT_CHECKED_IN_FOLLOW_UP,
            NudgeEligibility.select(
                eligible(
                    hourOfDay = followUp,
                    shownToday = 1,
                    lastShownAtMs = now - gap,
                    nowMillis = now,
                ),
            ),
        )
    }

    /** The gap never blocks a delivery that arrived on time — it is under the real spacing. */
    @Test
    fun `the minimum gap is smaller than the gap between the checkpoints`() {
        val closest = NudgeSchedule.FOLLOW_UP_OFFSET_HOURS * hour

        assertTrue(
            "A gap of ${NudgeConfig().minGapMs}ms would suppress punctual checkpoints ${closest}ms apart",
            NudgeConfig().minGapMs < closest,
        )
    }

    /** A clock moved backwards must not read as "long enough ago" and unlock a burst. */
    @Test
    fun `a backwards clock does not unlock a second nudge`() {
        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = followUp,
                    shownToday = 1,
                    lastShownAtMs = 100 * hour,
                    nowMillis = 10 * hour,
                ),
            ),
        )
    }

    /**
     * The daily cap is the only frequency bound, and it is counted from the log rather than a clock
     * reading — so a device clock moved backwards or across a timezone cannot unlock a repeat. A
     * rolling-window cooldown would need its own guard against exactly that.
     */
    @Test
    fun `the cap does not depend on the clock`() {
        val capped = NudgeConfig().maxPerDay
        val now = 100 * hour

        assertNull(NudgeEligibility.select(eligible(nowMillis = now, shownToday = capped)))
        assertNull(NudgeEligibility.select(eligible(nowMillis = now - 500 * hour, shownToday = capped)))
        assertNull(NudgeEligibility.select(eligible(nowMillis = 0L, shownToday = capped)))
    }

    /**
     * Dedup is per nudge and the bands move with the chosen hour, so without this a user who moved
     * the hour later after the follow-up had gone would reopen the first band and be sent "ready
     * when you are" after "no session yet today".
     */
    @Test
    fun `a sent follow-up spends the first checkpoint too`() {
        assertNull(
            NudgeEligibility.select(
                eligible(
                    spentToday = setOf(Nudge.NOT_CHECKED_IN_FOLLOW_UP),
                    shownToday = 1,
                    lastShownAtMs = 0L,
                ),
            ),
        )
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
        shownToday: Int = spentToday.size,
    ) = eligible(
        hourOfDay = Instant.ofEpochMilli(nowMs).atZone(utc).hour,
        hasCheckedInToday = hasCheckedInToday,
        spentToday = spentToday,
        shownToday = shownToday,
        lastShownAtMs = lastShownAtMs,
        nowMillis = nowMs,
    )

    @Test
    fun `before the first band the alarm is its opening`() {
        assertEquals(at(first), NudgeEligibility.nextEligibleAt(live(at(first - 3)), utc))
    }

    /**
     * The case a plain "next checkpoint" arm loses: a re-arm landing after the follow-up hour with
     * the follow-up unsent. The next checkpoint after now is tomorrow's; the follow-up is due now.
     */
    @Test
    fun `an open band that is unsent is due now`() {
        val now = at(followUp, minute = 30)
        val snapshot = live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(first))

        assertEquals(now, NudgeEligibility.nextEligibleAt(snapshot, utc))
    }

    /** A first reminder that landed late holds the follow-up until the gap clears, not until tomorrow. */
    @Test
    fun `a follow-up held back by the gap is due when the gap clears`() {
        val lastShown = at(followUp) - 30 * 60 * 1000L
        val snapshot = live(at(followUp), setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = lastShown)

        assertNull(NudgeEligibility.select(snapshot))
        assertEquals(lastShown + NudgeConfig().minGapMs, NudgeEligibility.nextEligibleAt(snapshot, utc))
    }

    @Test
    fun `nothing left today arms tomorrow's first checkpoint`() {
        val tomorrow = at(first, date = day.plusDays(1))
        val bothSent = live(at(followUp, 5), Nudge.entries.toSet(), lastShownAtMs = at(followUp))
        val checkedIn = live(at(first), hasCheckedInToday = true)
        val capped = live(at(first), shownToday = NudgeConfig().maxPerDay, lastShownAtMs = 0L)

        assertEquals(tomorrow, NudgeEligibility.nextEligibleAt(bothSent, utc))
        assertEquals(tomorrow, NudgeEligibility.nextEligibleAt(checkedIn, utc))
        assertEquals(tomorrow, NudgeEligibility.nextEligibleAt(capped, utc))
    }

    /**
     * A clock moved backwards puts the last send in the future. The gap then clears later than it
     * would have, and the alarm must wait for exactly that — the moment [NudgeEligibility.select]
     * next allows a send — rather than fire early into a pass that declines.
     */
    @Test
    fun `a clock moved backwards waits for the gap the rule will apply`() {
        val now = at(followUp)
        val lastShown = now + 3 * hour
        val snapshot = live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = lastShown)

        assertEquals(lastShown + NudgeConfig().minGapMs, NudgeEligibility.nextEligibleAt(snapshot, utc))
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
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(first, 10)) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(followUp - 1, 40)) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN_FOLLOW_UP), lastShownAtMs = at(followUp)) },
            { now -> live(now, hasCheckedInToday = true) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(23)) },
        )
        for (state in states) {
            for (minute in 0 until 24 * 60 step 10) {
                val now = at(0) + minute * 60_000L
                val snapshot = state(now)
                val next = NudgeEligibility.nextEligibleAt(snapshot, utc)

                assertTrue("next before now at minute $minute", next >= now)
                assertEquals(
                    "minute $minute",
                    NudgeEligibility.select(snapshot) != null,
                    next == now,
                )
            }
        }
    }

    /**
     * The same agreement at the ends of the hour range and across DST transitions, where [select]
     * reads a local hour and [NudgeEligibility.nextEligibleAt] reads instants. 20 leaves the
     * follow-up band two hours before midnight; Santiago moves its clocks *at* midnight, so the day's
     * end is the instant in question; London moves them inside the night. Swept over each local
     * day's real instants, so a skipped or repeated hour is swept exactly as the device lives it.
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
        val hours = listOf(NudgeSchedule.firstHourRange.first, first, NudgeSchedule.firstHourRange.last)
        for ((zone, date) in days) {
            hours.forEach { assertAgreementOver(zone, date, it) }
        }
    }

    private fun assertAgreementOver(zone: ZoneId, date: LocalDate, firstHour: Int) {
        fun local(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val followUpHour = NudgeSchedule.followUpHour(firstHour)
        val states: List<Pair<Set<Nudge>, Long?>> = listOf(
            emptySet<Nudge>() to null,
            setOf(Nudge.NOT_CHECKED_IN_FIRST) to local(firstHour, 10),
            setOf(Nudge.NOT_CHECKED_IN_FIRST) to local(followUpHour - 1, 40),
            setOf(Nudge.NOT_CHECKED_IN_FOLLOW_UP) to local(followUpHour),
        )
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        for ((spent, lastShown) in states) {
            for (now in dayStart until dayEnd step 10 * 60_000L) {
                val snapshot = NudgeSnapshot(
                    nowMillis = now,
                    hourOfDay = Instant.ofEpochMilli(now).atZone(zone).hour,
                    firstReminderHour = firstHour,
                    hasCheckedInToday = false,
                    spentToday = spent,
                    shownToday = spent.size,
                    lastShownAtMs = lastShown,
                )
                val next = NudgeEligibility.nextEligibleAt(snapshot, zone)
                val label = "$zone $date first=$firstHour spent=$spent at ${Instant.ofEpochMilli(now)}"

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
        val now = at(followUp, minute = 30)
        val snapshot = live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(first))

        assertEquals(now, NudgeEligibility.nextEligibleAt(snapshot, utc))
        assertEquals(now + delay, NudgeEligibility.nextAlarmAt(snapshot, utc))
    }

    /** Put off past the end of its band, a due nudge gives way to the next band's opening. */
    @Test
    fun `a due nudge near its band's end gives way to the next band`() {
        val now = at(followUp) - delay / 2

        assertEquals(at(followUp), NudgeEligibility.nextAlarmAt(live(now), utc))
    }

    /** Put off past midnight, the day's last band gives way to tomorrow's first checkpoint. */
    @Test
    fun `a due follow-up near midnight gives way to tomorrow`() {
        val now = at(23, minute = 50)
        val snapshot = live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(first))

        assertEquals(at(first, date = day.plusDays(1)), NudgeEligibility.nextAlarmAt(snapshot, utc))
    }

    /**
     * Only a due nudge moves: an on-time checkpoint and a gap-deferred follow-up are armed exactly
     * where the rules allow them, and nothing is ever armed for now.
     */
    @Test
    fun `nextAlarmAt differs from nextEligibleAt only for a nudge already due`() {
        val states: List<(Long) -> NudgeSnapshot> = listOf(
            { now -> live(now) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN_FIRST), lastShownAtMs = at(followUp - 1, 40)) },
            { now -> live(now, setOf(Nudge.NOT_CHECKED_IN_FOLLOW_UP), lastShownAtMs = at(followUp)) },
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
}
