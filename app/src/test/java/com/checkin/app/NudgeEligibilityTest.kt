package com.checkin.app

import com.checkin.app.notify.engagement.EngagementSnapshot
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeConfig
import com.checkin.app.notify.engagement.NudgeEligibility
import com.checkin.app.notify.engagement.NudgeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        isCheckedIn: Boolean = false,
        openSessionOverdue: Boolean = false,
        hasCheckedInToday: Boolean = false,
        shownToday: Int = 0,
        config: NudgeConfig = NudgeConfig(),
        nowMillis: Long = 100 * hour,
    ) = EngagementSnapshot(
        nowMillis = nowMillis,
        hourOfDay = hourOfDay,
        isCheckedIn = isCheckedIn,
        openSessionOverdue = openSessionOverdue,
        hasCheckedInToday = hasCheckedInToday,
        shownToday = shownToday,
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

    /** A session open since yesterday means today has no row yet, but the user is plainly working. */
    @Test
    fun `nothing fires while a session is open`() {
        assertNull(NudgeEligibility.select(eligible(isCheckedIn = true)))
    }

    /**
     * A session past its own day boundary is not evidence of presence — it is a session whose alarms
     * were lost, since the boundary close would otherwise have ended it. Counting it as "checked in"
     * silenced every nudge for as long as it stayed open, which is indefinitely on a device the user
     * has stopped opening the app on, and the nudge it silenced is the one that would have surfaced it.
     */
    @Test
    fun `a session already past its day boundary does not suppress`() {
        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
            NudgeEligibility.select(eligible(isCheckedIn = true, openSessionOverdue = true)),
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
