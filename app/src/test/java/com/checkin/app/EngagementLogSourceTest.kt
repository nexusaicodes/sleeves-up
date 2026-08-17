package com.checkin.app

import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The session reminder and the nudges share one table, and two of the log's questions must only ever
 * see nudge rows: the daily frequency cap, and "which notification earned this action".
 *
 * These are the failures that would otherwise ship silently — nothing crashes, a user simply stops
 * getting a nudge they opted into, or a nudge's conversion rate reports a check-in it never caused.
 * [FakeEngagementLog] applies the same `source` scoping as the Room queries, so a regression in
 * either implementation has to break one of these.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngagementLogSourceTest {

    private val hour = 60 * 60 * 1000L
    private val window = 2 * hour

    @Test
    fun `a session reminder does not count toward the nudge daily cap`() = runTest {
        val log = FakeEngagementLog()

        log.recordPresenceCheck(EngagementEventType.SHOWN, 1_000L)
        log.recordPresenceCheck(EngagementEventType.SHOWN, 2_000L)

        // maxPerDay is 1; if these counted, the day's real nudge would never be sent.
        assertEquals(0, log.shownNudgesSince(0L).size)

        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = 3_000L)
        assertEquals(1, log.shownNudgesSince(0L).size)
    }

    @Test
    fun `a session reminder does not absorb a check-in a nudge earned`() = runTest {
        val log = FakeEngagementLog()
        val nudgeAt = 10 * hour

        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = nudgeAt)
        // Fired after the nudge, so it is the most recent SHOWN row in the table.
        log.recordPresenceCheck(EngagementEventType.SHOWN, nudgeAt + 10 * 60 * 1000L)

        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
            log.recordConversionIfAttributable(nudgeAt + 20 * 60 * 1000L, window),
        )
    }

    /**
     * The end-to-end shape of the rejection rule: swipe the nudge away, then check in anyway an hour
     * later. The check-in is real, the credit is not — and a `CONVERTED` row here would report a
     * rejected notification as one that worked.
     */
    @Test
    fun `a nudge swiped away earns no conversion from a later check-in`() = runTest {
        val log = FakeEngagementLog()
        val nudgeAt = 10 * hour

        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = nudgeAt)
        log.record(
            Nudge.NOT_CHECKED_IN_MORNING,
            variant = 0,
            event = EngagementEventType.DISMISSED,
            atMillis = nudgeAt + 60_000L,
        )

        assertNull(log.recordConversionIfAttributable(nudgeAt + hour, window))
    }

    /** A reminder swiped away is not a nudge being rejected, and must not suppress its credit. */
    @Test
    fun `a dismissed session reminder does not suppress a nudge conversion`() = runTest {
        val log = FakeEngagementLog()
        val nudgeAt = 10 * hour

        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = nudgeAt)
        log.recordPresenceCheck(EngagementEventType.DISMISSED, nudgeAt + 60_000L)

        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
            log.recordConversionIfAttributable(nudgeAt + hour, window),
        )
    }

    @Test
    fun `a session reminder does not absorb a tap a nudge earned`() = runTest {
        val log = FakeEngagementLog()
        val nudgeAt = 10 * hour

        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = nudgeAt)
        log.recordPresenceCheck(EngagementEventType.SHOWN, nudgeAt + 5 * 60 * 1000L)

        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
            log.recordOpenedForLastShown(nudgeAt + 6 * 60 * 1000L, window),
        )
    }

    /** With no nudge in the window there is nothing to credit — a reminder is not a fallback. */
    @Test
    fun `a session reminder alone credits nothing`() = runTest {
        val log = FakeEngagementLog()

        log.recordPresenceCheck(EngagementEventType.SHOWN, 10 * hour)

        assertNull(log.recordConversionIfAttributable(10 * hour + 60_000L, window))
        assertNull(log.recordOpenedForLastShown(10 * hour + 60_000L, window))
    }

    /**
     * Service-lifecycle rows share the table too, and carry the same risk: a `STARTED` row landing
     * at the head of the log must not absorb a tap, and a revive must not eat the day's nudge slot.
     */
    @Test
    fun `service rows are invisible to the cap and to attribution`() = runTest {
        val log = FakeEngagementLog()
        val nudgeAt = 10 * hour

        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = nudgeAt)
        log.recordService(ServiceEventType.STARTED, nudgeAt + 60_000L, "1")
        log.recordService(ServiceEventType.REVIVED, nudgeAt + 120_000L, "app open")

        assertEquals(1, log.shownNudgesSince(0L).size)
        assertEquals(
            Nudge.NOT_CHECKED_IN_MORNING,
            log.recordOpenedForLastShown(nudgeAt + 5 * 60 * 1000L, window),
        )
    }

    /** A service row on its own is a breadcrumb, not a notification anyone could have acted on. */
    @Test
    fun `a service row alone credits nothing`() = runTest {
        val log = FakeEngagementLog()

        log.recordService(ServiceEventType.DEGRADED, 10 * hour, "startForeground refused")

        assertNull(log.recordConversionIfAttributable(10 * hour + 60_000L, window))
        assertNull(log.recordOpenedForLastShown(10 * hour + 60_000L, window))
    }

    /** The diagnostics card lists everything the app did, so `recent` stays unscoped. */
    @Test
    fun `presence events are still visible in the log`() = runTest {
        val log = FakeEngagementLog()

        log.recordPresenceCheck(EngagementEventType.SHOWN, 1_000L)
        log.record(Nudge.NOT_CHECKED_IN_MORNING, variant = 0, event = EngagementEventType.SHOWN, atMillis = 2_000L)

        val all = log.events.value
        assertEquals(2, all.size)
        val presence = all.single { it.source == EngagementSource.PRESENCE.name }
        assertEquals(PRESENCE_CHECK_KEY, presence.key)
        assertEquals(0, presence.variant)
    }
}
