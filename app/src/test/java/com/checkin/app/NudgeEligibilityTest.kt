package com.checkin.app

import com.checkin.app.notify.engagement.EngagementSnapshot
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeConfig
import com.checkin.app.notify.engagement.NudgeEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgeEligibilityTest {

    private val hour = 60 * 60 * 1000L

    /** The state in which NOT_CHECKED_IN_BY should fire; individual tests break one thing at a time. */
    private fun eligible(
        hourOfDay: Int = 12,
        isCheckedIn: Boolean = false,
        hasCheckedInToday: Boolean = false,
        shownToday: Int = 0,
        config: NudgeConfig = NudgeConfig(),
        nowMillis: Long = 100 * hour,
    ) = EngagementSnapshot(
        nowMillis = nowMillis,
        hourOfDay = hourOfDay,
        isCheckedIn = isCheckedIn,
        hasCheckedInToday = hasCheckedInToday,
        shownToday = shownToday,
        config = config,
    )

    /**
     * The baseline carries no history of any kind — no first check-in, nothing sent before. That is
     * deliberate: the one nudge that exists tells a user they haven't checked in today, and a user
     * who has never checked in is exactly who it is for. A tracking-started gate would lock it away
     * from them.
     */
    @Test
    fun `a snapshot with no history at all is eligible`() {
        assertEquals(Nudge.NOT_CHECKED_IN_BY, NudgeEligibility.select(eligible()))
    }

    @Test
    fun `nothing fires once the daily cap is reached`() {
        assertNull(NudgeEligibility.select(eligible(shownToday = 1)))
        assertEquals(
            Nudge.NOT_CHECKED_IN_BY,
            NudgeEligibility.select(eligible(shownToday = 1, config = NudgeConfig(maxPerDay = 2))),
        )
    }

    /**
     * There is no app-level do-not-disturb window. The hour only ever gates a nudge through its own
     * trigger rule, so a late-evening hour past that rule is eligible; Android's per-channel settings
     * are what a user silences the night with.
     */
    @Test
    fun `no hour of the day is suppressed on its own`() {
        assertEquals(Nudge.NOT_CHECKED_IN_BY, NudgeEligibility.select(eligible(hourOfDay = 23)))
    }

    /** Pins the boundary itself rather than the number, so a retune moves one constant. */
    @Test
    fun `nothing fires before the trigger hour`() {
        val triggerHour = NudgeConfig().notCheckedInByHour
        assertNull(NudgeEligibility.select(eligible(hourOfDay = triggerHour - 1)))
        assertEquals(Nudge.NOT_CHECKED_IN_BY, NudgeEligibility.select(eligible(hourOfDay = triggerHour)))
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
     * The daily cap is the only frequency bound, and it is counted from the log rather than a clock
     * reading — so a device clock moved backwards or across a timezone cannot unlock a repeat. A
     * rolling-window cooldown would need its own guard against exactly that.
     */
    @Test
    fun `the cap does not depend on the clock`() {
        val now = 100 * hour

        assertNull(NudgeEligibility.select(eligible(nowMillis = now, shownToday = 1)))
        assertNull(NudgeEligibility.select(eligible(nowMillis = now - 500 * hour, shownToday = 1)))
        assertNull(NudgeEligibility.select(eligible(nowMillis = 0L, shownToday = 1)))
    }
}
