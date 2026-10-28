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
 * "Costs nothing" is [NudgeSnapshot.spentToday] doing that work. Without it, a pass landing inside
 * a band the alarm has already fired in sends the *same* habit's nudge again — identical copy, same
 * id, re-alerting on a high-importance channel. Once per habit per day is what makes a redundant
 * pass genuinely free.
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
            // The pass also re-arms the checkpoint alarm, so an alarm lost to a force stop is repaired
            // here even on a pass that finds nothing to send — and through the container, so a
            // dispatcher that cannot be built still arms the plain checkpoint. It never throws, so a
            // refused arm cannot skip the prune below.
            container.nudges.runPass()
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
         * A name this work was previously enqueued under, cancelled before every enqueue.
         *
         * A unique name is WorkManager's own persisted key, so renaming does not rename the existing
         * work — it enqueues new work and leaves the old `WorkSpec` standing, here naming a worker
         * class that no longer resolves. Cancelling it is the analogue of deleting a retired
         * notification channel. Keep this call for as long as any install may still hold the old
         * work — nothing reports back that it is gone — and append rather than replace if the name
         * ever changes again. [WORK_NAME] is frozen for the same reason.
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

            // A first pass at enqueue time would run on a fresh install while the welcome tour is up:
            // a nudge over it, or — with notifications not yet asked for — a refused attempt that
            // spends the day's reminder. The backstop can wait an interval like any other pass.
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
