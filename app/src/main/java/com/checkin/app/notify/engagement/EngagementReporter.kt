package com.checkin.app.notify.engagement

import com.checkin.app.notify.EngagementRouting
import com.checkin.app.notify.EngagementTarget
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource

/**
 * The one hook app code calls to report what the user did, so the engagement layer stays a listener
 * rather than something the check-in paths have to know the internals of.
 *
 * [onCheckedIn] exists because a check-in is reachable two ways — the Check-In screen and a nudge tap
 * — and both matter to the engagement layer identically. Wiring it only to the notification path
 * would credit none of the check-ins a nudge prompted indirectly, and would leave an acted-on nudge
 * sitting in the shade.
 */
interface EngagementReporter {

    /**
     * A nudge was tapped: retire it and record the open.
     *
     * [key] and [variant] come from the [com.checkin.app.notify.EngagementTag] the notification
     * carried on its tap intent, so the open lands against the notification actually tapped. A null
     * [key] falls back to whichever nudge was most recently shown — the case is a notification posted
     * by a previous release, whose tap intent predates the tag and outlives the update.
     */
    suspend fun onNudgeOpened(atMillis: Long, key: String? = null, variant: Int = 0)

    /** A session was opened, by any path: retire a now-stale nudge and credit it if attributable. */
    suspend fun onCheckedIn(atMillis: Long)
}

class DefaultEngagementReporter(
    private val notifier: Notifier,
    private val log: EngagementLog,
    private val conversionWindowMs: Long = CONVERSION_WINDOW_MS,
) : EngagementReporter {

    override suspend fun onNudgeOpened(atMillis: Long, key: String?, variant: Int) {
        // Nudges are posted without autoCancel, so that the only delete intent the platform delivers
        // is a real dismissal. Clearing a tapped one is therefore the app's job, and it happens here
        // rather than after the gate resolves: the notification has served its purpose the moment it
        // is tapped, whether or not the user goes on to complete the check-in.
        retirePostedNudges()

        // The source is fixed rather than read from the tap: only a nudge's launch extra reaches this
        // hook, and pinning it means a presence tag arriving here could never be logged as a nudge
        // open — the interference the `source` column exists to prevent, refused a second time.
        val tagged = EngagementRouting.resolve(EngagementSource.NUDGE.name, key, variant)
        if (tagged is EngagementTarget.NudgeTarget) {
            // The tag names the notification outright, so no time window is needed to identify it —
            // unlike the fallback, which can only infer identity from what was posted most recently
            // and so has to bound how far back it will look.
            log.record(tagged.nudge, tagged.variant, EngagementEventType.OPENED, atMillis)
        } else {
            log.recordOpenedForLastShown(atMillis, conversionWindowMs)
        }
    }

    override suspend fun onCheckedIn(atMillis: Long) {
        // A nudge asking for a check-in is stale the moment one happens. Left posted, tapping it
        // later puts the user through the full presence gate and then resolves to nothing, which
        // reads as a check-in that silently failed.
        retirePostedNudges()
        log.recordConversionIfAttributable(atMillis, conversionWindowMs)
    }

    /**
     * Clears every nudge kind rather than one id: each has its own, and this call site cannot tell
     * which is posted.
     */
    private fun retirePostedNudges() {
        Nudge.entries.forEach { notifier.cancel(it.notificationId) }
    }

    companion object {
        /**
         * How long after a nudge a check-in can still be credited to it. Long enough to cover "saw
         * it, acted a couple of hours later"; short enough that the next morning's unprompted
         * check-in isn't attributed to yesterday's notification.
         */
        const val CONVERSION_WINDOW_MS = 4 * 60 * 60 * 1000L
    }
}
