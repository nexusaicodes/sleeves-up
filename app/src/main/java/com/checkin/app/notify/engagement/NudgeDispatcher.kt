package com.checkin.app.notify.engagement

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.EngagementTag
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.service.CheckInService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The subset the Settings debug harness needs, so the ViewModel doesn't depend on a Context. */
interface NudgeTrigger {
    suspend fun runOnce(): Nudge?

    /**
     * Bypasses eligibility to post [nudge] now. A non-null [variant] overrides the install's own
     * bucket, which the harness needs because bucketing is deterministic per install — without it,
     * every other variant's copy is unreachable on a given device.
     */
    suspend fun forceSend(nudge: Nudge, variant: Int? = null): Nudge?
}

/**
 * Assembles a snapshot, asks [NudgeEligibility] what to send, and posts the result.
 *
 * Every read here is a read-only observation of tracking state — this layer never writes to the
 * sessions table, and the decision itself stays in the pure rules.
 */
class NudgeDispatcher(
    private val strings: StringResolver,
    private val repository: CheckInRepository,
    private val install: EngagementInstall,
    private val notifier: Notifier,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
) : NudgeTrigger {

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /** Returns the nudge sent, or null when nothing was eligible or posting was refused. */
    override suspend fun runOnce(): Nudge? {
        val snapshot = buildSnapshot()
        val nudge = NudgeEligibility.select(snapshot) ?: return null
        val sent = send(nudge, snapshot.nowMillis, variantOverride = null) ?: return null

        // Retire the day's earlier checkpoints, but only now that this one is actually up. They carry
        // distinct ids by design, so otherwise an evening nudge stacks under a morning one still in
        // the tray — two notifications saying the user hasn't checked in, which reads as a stuck loop
        // rather than as one message that came back. Clearing *before* the post would instead take a
        // still-actionable notification away on the pass where the post is refused.
        //
        // Scheduling belongs on this path and not inside `send`: the debug harness posts through the
        // same writer, and a force-sent nudge must add to the tray rather than clear a genuine one
        // the user has not answered yet.
        Nudge.entries.filter { it != sent }.forEach { notifier.cancel(it.notificationId) }
        return sent
    }

    override suspend fun forceSend(nudge: Nudge, variant: Int?): Nudge? =
        send(nudge, timeSource.nowMillis(), variantOverride = variant)

    private suspend fun send(nudge: Nudge, nowMillis: Long, variantOverride: Int?): Nudge? {
        val variantCount = NudgeCatalog.variants(nudge).size
        val variant = variantOverride?.mod(variantCount)
            ?: VariantAssigner.assign(install.installId(), nudge.name, variantCount)
        val copy = NudgeCatalog.variant(nudge, variant)

        val posted = notifier.show(
            NotificationSpec(
                id = nudge.notificationId,
                channelId = NotificationChannels.ENGAGEMENT,
                title = strings.get(copy.titleRes),
                body = strings.get(copy.bodyRes),
                launchExtra = CheckInService.EXTRA_CHECK_IN,
                // Swiping a nudge away is the clearest signal it isn't wanted, and the only one the
                // log can't infer from the absence of a check-in.
                tag = EngagementTag(EngagementSource.NUDGE, nudge.name, variant),
            ),
        )
        // Notifications can be refused (permission revoked). Logging a SHOWN we never showed would
        // put an un-convertible event in the denominator and understate every conversion rate.
        if (!posted) return null

        log.record(nudge, variant, EngagementEventType.SHOWN, nowMillis)
        return nudge
    }

    private suspend fun buildSnapshot(): NudgeSnapshot {
        val now = timeSource.nowMillis()
        val today = timeSource.today()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        val todaySessions = repository.getSessionsByDate(today.format(dateFormatter))
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Read from the log rather than a prefs tally, so the frequency rules survive a prefs wipe
        // and can never drift out of step with what was actually sent. One query answers all three.
        val shown = log.shownNudgesSince(startOfDay)

        return NudgeSnapshot(
            nowMillis = now,
            hourOfDay = hour,
            // Deliberately the day's rows and not `getActiveSession()`: a session still open from an
            // earlier day is a lost day-boundary close, not a user who is present. See the field.
            hasCheckedInToday = todaySessions.isNotEmpty(),
            alreadySentToday = shown.mapNotNullTo(mutableSetOf()) { showing ->
                Nudge.entries.firstOrNull { it.name == showing.key }
            },
            shownToday = shown.size,
            lastShownAtMs = shown.maxOfOrNull { it.atMillis },
        )
    }
}
