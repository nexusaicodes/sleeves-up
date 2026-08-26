package com.checkin.app.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Seam over the platform notification manager so the engagement layer can be unit-tested without
 * Android, and so posting is refused rather than thrown when notifications aren't permitted.
 *
 * ### Reading the `notify` tree
 *
 * Three packages, split by what a file is *for* rather than by what it is named — several
 * `Engagement*` types sit outside `engagement/`, and that is deliberate:
 *
 * - **`notify/`** — the plumbing every notification shares, whoever sends it: how one is described
 *   ([NotificationSpec]), built ([NotificationFactory]), posted (this file), and whether posting is
 *   even possible ([NotificationDelivery], pure). Its ids and request codes are in
 *   [NotificationIds]. It also holds what rides a notification's intents — [EngagementTag] and
 *   [LaunchExtras] outbound, [EngagementRouting]/[EngagementTarget] decoding inbound — which is why
 *   those live here and not in `engagement/`: they are intent payloads, and the session reminder
 *   uses the same machinery a nudge does.
 * - **`notify/engagement/`** — deciding *whether and when* to send a nudge, and sending it. The
 *   whole decision surface is one pure function over a plain value object; the rest is copy,
 *   scheduling and the alarm that wakes it.
 * - **`notify/log/`** — the separate analytics database and the pure rules read off it. Nothing
 *   here posts anything.
 *
 * `notify/` is the shared base, but it is not a leaf: it decodes *into* the other two, so
 * [EngagementRouting] and [EngagementTarget] import from both `engagement/` and `log/`, while
 * [Nudge] imports [NotificationIds] back out of here. Do not read the layering as a one-way arrow.
 * The invariant that actually holds is the outer one: **nothing under `notify/` writes to
 * `sessions`.** It may read tracking state to decide what to say, and never writes it back.
 */
interface Notifier {
    /**
     * Posts [spec], returning false when it could not be displayed.
     *
     * The return value is load-bearing, not advisory: the session reminder decides whether to
     * advance its alert ladder on the strength of it, and the engagement log records a `SHOWN` from
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
