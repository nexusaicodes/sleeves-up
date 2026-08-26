package com.checkin.app.notify

import com.checkin.app.notify.engagement.Nudge

/**
 * What a notification's tag turned out to name — the decoded counterpart of [EngagementTag].
 *
 * [EngagementTag] is what gets written onto an intent; this is what comes back off one, and
 * [EngagementRouting] is the decode between them. Keeping the three names straight matters, because
 * resolving a session reminder as a nudge would write it through the nudge entry point, where it
 * would count against the daily cap and sit at the head of the attribution queries.
 */
sealed interface EngagementTarget {
    data class NudgeTarget(val nudge: Nudge, val variant: Int) : EngagementTarget
    data object PresenceTarget : EngagementTarget
}
