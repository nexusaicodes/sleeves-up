package com.checkin.app

import com.checkin.app.notify.NotificationDelivery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard that decides whether a post reaches anyone. Everything downstream treats a `true` here as
 * proof the user was asked something: the nudge dispatcher spends a slot in the daily cap, and the
 * session reminder marks its alert spent. Each way the platform drops a notification without saying
 * so is pinned below.
 */
class NotificationDeliveryTest {

    private val default = 3 // NotificationManager.IMPORTANCE_DEFAULT

    @Test
    fun `a granted permission and a live channel deliver`() {
        assertTrue(
            NotificationDelivery.canDeliver(
                permissionGranted = true,
                appEnabled = true,
                channelImportance = default,
            ),
        )
    }

    @Test
    fun `a revoked permission does not deliver`() {
        assertFalse(
            NotificationDelivery.canDeliver(
                permissionGranted = false,
                appEnabled = true,
                channelImportance = default,
            ),
        )
    }

    @Test
    fun `notifications switched off for the app do not deliver`() {
        assertFalse(
            NotificationDelivery.canDeliver(
                permissionGranted = true,
                appEnabled = false,
                channelImportance = default,
            ),
        )
    }

    /**
     * The case the permission check alone misses: the user leaves POST_NOTIFICATIONS granted and
     * blocks one channel. `notify` reports nothing, so without this the app would spend the
     * reminder's one alert on a message nobody saw, and record a send for a nudge nobody was shown.
     */
    @Test
    fun `a blocked channel does not deliver`() {
        assertFalse(
            NotificationDelivery.canDeliver(
                permissionGranted = true,
                appEnabled = true,
                channelImportance = NotificationDelivery.IMPORTANCE_NONE,
            ),
        )
    }

    /** Channels are created at startup, so a missing one means something is already wrong. */
    @Test
    fun `an unknown channel does not deliver`() {
        assertFalse(
            NotificationDelivery.canDeliver(
                permissionGranted = true,
                appEnabled = true,
                channelImportance = null,
            ),
        )
    }
}
