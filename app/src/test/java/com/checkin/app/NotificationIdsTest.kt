package com.checkin.app

import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.nudge.Nudge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Posting twice under one id replaces rather than adds, and the senders never see each other: the
 * service, the session reminder and every nudge would each look correct in isolation while one
 * silently took another's place in the tray.
 */
class NotificationIdsTest {

    /** Across habits too: a hundred habits' worth of blocks, every slot, plus every fixed and retired id. */
    @Test
    fun `no two notifications share an id`() {
        val habitIds = (1L..100L).flatMap { habit ->
            (0 until NotificationIds.HABIT_NUDGE_SLOTS).map { NotificationIds.habitNudge(habit, it) }
        }
        val ids = listOf(NotificationIds.TIMER, NotificationIds.SESSION_REMINDER) +
            NotificationIds.RETIRED_NUDGES + habitIds
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a habit's nudge id is its block plus the nudge's slot`() {
        assertEquals(100_004, Nudge.NOT_CHECKED_IN.notificationIdFor(1L))
        assertEquals(100_008, Nudge.NOT_CHECKED_IN.notificationIdFor(2L))
    }

    /** A slot past the block would land in the next habit's ids. */
    @Test(expected = IllegalArgumentException::class)
    fun `a slot outside a habit's block is refused`() {
        NotificationIds.habitNudge(1L, NotificationIds.HABIT_NUDGE_SLOTS)
    }

    /** Every id an earlier build posted a nudge under stays retired, so a stale one still clears. */
    @Test
    fun `v3_0's nudge ids stay retired`() {
        assertEquals(listOf(10, 11, 12), NotificationIds.RETIRED_NUDGES)
    }
}
