package com.checkin.app

import com.checkin.app.ui.settings.ChannelState
import com.checkin.app.ui.settings.DebugSnapshot
import com.checkin.app.ui.settings.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the debug diagnostics card calls wrong. Each warning names a state the app renders as
 * entirely normal — a killed service still draws a running timer, an unarmed boundary looks
 * identical until it writes a multi-day duration — so the conditions are pinned here.
 */
class DebugSnapshotTest {

    private val now = 1_700_000_000_000L
    private val minute = 60L * 1_000L
    private val session = SessionState(id = 7L, startedAt = now - minute, dateKey = "2026-06-15")

    private fun snapshot(
        session: SessionState? = this.session,
        serviceRunning: Boolean = true,
        nextReminderAt: Long = now + minute,
        dayBoundaryAt: Long = now + minute,
        expectedDayBoundaryAt: Long? = now + minute,
        nextCheckpointAt: Long = now + minute,
        channels: List<ChannelState> = emptyList(),
    ) = DebugSnapshot(
        nowMs = now,
        session = session,
        serviceRunning = serviceRunning,
        nextReminderAt = nextReminderAt,
        dayBoundaryAt = dayBoundaryAt,
        remindersSent = 0,
        expectedDayBoundaryAt = expectedDayBoundaryAt,
        nextCheckpointAt = nextCheckpointAt,
        channels = channels,
    )

    @Test
    fun `a healthy session warns about nothing`() {
        assertEquals(emptyList<String>(), snapshot().warnings())
    }

    /** `START_STICKY` is best effort — the state `SessionWatchdog` exists to repair. */
    @Test
    fun `an open session with no service is reported`() {
        val warnings = snapshot(serviceRunning = false).warnings()

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("no service"))
    }

    /** A force stop or package replace cancels alarms while leaving the session open. */
    @Test
    fun `an unarmed day boundary is reported`() {
        val warnings = snapshot(dayBoundaryAt = 0L, expectedDayBoundaryAt = now + minute).warnings()

        assertTrue(warnings.any { it.contains("NOT armed") })
    }

    /** A past-due alarm is delivered immediately, so still-open well past it means dropped, not late. */
    @Test
    fun `a day boundary long past due with the session still open is reported`() {
        val warnings = snapshot(dayBoundaryAt = now - 10 * minute).warnings()

        assertTrue(warnings.any { it.contains("past due") })
    }

    /** Just-passed is the ordinary race between the boundary and the broadcast, not a fault. */
    @Test
    fun `a boundary that has only just passed is not reported`() {
        val justPassed = now - 1000L
        val warnings = snapshot(dayBoundaryAt = justPassed, expectedDayBoundaryAt = justPassed).warnings()

        assertEquals(emptyList<String>(), warnings)
    }

    /** The armed instant is stored at check-in, so a mid-session zone change leaves it on old midnight. */
    @Test
    fun `a boundary disagreeing with the session's date key is reported`() {
        val warnings = snapshot(
            dayBoundaryAt = now + minute,
            expectedDayBoundaryAt = now + 60 * minute,
        ).warnings()

        assertTrue(warnings.any { it.contains("date_key implies") })
    }

    /** Check-out cancels both alarms; either half failing strands them over a closed session. */
    @Test
    fun `alarms left armed with no session are reported`() {
        val warnings = snapshot(
            session = null,
            serviceRunning = false,
            expectedDayBoundaryAt = null,
        ).warnings()

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("no open session"))
    }

    /** A service with nothing behind it is an orphan notification the reconcile should have torn down. */
    @Test
    fun `a service running with no session is reported`() {
        val warnings = snapshot(
            session = null,
            serviceRunning = true,
            nextReminderAt = 0L,
            dayBoundaryAt = 0L,
            expectedDayBoundaryAt = null,
        ).warnings()

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("orphan notification"))
    }

    /** Nothing open and nothing armed is the resting state, not a fault. */
    @Test
    fun `a closed idle app warns about nothing`() {
        val warnings = snapshot(
            session = null,
            serviceRunning = false,
            nextReminderAt = 0L,
            dayBoundaryAt = 0L,
            expectedDayBoundaryAt = null,
        ).warnings()

        assertEquals(emptyList<String>(), warnings)
    }

    /** Which switch is off is the diagnostic; the permission and the channel are fixed elsewhere. */
    @Test
    fun `each blocked channel names the switch that blocked it`() {
        val importanceDefault = 3
        val channels = listOf(
            ChannelState("a", permissionGranted = false, appEnabled = true, importance = importanceDefault),
            ChannelState("b", permissionGranted = true, appEnabled = false, importance = importanceDefault),
            ChannelState("c", permissionGranted = true, appEnabled = true, importance = 0),
            ChannelState("d", permissionGranted = true, appEnabled = true, importance = null),
            ChannelState("e", permissionGranted = true, appEnabled = true, importance = importanceDefault),
        )

        assertEquals("POST_NOTIFICATIONS denied", channels[0].blocker())
        assertEquals("notifications off app-wide", channels[1].blocker())
        assertEquals("channel muted", channels[2].blocker())
        // A post to a channel that was never created is discarded, so a missing one is undeliverable.
        assertEquals("channel missing", channels[3].blocker())
        assertEquals(null, channels[4].blocker())

        // Four of the five reach the warnings; the deliverable one does not.
        assertEquals(4, snapshot(channels = channels).warnings().size)
    }

    /** The clipboard payload carries the warnings, not just the facts they were derived from. */
    @Test
    fun `the text report includes the warnings`() {
        val text = snapshot(serviceRunning = false).asText()

        assertTrue(text.contains("session    #7"))
        assertTrue(text.contains("! Open session with no service"))
    }
}
