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
import com.checkin.app.service.SessionSchedule
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
        return send(nudge, snapshot.nowMillis, variantOverride = null)
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

        // Retire the day's other checkpoints, but only now that this one is actually up. They carry
        // distinct ids by design, so otherwise an evening nudge stacks under a morning one still in
        // the tray — two notifications saying the user hasn't checked in, which reads as a stuck loop
        // rather than as one message that came back. Clearing *before* the post would instead take a
        // still-actionable notification away on the pass where the post is refused.
        Nudge.entries.filter { it != nudge }.forEach { notifier.cancel(it.notificationId) }

        log.record(nudge, variant, EngagementEventType.SHOWN, nowMillis)
        return nudge
    }

    private suspend fun buildSnapshot(): EngagementSnapshot {
        val now = timeSource.nowMillis()
        val today = timeSource.today()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        val active = repository.getActiveSession()
        val todaySessions = repository.getSessionsByDate(today.format(dateFormatter))
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return EngagementSnapshot(
            nowMillis = now,
            hourOfDay = hour,
            isCheckedIn = active != null,
            // Derived from the session's own date_key through the same pure helper the day-boundary
            // close uses, so "overdue" here means exactly the instant that close was meant to happen.
            openSessionOverdue = active
                ?.let { SessionSchedule.dayBoundaryOf(it.dateKey, ZoneId.systemDefault()) }
                ?.let { now >= it } == true,
            hasCheckedInToday = todaySessions.isNotEmpty(),
            // Counted from the log rather than a prefs tally, so the cap survives a prefs wipe and
            // can never drift out of step with what was actually sent.
            shownToday = log.shownCountSince(startOfDay),
        )
    }
}
