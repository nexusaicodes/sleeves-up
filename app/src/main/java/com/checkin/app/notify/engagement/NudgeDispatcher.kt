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

/**
 * Assembles a snapshot, asks [NudgeEligibility] what to send, and posts the result.
 *
 * [runOnce] is the only way a nudge is ever posted — there is no bypass and no forced send, so the
 * copy a device shows is whatever eligibility and the install's own variant bucket produce.
 *
 * Every read here is a read-only observation of tracking state — this layer never writes to the
 * sessions table, and the decision itself stays in the pure rules.
 */
class NudgeDispatcher(
    private val strings: StringResolver,
    private val repository: CheckInRepository,
    private val install: EngagementInstallId,
    private val notifier: Notifier,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
) {

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /** Returns the nudge sent, or null when nothing was eligible or posting was refused. */
    suspend fun runOnce(): Nudge? {
        val snapshot = buildSnapshot()
        val nudge = NudgeEligibility.select(snapshot) ?: return null
        val sent = send(nudge, snapshot.nowMillis) ?: return null

        // Retire the day's earlier checkpoints, but only now that this one is actually up. They carry
        // distinct ids by design, so otherwise an evening nudge stacks under a morning one still in
        // the tray — two notifications saying the user hasn't checked in, which reads as a stuck loop
        // rather than as one message that came back. Clearing *before* the post would instead take a
        // still-actionable notification away on the pass where the post is refused.
        Nudge.entries.filter { it != sent }.forEach { notifier.cancel(it.notificationId) }
        return sent
    }

    private suspend fun send(nudge: Nudge, nowMillis: Long): Nudge? {
        val variantCount = NudgeCatalog.variants(nudge).size
        val variant = VariantAssigner.assign(install.installId(), nudge.name, variantCount)
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
