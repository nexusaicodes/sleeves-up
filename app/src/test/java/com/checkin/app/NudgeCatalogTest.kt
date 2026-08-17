package com.checkin.app

import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.notify.engagement.NudgeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog `error()`s on an unregistered nudge, so a [Nudge] added without copy compiles, passes
 * every other test, and then throws at send time — inside a broadcast receiver, on a device, for a
 * notification nobody was watching for. These make that a failure at build time instead.
 */
class NudgeCatalogTest {

    @Test
    fun `every nudge has copy`() {
        Nudge.entries.forEach { nudge ->
            assertTrue("$nudge has no copy registered", NudgeCatalog.variants(nudge).isNotEmpty())
        }
    }

    /**
     * Variants are bucketed per install against `variants(nudge).size`, so an uneven count would give
     * the checkpoints different split ratios and make their conversion rates incomparable — the one
     * thing the variant column exists to measure.
     */
    @Test
    fun `every nudge offers the same number of variants`() {
        val counts = Nudge.entries.map { NudgeCatalog.variants(it).size }.distinct()
        assertEquals("variant counts differ across nudges: $counts", 1, counts.size)
    }

    /** Distinct copy per variant and per nudge — a duplicate would silently halve an experiment. */
    @Test
    fun `no two variants share a resource pair`() {
        val all = Nudge.entries.flatMap { NudgeCatalog.variants(it) }
        assertEquals(all.size, all.toSet().size)
    }

    /** Wrapping, so a stale or forced index can never index past the list. */
    @Test
    fun `an out of range variant index wraps`() {
        val nudge = Nudge.entries.first()
        val count = NudgeCatalog.variants(nudge).size

        assertEquals(NudgeCatalog.variant(nudge, 0), NudgeCatalog.variant(nudge, count))
        assertEquals(NudgeCatalog.variant(nudge, count - 1), NudgeCatalog.variant(nudge, -1))
    }

    /** One nudge per checkpoint, so no band can resolve to a nudge that does not exist. */
    @Test
    fun `every checkpoint has exactly one nudge`() {
        assertEquals(
            NudgeSchedule.Checkpoint.entries.toSet(),
            Nudge.entries.map { it.checkpoint }.toSet(),
        )
        assertEquals(NudgeSchedule.Checkpoint.entries.size, Nudge.entries.size)
    }

    /** Ids are dedicated constants; a collision would make one nudge replace another in the tray. */
    @Test
    fun `no two nudges share a notification id`() {
        val ids = Nudge.entries.map { it.notificationId }
        assertEquals(ids.size, ids.toSet().size)
    }
}
