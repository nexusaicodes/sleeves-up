package com.checkin.app.notify.nudge

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
 * **It does not deliver nudges** — [NudgeAlarms] does, and putting the trigger back here is the bug
 * documented there. The pass survives because it is the only place `reviveIfNeeded` and `prune` can
 * hang, and because a second chance at the day's nudge costs nothing.
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
            // points rather than the only one. It reports a refusal as `false` and never throws.
            container.sessionWatchdog.reviveIfNeeded()
            // Re-arm before dispatching: a checkpoint alarm lost to a force stop is repaired here
            // even on a pass that then finds nothing eligible to send.
            //
            // Guarded on its own, like every other arming site: `setAndAllowWhileIdle` throws past
            // the platform's 500-alarm cap, and sharing the outer catch would let that one throw skip
            // the dispatch and the prune — the two jobs this pass exists for.
            runCatching { container.nudgeAlarms.armNext(container.timeSource.nowMillis()) }
            container.nudgeDispatcher.runOnce()
            container.nudgeSendLog.prune(
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
        private const val WORK_NAME = "nudge_pass"

        /**
         * The name this work was enqueued under before `notify/engagement/` became `notify/nudge/`.
         *
         * A unique name is WorkManager's own persisted key, so renaming does not rename the existing
         * work — it enqueues new work and leaves the old `WorkSpec` standing. Because the worker
         * class moved packages in the same change, that orphan names a class that no longer
         * resolves, so WorkManager cannot instantiate it and fails it on a schedule forever. Had only
         * the name changed, it would have been worse: [ExistingPeriodicWorkPolicy.KEEP] would leave
         * two live hourly passes, doubling every revive, re-arm and prune. Cancelling before the
         * enqueue is what makes the rename a rename either way — the exact analogue of deleting a
         * retired notification channel.
         *
         * Verified on device: after the upgrade `WorkName` joins one `WorkSpec` in state 5
         * (CANCELLED) under this name, and one in state 0 (ENQUEUED) under [WORK_NAME].
         *
         * Keep this call for as long as any install may still hold the old work — nothing reports
         * back that it is gone. Append rather than replace if the name ever changes again.
         */
        private const val RETIRED_WORK_NAME = "engagement_nudge_pass"
        private const val INTERVAL_MINUTES = 60L

        /**
         * Nothing reads a send older than the start of today — [NudgeDispatcher] queries from
         * midnight and no other reader exists. The week is slack for a clock moved backwards, not a
         * decision to keep records: this ledger is scheduler state, and stale rows are just litter.
         */
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(7)

        /**
         * Enqueued unconditionally at startup with [ExistingPeriodicWorkPolicy.KEEP], so it survives
         * reboots and app updates without resetting its schedule on every launch. The pass is cheap,
         * and a post to a channel the user has turned off is refused by `Notifier` rather than shown.
         */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            // Before enqueuing, or KEEP preserves the orphan alongside the new one. One instance for
            // both calls, so the ordering is visibly on the thing it has to be ordered on.
            workManager.cancelUniqueWork(RETIRED_WORK_NAME)

            val request = PeriodicWorkRequestBuilder<NudgeWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
