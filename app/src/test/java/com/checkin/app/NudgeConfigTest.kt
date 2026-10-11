package com.checkin.app

import com.checkin.app.notify.nudge.NudgeConfig
import com.checkin.app.notify.nudge.NudgeSchedule
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Joins the two files that between them decide when a nudge can be sent.
 *
 * [NudgeConfig.DEFAULT_MIN_GAP_MS] is documented as being under the smallest gap between two
 * checkpoints — a claim about a constant that lives in [NudgeSchedule], with nothing holding the two
 * together. Two copies of one fact in two files is how an alarm comes to fire at an instant the
 * rules then decline to act on, which on a device is indistinguishable from an alarm that never
 * fired at all.
 */
class NudgeConfigTest {

    @Test
    fun `min gap never blocks a checkpoint that arrived on time`() {
        // Read from both files rather than restated here: a test carrying its own copy of the
        // numbers would keep passing while the two it is comparing drifted apart.
        val smallestGap = NudgeSchedule.FOLLOW_UP_OFFSET_HOURS * HOUR_MS
        assertTrue(
            "minGapMs (${NudgeConfig.DEFAULT_MIN_GAP_MS}ms) must stay under the smallest checkpoint " +
                "gap (${smallestGap}ms), or a punctual delivery is suppressed by the previous one",
            NudgeConfig.DEFAULT_MIN_GAP_MS < smallestGap,
        )
    }

    @Test
    fun `the cap admits every checkpoint, so the follow-up is never refused by it`() {
        // Below the checkpoint count, the first reminder would spend the day and the follow-up — the
        // one that exists because the day got away — could never send.
        assertTrue(NudgeConfig().maxPerDay >= NudgeSchedule.Checkpoint.entries.size)
    }

    private companion object {
        const val HOUR_MS = 60L * 60L * 1_000L
    }
}
