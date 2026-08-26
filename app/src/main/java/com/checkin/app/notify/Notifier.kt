package com.checkin.app.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Seam over the platform notification manager so the nudge layer can be unit-tested without Android,
 * and so posting is refused rather than thrown when notifications aren't permitted.
 *
 * ### Reading the `notify` tree
 *
 * Two packages, split by what a file is *for*:
 *
 * - **`notify/`** — the plumbing every notification shares, whoever sends it: how one is described
 *   ([NotificationSpec]), built ([NotificationFactory]), posted (this file), and whether posting is
 *   even possible ([NotificationDelivery], pure). Its ids and request codes are in
 *   [NotificationIds], and [LaunchExtras] is what rides a notification's tap intent — it lives here
 *   rather than in `nudge/` because the session reminder uses the same machinery a nudge does.
 * - **`notify/nudge/`** — deciding *whether and when* to send a nudge, sending it, and the
 *   ledger of what was already sent today. The whole decision surface is one pure function over a
 *   plain value object; the rest is copy, scheduling and the alarm that wakes it.
 *
 * There was a third package, `notify/log/`, holding a general engagement log — opens, dismissals,
 * conversions, per-variant buckets, service lifecycle rows. Nothing ever read any of it back, and it
 * is gone; what survives is [com.checkin.app.notify.nudge.NudgeSendLog], which exists solely
 * because the frequency rules must know what was already sent today.
 *
 * `notify/` is the shared base but not a leaf — [Nudge] imports [NotificationIds] back out of here —
 * so do not read the layering as a one-way arrow. The invariant that actually holds is the outer
 * one: **nothing under `notify/` writes to `sessions`.** It may read tracking state to decide what
 * to say, and never writes it back.
 */
interface Notifier {
    /**
     * Posts [spec], returning false when it could not be displayed.
     *
     * The return value is load-bearing, not advisory: the session reminder decides whether to
     * advance its alert ladder on the strength of it, and the send ledger records the send from
     * it, so "true" has to mean the notification is genuinely on the shade.
     */
    fun show(spec: NotificationSpec): Boolean
    fun cancel(id: Int)
}

class AndroidNotifier(
    private val context: Context,
    private val factory: NotificationFactory = NotificationFactory(context),
) : Notifier {

    override fun show(spec: NotificationSpec): Boolean {
        if (!canPost(spec.channelId)) return false

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.notify(spec.id, factory.build(spec))
        return true
    }

    /**
     * Reads the three switches that can silence a post to [channelId] and hands them to
     * [NotificationDelivery], which owns the decision itself and is unit-tested for each of them.
     */
    private fun canPost(channelId: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return NotificationDelivery.canDeliver(
            permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            appEnabled = manager.areNotificationsEnabled(),
            channelImportance = manager.getNotificationChannelCompat(channelId)?.importance,
        )
    }

    override fun cancel(id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
