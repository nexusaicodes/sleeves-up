package com.checkin.app

import com.checkin.app.notify.nudge.NudgeConfig
import com.checkin.app.notify.nudge.NudgeSchedule
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Joins the two files that between them decide when a nudge can be sent.
 *
 * [NudgeSchedule]'s latest choosable hour is documented as leaving a band wide enough for an
 * inexact alarm's slip plus [NudgeConfig.DEFAULT_MIN_GAP_MS] — a claim about a constant that lives
 * in the other file, with nothing holding the two together. Two copies of one fact in two files is
 * how a reminder comes to be deferred past midnight, which on a device is indistinguishable from an
 * alarm that never fired at all.
 */
class NudgeConfigTest {

    @Test
    fun `the latest band still has room for a slipped alarm and a deferral`() {
        // Read from both files rather than restated here: a test carrying its own copy of the
        // numbers would keep passing while the two it is comparing drifted apart.
        val latestBandMs = (MINUTES_PER_DAY - NudgeSchedule.minuteRange.last) * MINUTE_MS
        val needed = ALARM_SLIP_MS + NudgeConfig.DEFAULT_MIN_GAP_MS
        assertTrue(
            "the latest band (${latestBandMs}ms) must outlast an alarm slip plus the gap (${needed}ms), " +
                "or a habit held back behind another's reminder is deferred into tomorrow",
            latestBandMs >= needed,
        )
    }

    private companion object {
        const val MINUTE_MS = 60L * 1_000L
        const val HOUR_MS = 60L * MINUTE_MS
        const val MINUTES_PER_DAY = 24 * 60

        /** How late `setAndAllowWhileIdle` may deliver, as `NudgeSchedule` states it. */
        const val ALARM_SLIP_MS = HOUR_MS
    }
}
