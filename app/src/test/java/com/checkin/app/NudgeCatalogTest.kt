package com.checkin.app

import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeCatalog
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

    /** Slots are fixed numbers; two nudges on one would replace each other in a habit's tray. */
    @Test
    fun `no two nudges share a slot`() {
        val slots = Nudge.entries.map { it.slot }
        assertEquals(slots.size, slots.toSet().size)
    }

    /** A retired id reused would let a new nudge replace a stale one in an updated install's tray. */
    @Test
    fun `no nudge posts under a retired id`() {
        val ids = (1L..100L).flatMap { habit -> Nudge.entries.map { it.notificationIdFor(habit) } }
        assertTrue(ids.none { it in NotificationIds.RETIRED_NUDGES })
    }

    /**
     * A ledger key resolves whether this build wrote it or v3.0 did. Every one of v3.0's three keys
     * stands for today's one nudge, since each was the single record's "not checked in" reminder.
     */
    @Test
    fun `stored keys resolve, legacy ones included`() {
        Nudge.entries.forEach { assertEquals(it, Nudge.fromStoredKey(it.name)) }
        listOf("NOT_CHECKED_IN_MORNING", "NOT_CHECKED_IN_AFTERNOON", "NOT_CHECKED_IN_EVENING").forEach {
            assertEquals(it, Nudge.NOT_CHECKED_IN, Nudge.fromStoredKey(it))
        }
        assertNull(Nudge.fromStoredKey("SOMETHING_ELSE"))
    }
}
