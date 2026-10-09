package com.checkin.app

import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeCatalog
import com.checkin.app.notify.nudge.NudgeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            assertTrue("$nudge has no copy registered", runCatching { NudgeCatalog.copyFor(nudge) }.isSuccess)
        }
    }

    /**
     * Distinct copy per nudge. A second send in a day arriving as the same string reads as a stuck
     * loop rather than as a message that came back, which is the whole reason there is one wording
     * per checkpoint instead of one shared wording sent twice.
     */
    @Test
    fun `no two nudges share a resource pair`() {
        val all = Nudge.entries.map { NudgeCatalog.copyFor(it) }
        assertEquals(all.size, all.toSet().size)
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

    /** A retired id reused would let a new nudge replace a stale one in an updated install's tray. */
    @Test
    fun `no nudge posts under a retired id`() {
        val ids = Nudge.entries.map { it.notificationId }.toSet()
        assertTrue(ids.none { it in NotificationIds.RETIRED_NUDGES })
    }

    /** A ledger key resolves whether it was written by this build or an earlier one. */
    @Test
    fun `stored keys resolve, legacy ones included`() {
        Nudge.entries.forEach { assertEquals(it, Nudge.fromStoredKey(it.name)) }
        assertEquals(Nudge.NOT_CHECKED_IN_FIRST, Nudge.fromStoredKey("NOT_CHECKED_IN_MORNING"))
        assertEquals(Nudge.NOT_CHECKED_IN_FOLLOW_UP, Nudge.fromStoredKey("NOT_CHECKED_IN_AFTERNOON"))
        assertNull(Nudge.fromStoredKey("NOT_CHECKED_IN_EVENING"))
    }
}
