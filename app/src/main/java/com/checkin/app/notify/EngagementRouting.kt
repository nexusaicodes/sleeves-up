package com.checkin.app.notify

import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import com.checkin.app.notify.log.RoomEngagementLog

/**
 * Maps a notification tag's payload back to the notification it identifies.
 *
 * It lives in `notify/` rather than `engagement/` because a tag is an intent payload and the
 * session reminder rides the same machinery a nudge does — see the tree map in [Notifier] for how
 * the three packages divide up.
 *
 * Kept pure and separate from the Android-only classes that carry the payload — the dismiss receiver
 * and the Activity that handles a tap — which would otherwise be the one place a mis-route could
 * hide: a session reminder resolved as a nudge would be written through the nudge entry point, where
 * it would count against the daily cap and sit at the head of the attribution queries, exactly the
 * interference the `source` column exists to prevent.
 */
object EngagementRouting {

    /**
     * Null when the payload no longer names anything the app can act on: a malformed extra, or a
     * nudge that has since been renamed or removed. Dropping it matches how
     * [RoomEngagementLog]'s own name lookup already handles a retired experiment.
     */
    fun resolve(source: String?, key: String?, variant: Int): EngagementTarget? = when (source) {
        EngagementSource.PRESENCE.name ->
            if (key == PRESENCE_CHECK_KEY) EngagementTarget.PresenceTarget else null

        EngagementSource.NUDGE.name ->
            Nudge.entries.firstOrNull { it.name == key }
                ?.let { EngagementTarget.NudgeTarget(it, variant) }

        else -> null
    }
}
