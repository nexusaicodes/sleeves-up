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

class NudgeEligibilityTest {

    private val hour = 60 * 60 * 1000L

    /**
     * The state in which the morning nudge should fire; individual tests break one thing at a time.
     *
     * The default hour is taken from the checkpoint rather than written out, so retuning the
     * schedule cannot leave this suite quietly exercising a band it was not written for.
     */
    private fun eligible(
        hourOfDay: Int = NudgeSchedule.Checkpoint.MORNING.hour,
        hasCheckedInToday: Boolean = false,
        alreadySentToday: Set<Nudge> = emptySet(),
        shownToday: Int = 0,
        lastShownAtMs: Long? = null,
        config: NudgeConfig = NudgeConfig(),
        nowMillis: Long = 100 * hour,
    ) = NudgeSnapshot(
        nowMillis = nowMillis,
        hourOfDay = hourOfDay,
        hasCheckedInToday = hasCheckedInToday,
        alreadySentToday = alreadySentToday,
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
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, NudgeEligibility.select(eligible()))
    }

    /**
     * The whole reason the trigger is a band rather than a `>=` threshold. [NudgeEligibility] takes
     * the first match in declaration order, so were these thresholds every hour past 10:00 would
     * resolve to MORNING and the afternoon and evening copy would be unreachable for good — three
     * nudges declared, given ids and copy, and two of them dead.
     */
    @Test
    fun `each checkpoint hour selects its own nudge`() {
        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
            NudgeEligibility.select(eligible(hourOfDay = NudgeSchedule.Checkpoint.MORNING.hour)),
        )
        assertEquals(
            Nudge.NOT_CHECKED_IN_AFTERNOON,
            NudgeEligibility.select(eligible(hourOfDay = NudgeSchedule.Checkpoint.AFTERNOON.hour)),
        )
        assertEquals(
            Nudge.NOT_CHECKED_IN_EVENING,
            NudgeEligibility.select(eligible(hourOfDay = NudgeSchedule.Checkpoint.EVENING.hour)),
        )
    }

    /** An hour inside a band, not on its boundary, still belongs to that band. */
    @Test
    fun `an hour between two checkpoints belongs to the earlier one`() {
        val between = NudgeSchedule.Checkpoint.AFTERNOON.hour - 1
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, NudgeEligibility.select(eligible(hourOfDay = between)))
    }

    /**
     * There is no app-level do-not-disturb window; the last band simply runs to the end of the day,
     * and Android's per-channel settings are what a user silences the night with.
     */
    @Test
    fun `the last hour of the day is still eligible`() {
        assertEquals(Nudge.NOT_CHECKED_IN_EVENING, NudgeEligibility.select(eligible(hourOfDay = 23)))
    }

    /** Pins the boundary itself rather than the number, so a retune moves one constant. */
    @Test
    fun `nothing fires before the first checkpoint`() {
        val first = NudgeSchedule.Checkpoint.MORNING.hour
        assertNull(NudgeEligibility.select(eligible(hourOfDay = first - 1)))
        assertNull(NudgeEligibility.select(eligible(hourOfDay = 0)))
        assertEquals(Nudge.NOT_CHECKED_IN_MORNING, NudgeEligibility.select(eligible(hourOfDay = first)))
    }

    @Test
    fun `nothing fires once the daily cap is reached`() {
        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
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
     * hourly worker. Without this, an 11:00 pass reposts the 10:00 morning nudge under the same id
     * with the same copy, which re-alerts on a high-importance channel and reads as a stuck loop; and
     * because it also spends a slot of the daily cap, the afternoon and evening copy never fire.
     */
    @Test
    fun `a checkpoint already sent today does not fire again inside its band`() {
        val laterInTheBand = NudgeSchedule.Checkpoint.AFTERNOON.hour - 1

        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = laterInTheBand,
                    alreadySentToday = setOf(Nudge.NOT_CHECKED_IN_MORNING),
                    shownToday = 1,
                    lastShownAtMs = null,
                ),
            ),
        )
    }

    /** Only the band's own nudge is retired by having been sent — the later checkpoints still fire. */
    @Test
    fun `a later checkpoint still fires after an earlier one was sent`() {
        assertEquals(
            Nudge.NOT_CHECKED_IN_AFTERNOON,
            NudgeEligibility.select(
                eligible(
                    hourOfDay = NudgeSchedule.Checkpoint.AFTERNOON.hour,
                    alreadySentToday = setOf(Nudge.NOT_CHECKED_IN_MORNING),
                    shownToday = 1,
                ),
            ),
        )
    }

    /**
     * The checkpoint hours look like they space deliveries out and do not: the alarm is inexact, so
     * Doze can hold the morning one until 13:57 and the afternoon one then lands three minutes later.
     * Two high-importance messages that close together read as a malfunction, and they spend the whole
     * day's budget before it is half over.
     */
    @Test
    fun `a second nudge is refused until the minimum gap has passed`() {
        val now = 100 * hour
        val gap = NudgeConfig().minGapMs

        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = NudgeSchedule.Checkpoint.AFTERNOON.hour,
                    shownToday = 1,
                    lastShownAtMs = now - gap + 1,
                    nowMillis = now,
                ),
            ),
        )
        assertEquals(
            Nudge.NOT_CHECKED_IN_AFTERNOON,
            NudgeEligibility.select(
                eligible(
                    hourOfDay = NudgeSchedule.Checkpoint.AFTERNOON.hour,
                    shownToday = 1,
                    lastShownAtMs = now - gap,
                    nowMillis = now,
                ),
            ),
        )
    }

    /** The gap never blocks a delivery that arrived on time — it is under the smallest real spacing. */
    @Test
    fun `the minimum gap is smaller than the closest two checkpoints`() {
        val closest = NudgeSchedule.Checkpoint.entries
            .zipWithNext { a, b -> (b.hour - a.hour) * hour }
            .min()

        assertTrue(
            "A gap of ${NudgeConfig().minGapMs}ms would suppress punctual checkpoints ${closest}ms apart",
            NudgeConfig().minGapMs <= closest,
        )
    }

    /** A clock moved backwards must not read as "long enough ago" and unlock a burst. */
    @Test
    fun `a backwards clock does not unlock a second nudge`() {
        assertNull(
            NudgeEligibility.select(
                eligible(
                    hourOfDay = NudgeSchedule.Checkpoint.AFTERNOON.hour,
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
}
