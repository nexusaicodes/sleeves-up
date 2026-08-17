package com.checkin.app

import com.checkin.app.notify.EngagementRouting
import com.checkin.app.notify.EngagementTarget
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The receiver that calls this is Android-only, so a mis-route would otherwise be invisible until it
 * had already polluted the log — a session reminder resolved as a nudge is written through the nudge
 * entry point, where it counts against the daily cap and sits at the head of the attribution
 * queries, which is the exact interference the `source` column exists to prevent.
 */
class EngagementRoutingTest {

    @Test
    fun `a nudge dismissal resolves to the nudge and its variant`() {
        val target = EngagementRouting.resolve(
            source = EngagementSource.NUDGE.name,
            key = Nudge.NOT_CHECKED_IN_MORNING.name,
            variant = 1,
        )

        assertEquals(EngagementTarget.NudgeTarget(Nudge.NOT_CHECKED_IN_MORNING, 1), target)
    }

    @Test
    fun `a presence dismissal resolves to the presence target`() {
        val target = EngagementRouting.resolve(
            source = EngagementSource.PRESENCE.name,
            key = PRESENCE_CHECK_KEY,
            variant = 0,
        )

        assertEquals(EngagementTarget.PresenceTarget, target)
    }

    /** A nudge renamed or removed between the post and the swipe. Dropped, never guessed at. */
    @Test
    fun `an unknown nudge key resolves to nothing`() {
        assertNull(
            EngagementRouting.resolve(EngagementSource.NUDGE.name, "RETIRED_EXPERIMENT", variant = 0),
        )
    }

    /** A pending intent that survived an app upgrade could carry anything; none of it is trusted. */
    @Test
    fun `a malformed payload resolves to nothing`() {
        assertNull(EngagementRouting.resolve(source = null, key = null, variant = 0))
        assertNull(EngagementRouting.resolve("NOT_A_SOURCE", Nudge.NOT_CHECKED_IN_MORNING.name, 0))
        // Right source, wrong key: presence has exactly one, so anything else is a mismatch.
        assertNull(EngagementRouting.resolve(EngagementSource.PRESENCE.name, "SOMETHING_ELSE", 0))
    }

    /** Every nudge must be routable, or its dismissals vanish the day it is added. */
    @Test
    fun `every nudge resolves from its own name`() {
        Nudge.entries.forEach { nudge ->
            assertEquals(
                EngagementTarget.NudgeTarget(nudge, 0),
                EngagementRouting.resolve(EngagementSource.NUDGE.name, nudge.name, 0),
            )
        }
    }
}
