package com.checkin.app.notify.engagement

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkin.app.CheckInApplication
import java.util.concurrent.TimeUnit

/**
 * The periodic backstop, and the home of the housekeeping that has to happen somewhere.
 *
 * **It is no longer what delivers a nudge** — [NudgeAlarms] is. Relying on this pass alone is the bug
 * that made a real install go silent for days: an app the user is not opening drops through Android's
 * standby buckets until periodic work runs roughly once a day, settling at a consistent hour, so a
 * trigger asking "is it past 10am" is asked at 5am forever and the answer never changes. The pass
 * survives because it is the only place `reviveIfNeeded` and `prune` can hang, and because a second
 * chance at the day's nudge costs nothing.
 *
 * "Costs nothing" is [NudgeSnapshot.alreadySentToday] doing that work, not the daily cap. A cap of
 * two lets a pass landing inside a band the alarm has already fired in send the *same* nudge again —
 * identical copy, same id, re-alerting on a high-importance channel, and the day's remaining slot
 * gone. The per-checkpoint rule is what makes a redundant pass genuinely free.
 */
class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CheckInApplication).container
        return runCatching {
            // Piggy-backed on the pass that already runs: a session whose service was killed gets a
            // chance to recover roughly hourly rather than waiting for the user to open the app. The
            // start may be refused here — background foreground-service starts are restricted, and a
            // worker is not among the exemptions — which is why this is the last of the three revive
            // points rather than the only one. A refusal is logged, not thrown.
            container.sessionWatchdog.reviveIfNeeded(source = "hourly pass")
            // Re-arm before dispatching: a checkpoint alarm lost to a force stop is repaired here
            // even on a pass that then finds nothing eligible to send.
            //
            // Guarded on its own, like every other arming site: `setAndAllowWhileIdle` throws past
            // the platform's 500-alarm cap, and sharing the outer catch would let that one throw skip
            // the dispatch and the prune — the two jobs this pass exists for.
            runCatching { container.nudgeAlarms.armNext(container.timeSource.nowMillis()) }
            container.nudgeDispatcher.runOnce()
            container.engagementLog.prune(
                container.timeSource.nowMillis() - RETENTION_MS,
            )
        }.fold(
            onSuccess = { Result.success() },
            // A failed pass still reports success, because `Result.failure()` is terminal for
            // periodic work — one transient throw would cancel every future pass and silence nudges
            // until the next cold start. Retrying is pointless anyway: the next pass is an hour away
            // and re-evaluates against fresher state.
            onFailure = { Result.success() },
        )
    }

    companion object {
        private const val WORK_NAME = "engagement_nudge_pass"
        private const val INTERVAL_MINUTES = 60L
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(180)

        /**
         * Enqueued unconditionally at startup with [ExistingPeriodicWorkPolicy.KEEP], so it survives
         * reboots and app updates without resetting its schedule on every launch. The pass is cheap,
         * and exits without posting when nudges are switched off.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
