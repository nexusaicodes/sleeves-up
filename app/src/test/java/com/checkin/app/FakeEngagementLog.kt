package com.checkin.app

import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.log.AttributionRules
import com.checkin.app.notify.log.EngagementEvent
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.NudgeShowing
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for the engagement database, with the same attribution semantics. */
class FakeEngagementLog : EngagementLog {
    val events = MutableStateFlow<List<EngagementEvent>>(emptyList())
    var clearCount = 0
    var prunedBefore: Long? = null

    override suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long) {
        events.value = events.value + EngagementEvent(
            id = events.value.size + 1L,
            at = atMillis,
            key = nudge.name,
            variant = variant,
            event = event.name,
            source = EngagementSource.NUDGE.name,
        )
    }

    override suspend fun recordPresenceCheck(event: EngagementEventType, atMillis: Long) {
        events.value = events.value + EngagementEvent(
            id = events.value.size + 1L,
            at = atMillis,
            key = PRESENCE_CHECK_KEY,
            variant = 0,
            event = event.name,
            source = EngagementSource.PRESENCE.name,
        )
    }

    override suspend fun recordService(event: ServiceEventType, atMillis: Long, detail: String) {
        events.value = events.value + EngagementEvent(
            id = events.value.size + 1L,
            at = atMillis,
            key = detail,
            variant = 0,
            event = event.name,
            source = EngagementSource.SERVICE.name,
        )
    }

    // Mirrors the Room queries' `source` scoping. Without it the fake would answer the cap and
    // attribution questions differently from production, and a test could only prove the fake right.
    private fun nudgeEvents() = events.value.filter { it.source == EngagementSource.NUDGE.name }

    private fun lastShownWithin(atMillis: Long, windowMs: Long): EngagementEvent? =
        nudgeEvents().filter { it.event == EngagementEventType.SHOWN.name && it.at >= atMillis - windowMs }
            .maxByOrNull { it.at }

    override suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        // Shares AttributionRules with the Room implementation, so the fake can't drift on the
        // decision — only on storage.
        val latestConverted = nudgeEvents()
            .filter { it.event == EngagementEventType.CONVERTED.name }
            .maxOfOrNull { it.at }
        val latestDismissed = nudgeEvents()
            .filter { it.event == EngagementEventType.DISMISSED.name }
            .maxOfOrNull { it.at }
        if (!AttributionRules.canCredit(shown.at, atMillis, windowMs, latestConverted, latestDismissed)) return null
        val nudge = Nudge.entries.firstOrNull { it.name == shown.key } ?: return null
        record(nudge, shown.variant, EngagementEventType.CONVERTED, atMillis)
        return nudge
    }

    override suspend fun recordOpenedForLastShown(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val nudge = Nudge.entries.firstOrNull { it.name == shown.key } ?: return null
        record(nudge, shown.variant, EngagementEventType.OPENED, atMillis)
        return nudge
    }

    override suspend fun shownNudgesSince(since: Long): List<NudgeShowing> =
        nudgeEvents().filter { it.event == EngagementEventType.SHOWN.name && it.at >= since }
            .sortedBy { it.at }
            .map { NudgeShowing(it.key, it.at) }

    override fun recent(limit: Int): Flow<List<EngagementEvent>> =
        events.map { list -> list.sortedByDescending { it.at }.take(limit) }

    override suspend fun clear() {
        clearCount++
        events.value = emptyList()
    }

    override suspend fun prune(before: Long) {
        prunedBefore = before
    }
}
