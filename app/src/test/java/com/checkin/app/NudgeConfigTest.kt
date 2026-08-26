package com.checkin.app

import com.checkin.app.notify.nudge.NudgeConfig
import com.checkin.app.notify.nudge.NudgeSchedule
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Joins the two files that between them decide when a nudge can be sent.
 *
 * [NudgeConfig.DEFAULT_MIN_GAP_MS] is documented as being under the smallest gap between two
 * checkpoints — a claim about constants that live in [NudgeSchedule], with nothing holding the two
 * together. Two copies of one fact in two files is how an alarm comes to fire at an instant the
 * rules then decline to act on, which on a device is indistinguishable from an alarm that never
 * fired at all.
 */
class NudgeConfigTest {

    private val checkpointGapsMs: List<Long>
        get() = NudgeSchedule.Checkpoint.entries
            .map { it.hour }
            .sorted()
            .zipWithNext { earlier, later -> (later - earlier) * HOUR_MS }

    @Test
    fun `min gap never blocks a checkpoint that arrived on time`() {
        // Read from both files rather than restated here: a test carrying its own copy of the
        // numbers would keep passing while the two it is comparing drifted apart.
        val smallestGap = checkpointGapsMs.min()
        assertTrue(
            "minGapMs (${NudgeConfig.DEFAULT_MIN_GAP_MS}ms) must stay under the smallest checkpoint " +
                "gap (${smallestGap}ms), or a punctual delivery is suppressed by the previous one",
            NudgeConfig.DEFAULT_MIN_GAP_MS < smallestGap,
        )
    }

    @Test
    fun `the day holds more checkpoints than the cap allows, which is what makes slippage survivable`() {
        // The checkpoints are spread so a deferred delivery still lands inside a band; the cap is
        // what stops all of them being spent. If these ever became equal the spread would be
        // decorative — every checkpoint would send, and maxPerDay would bound nothing.
        assertTrue(NudgeSchedule.Checkpoint.entries.size > NudgeConfig().maxPerDay)
    }

    private companion object {
        const val HOUR_MS = 60L * 60L * 1_000L
    }
}
