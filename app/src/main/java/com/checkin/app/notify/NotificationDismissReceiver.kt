package com.checkin.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.log.EngagementEventType
import kotlinx.coroutines.launch

/**
 * Records that the user swiped a notification away — the only source of
 * [EngagementEventType.DISMISSED], and so the only thing that tells "hasn't acted on it yet" apart
 * from "actively rejected it".
 *
 * Only genuine dismissals arrive here. The platform delivers no delete intent for an app-initiated
 * `cancel()`, and nothing the app posts is auto-cancelling (see [NotificationSpec.tag]), so a tap can
 * never masquerade as a rejection.
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val target = EngagementRouting.resolve(
            source = intent.getStringExtra(EXTRA_SOURCE),
            key = intent.getStringExtra(EXTRA_KEY),
            variant = intent.getIntExtra(EXTRA_VARIANT, 0),
        ) ?: return

        val container = (context.applicationContext as? CheckInApplication)?.container ?: return

        // The DB write outlives onReceive, so hold the broadcast open until it lands. The work runs
        // on the app-wide scope rather than a receiver-scoped one, which would be cancelled the
        // moment this method returns.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val at = container.timeSource.nowMillis()
                when (target) {
                    is EngagementTarget.NudgeTarget -> container.engagementLog.record(
                        target.nudge,
                        target.variant,
                        EngagementEventType.DISMISSED,
                        at,
                    )

                    EngagementTarget.PresenceTarget ->
                        container.engagementLog.recordSessionReminder(EngagementEventType.DISMISSED, at)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        // Frozen, and deliberately not shared with the tap intent's keys: a notification posted by an
        // earlier release survives an update still holding the `PendingIntent` it was built with, so
        // renaming these silently stops recording its dismissal.
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_VARIANT = "variant"

        /** Request code is the notification id, so two notifications never share a delete intent. */
        fun pendingIntent(context: Context, notificationId: Int, tag: EngagementTag): PendingIntent {
            val intent = Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(EXTRA_SOURCE, tag.source.name)
                putExtra(EXTRA_KEY, tag.key)
                putExtra(EXTRA_VARIANT, tag.variant)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
