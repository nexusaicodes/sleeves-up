package com.checkin.app.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.checkin.app.MainActivity
import com.checkin.app.R

/**
 * Turns a [NotificationSpec] into a platform [Notification] — the one place that decides how any of
 * the app's notifications look.
 *
 * Split from [Notifier] because a foreground service must hand `startForeground` an already-built
 * notification rather than ask for one to be posted, and that call has to succeed whether or not
 * POST_NOTIFICATIONS is granted, so it cannot go through the guarded path. Building and posting are
 * therefore two steps.
 */
class NotificationFactory(private val context: Context) {

    fun build(spec: NotificationSpec): Notification {
        val builder = NotificationCompat.Builder(context, spec.channelId)
            // The brand mark is the small icon for every notification the app sends, never platform
            // stock. Status-bar icons keep only their alpha, so this one is white-on-transparent.
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setContentIntent(launchIntent(contentRequestCode(spec.id), spec.launchExtra, spec.tag))
            .setOngoing(spec.ongoing)
            .setSilent(spec.silent)
            // Always false. A tapped notification is cancelled by whoever handles the tap, so the
            // only delete intent the platform ever delivers is a real user dismissal.
            .setAutoCancel(false)

        // An ongoing notification is a live status line, not a message: expanding it would only
        // repeat the same short text behind a chevron. Everything else carries a sentence that can
        // outrun one line.
        if (!spec.ongoing) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
        }

        // Hands the ticking to the platform. `setUsesChronometer` is meaningless without a `when`,
        // and `setShowWhen` is explicit because an ongoing notification does not show one by default.
        spec.chronometerBase?.let { base ->
            builder.setWhen(base)
                .setShowWhen(true)
                .setUsesChronometer(true)
        }

        spec.actions.forEachIndexed { index, action ->
            builder.addAction(
                action.iconRes,
                action.label,
                launchIntent(actionRequestCode(spec.id, index), action.launchExtra, spec.tag),
            )
        }

        spec.tag?.let {
            builder.setDeleteIntent(NotificationDismissReceiver.pendingIntent(context, spec.id, it))
        }

        return builder.build()
    }

    /**
     * [PendingIntent] equality ignores extras, so two intents that differ only by what they carry
     * collide under `FLAG_UPDATE_CURRENT` and the second silently rewrites the first — a "Check Out"
     * action would end up behind the notification's own body tap. Distinct request codes are what
     * keeps them apart.
     */
    private fun launchIntent(requestCode: Int, launchExtra: String?, tag: EngagementTag?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            launchExtra?.let { putExtra(it, true) }
            // Identity travels with the tap, so an open is attributed to the notification actually
            // tapped rather than to whichever the log happens to hold as most recently shown.
            tag?.let {
                putExtra(EngagementTag.EXTRA_KEY, it.key)
                putExtra(EngagementTag.EXTRA_VARIANT, it.variant)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentRequestCode(notificationId: Int): Int = CONTENT_REQUEST_BASE + notificationId

    private fun actionRequestCode(notificationId: Int, index: Int): Int =
        ACTION_REQUEST_BASE + notificationId * MAX_ACTIONS + index

    private companion object {
        /**
         * Content codes are offset off zero rather than being the notification id itself: request
         * codes are a namespace shared with *previously installed* versions of the app, whose
         * notifications survive an update, and earlier releases used the low numbers that are also
         * notification ids here. Since [PendingIntent] equality ignores extras, posting under a bare
         * id rewrites one of those still-posted notifications' tap target to the wrong screen.
         */
        const val CONTENT_REQUEST_BASE = 1_000

        /**
         * Clear of the content codes, with room for [MAX_ACTIONS] per notification. Both bands, and
         * the two the alarms use, are listed together in [NotificationIds].
         */
        const val ACTION_REQUEST_BASE = 10_000

        /** Actions per notification the request-code scheme has room for. Android shows three. */
        const val MAX_ACTIONS = 8
    }
}
