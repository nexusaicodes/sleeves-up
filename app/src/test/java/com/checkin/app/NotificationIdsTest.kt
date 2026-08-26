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

    @Test
    fun `no two notifications share an id`() {
        val ids = listOf(NotificationIds.TIMER, NotificationIds.SESSION_REMINDER) +
            Nudge.entries.map { it.notificationId }
        assertEquals(ids.size, ids.toSet().size)
    }
}
