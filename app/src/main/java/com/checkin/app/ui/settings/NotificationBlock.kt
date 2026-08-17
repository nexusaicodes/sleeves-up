package com.checkin.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.checkin.app.R
import com.checkin.app.notify.NotificationChannels

/**
 * How much of the app's notification surface the system is currently swallowing.
 *
 * Three switches silence a notification and only one of them is the runtime permission: the user can
 * grant it and still turn notifications off for the whole app, or set an individual channel to
 * "None". All three are checked — the latter two are the settings behind "I had everything enabled",
 * and a card that looked only at the permission would be blind to them.
 *
 * Every case carries copy, [NONE] included: [NotificationsCard] renders in all of them, so the
 * healthy state is a real state with something to say — where the switches are — rather than an
 * absence.
 */
internal enum class NotificationBlock(val titleRes: Int, val helpRes: Int) {
    NONE(R.string.settings_notifications_title, R.string.settings_notifications_help),
    ALL(R.string.settings_notifications_off, R.string.settings_notifications_off_help),
    CHANNELS(R.string.settings_notifications_partial, R.string.settings_notifications_partial_help),
    ;

    companion object {
        /**
         * The classification itself, kept pure and separate from the platform reads for the same
         * reason as [com.checkin.app.notify.NotificationDelivery]: the reading half is Android-only
         * and unreachable from a JVM-only suite, so the decision the card renders from would
         * otherwise be the one part of the path nothing exercises.
         *
         * [timerChannelImportance] is null when the channel does not exist. That counts as unblocked
         * rather than blocked — all three channels are created at startup, so null means something
         * odd rather than something the user chose, and this card must not cry wolf.
         */
        fun classify(
            permissionGranted: Boolean,
            appEnabled: Boolean,
            timerChannelImportance: Int?,
        ): NotificationBlock = when {
            !permissionGranted || !appEnabled -> ALL
            timerChannelImportance == NotificationManagerCompat.IMPORTANCE_NONE -> CHANNELS
            else -> NONE
        }
    }
}

/**
 * Reads the three switches off the platform and hands them to [NotificationBlock.classify].
 *
 * Only the one channel a session depends on is inspected. Muting the reminder or the check-in
 * reminders is a preference, not a fault: the reminder only ever asks, and the day-boundary close
 * ends a forgotten session with no notification involved. Their channels *are* their opt-outs —
 * there is no in-app switch for either — so warning about a muted one would be nagging the user
 * about the choice they just made, in the same breath as telling them where to make it.
 */
internal fun Context.notificationBlock(): NotificationBlock {
    val manager = NotificationManagerCompat.from(this)
    return NotificationBlock.classify(
        permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        appEnabled = manager.areNotificationsEnabled(),
        timerChannelImportance = manager.getNotificationChannelCompat(NotificationChannels.TIMER)?.importance,
    )
}
