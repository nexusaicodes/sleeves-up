package com.checkin.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.edit
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.data.TimeSource
import com.checkin.app.notify.NotificationAction
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows the ongoing timer for an active session, and only that.
 *
 * The platform draws the elapsed time from a single post (see [NotificationSpec.chronometerBase]),
 * and the session's reminder and day-boundary close run off alarms ([SessionLifecycleRunner]), so
 * this posts on state changes only — a handful of times per session. **Do not add a ticker.** A
 * per-second re-post is tens of thousands of main-thread binder calls into the system over a long
 * session, as many chances for one to throw, and the behavioural signature OEM background management
 * kills apps for — while still freezing in deep sleep, since a coroutine `delay` runs on uptime.
 *
 * The database row is authoritative for everything that ends up in a session's duration; the fields
 * here and the `checkin_timer_prefs` mirror are a cache for rendering.
 */
class CheckInService : Service() {

    /**
     * The scope every command runs on.
     *
     * Both halves of the context are load-bearing. The handler stops a refused or throwing platform
     * call from taking the process — and with it the user's running session — down with it. The
     * **supervisor** job stops that same throw from taking the *scope* down: an exception handler
     * reports a failure, it does not contain one, so under a plain `Job()` the first throw cancels
     * the scope for good and every later command becomes a silent no-op — including the reconcile
     * that would tear down an orphaned notification.
     */
    private val serviceScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() +
            CoroutineExceptionHandler { _, throwable -> logDegraded("scope: ${throwable.javaClass.simpleName}") },
    )

    private val container by lazy { (application as CheckInApplication).container }
    private val repository by lazy { container.repository }
    private val notifier: Notifier by lazy { container.notifier }
    private val notificationFactory: NotificationFactory by lazy { container.notificationFactory }
    private val sessionLifecycleRunner: SessionLifecycleRunner by lazy { container.sessionLifecycleRunner }

    /** The same injectable clock the rest of the app reads, rather than a direct platform call. */
    private val timeSource: TimeSource by lazy { container.timeSource }

    // Analytics only. Service rows are scoped out of the nudge cap and attribution queries, so
    // writing here can't change what the engagement layer decides to send.
    private val engagementLog: EngagementLog by lazy { container.engagementLog }

    /** The in-flight DB reconciliation. A later command cancels it so a stale snapshot can't win. */
    private var reconcileJob: Job? = null

    private var startTime: Long = 0
    private var sessionId: Long = -1

    companion object {
        /**
         * Whether a session currently has a **foreground notification** behind it in this process.
         *
         * The watchdog reads this to decide whether a session has lost its timer, so it tracks the
         * notification and not merely the existence of a `Service` object. Those are not the same
         * state: [enterForeground] is guarded, and a caught `startForeground` failure leaves this
         * instance alive with nothing on the shade — the exact condition the watchdog exists to
         * repair. Setting it in `onCreate` would report that condition as healthy forever.
         *
         * A killed process resets it to false on restart, which is the other signal wanted.
         * Deliberately not `ActivityManager.getRunningServices`, which is deprecated and no longer
         * reports other processes' services reliably.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        /** The watchdog found an open session with no service. Distinct from [ACTION_START]. */
        const val ACTION_REVIVE = "REVIVE"

        /** Something outside the service changed the row; re-read it and redraw the notification. */
        const val ACTION_REFRESH = "REFRESH"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_START_TIME = "START_TIME"
        const val EXTRA_CHECK_OUT = "check_out"

        /** Set by an engagement nudge tap; opens the gate and checks in on success. */
        const val EXTRA_CHECK_IN = "check_in"
        const val PREFS_NAME = "checkin_timer_prefs"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"
    }

    override fun onCreate() {
        super.onCreate()
        // Channels are registered app-wide by CheckInApplication; re-ensuring here is idempotent and
        // keeps this service independent of who started it.
        NotificationChannels.ensureAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_START -> handleStart(intent)
        ACTION_STOP -> {
            tearDown()
            START_NOT_STICKY
        }
        ACTION_REVIVE -> handleRevive(intent)
        ACTION_REFRESH -> reconcileAndPost()
        else -> handleStickyRestart()
    }

    private fun handleStart(intent: Intent): Int {
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        // Share the DB row's check-in instant so this notification and the on-screen ticker agree;
        // fall back to now only if the extra is missing.
        startTime = intent.getLongExtra(EXTRA_START_TIME, timeSource.nowMillis())
        saveState()

        enterForeground()
        logService(ServiceEventType.STARTED, sessionId.toString())
        // The session's alarms are deliberately not armed here. This start can be refused — a
        // restricted App Standby bucket, an OEM that declines a specialUse foreground service — and
        // a refusal must not cost the session its day-boundary close. Arming belongs to whoever
        // wrote the row, which cannot be refused; see CheckInViewModel.executeCheckIn.
        return START_STICKY
    }

    /**
     * Restores the notification for a session that is already running, after its service was killed.
     *
     * Deliberately **not** [ACTION_START]. That path is written for a fresh check-in: it takes the
     * session's timing from the intent and saves it as the render mirror, which is correct for a
     * session that has not started yet and wrong for one already running.
     *
     * Restores the notification and nothing else. The session's alarms are repaired by
     * [SessionWatchdog], which sends this command in the first place — and repairs them whether or
     * not the service turned out to need reviving, because the two are lost separately.
     */
    private fun handleRevive(intent: Intent): Int {
        // The prefs mirror first, so the notification posted inside the foreground-start deadline is
        // not blank. Falling back to the extras only matters if it was cleared; the reconcile
        // corrects either way.
        if (startTime == 0L) restoreState()
        if (startTime == 0L) {
            sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
            startTime = intent.getLongExtra(EXTRA_START_TIME, 0L)
        }
        return reconcileAndPost()
    }

    /**
     * `START_STICKY` re-delivery after a kill: restore the advisory mirror for an immediate redraw,
     * then reconcile against the DB.
     *
     * The mirror never *vetoes* the restart. An empty or cleared `checkin_timer_prefs` must not stop
     * the service without asking the database — that would invert the rule that the row is
     * authoritative, precisely where the cache is least trustworthy and the row most.
     */
    private fun handleStickyRestart(): Int = reconcileAndPost()

    /**
     * Meets the foreground-start deadline, then reconciles against the active session row and posts
     * from it. A closed or absent row is an orphan and tears down instead of re-posting.
     */
    private fun reconcileAndPost(): Int {
        // The advisory mirror is read first purely so the notification posted inside the
        // foreground-start deadline shows the right elapsed time rather than counting from the
        // epoch. The DB read below overwrites whatever it said.
        if (startTime == 0L) restoreState()
        enterForeground()
        reconcileJob?.cancel()
        reconcileJob = serviceScope.launch {
            when (val result = ServiceReconciler.reconcile(repository.getActiveSession())) {
                ServiceReconciler.Result.Stop -> tearDown()
                is ServiceReconciler.Result.Adopt -> {
                    adopt(result)
                    saveState()
                    postTimerNotification()
                }
            }
        }
        return START_STICKY
    }

    /** Overwrites in-memory render state with the authoritative DB row's values. */
    private fun adopt(result: ServiceReconciler.Result.Adopt) {
        sessionId = result.sessionId
        startTime = result.startTime
    }

    /** Ends the service: no live session is left for it to show. */
    private fun tearDown() {
        reconcileJob?.cancel()
        reconcileJob = null
        isRunning = false
        sessionLifecycleRunner.cancel()
        logService(ServiceEventType.STOPPED, sessionId.toString())
        clearState()
        cancelReminderNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Delegates to [SessionClock], which is where the arithmetic is unit-tested — a Service is not,
    // and this is the number the user actually reads off the notification.
    private fun chronometerBase(): Long? = SessionClock.chronometerBase(startTime)

    /**
     * Enters (or updates) the foreground state.
     *
     * Guarded because every reason this can throw is a reason to keep the session alive rather than
     * crash: a background-start refusal, a service the system has already demoted, a foreground-type
     * restriction. An escaping throw would kill the process, take the notification off the shade
     * with it, and leave the row open with nothing timing it.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun enterForeground() {
        try {
            startForeground(NotificationIds.TIMER, buildTimerNotification())
            isRunning = true
        } catch (e: Exception) {
            // Cleared, not left standing: this instance is alive but has no notification, and
            // saying otherwise keeps the watchdog from putting one back.
            isRunning = false
            logDegraded("startForeground: ${e.javaClass.simpleName}")
        }
    }

    private fun postTimerNotification() = enterForeground()

    /** The ongoing timer, built rather than posted: `startForeground` takes the object itself. */
    private fun buildTimerNotification(): Notification = notificationFactory.build(timerSpec())

    private fun timerSpec() = NotificationSpec(
        id = NotificationIds.TIMER,
        channelId = NotificationChannels.TIMER,
        title = getString(R.string.notification_title),
        // Must not print the elapsed time — the chronometer already draws it (see the string).
        body = getString(R.string.notification_running),
        actions = listOf(
            // "Check Out" opens the app so the presence gate runs — check-out stays gated, never silent.
            NotificationAction(
                iconRes = R.drawable.ic_stat_check_out,
                label = getString(R.string.notification_action_stop),
                launchExtra = EXTRA_CHECK_OUT,
            ),
        ),
        ongoing = true,
        silent = true,
        chronometerBase = chronometerBase(),
    )

    private fun cancelReminderNotification() {
        notifier.cancel(NotificationIds.SESSION_REMINDER)
    }

    /**
     * Records an event, best-effort.
     *
     * The engagement log drives no tracking rule, so a failed write must not take the foreground
     * service — and with it the user's running timer — down.
     *
     * Written on the **application** scope rather than [serviceScope] on purpose: half of what is
     * worth recording here happens as the service ends — the `STOPPED` row, and the `DEGRADED` row
     * the scope's own exception handler writes — and [serviceScope] is cancelled in `onDestroy`,
     * dropping exactly those. The app scope carries a `SupervisorJob` and no exception handler,
     * hence the `catch`.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun logService(type: ServiceEventType, detail: String) {
        val at = timeSource.nowMillis()
        container.applicationScope.launch {
            try {
                engagementLog.recordService(type, at, detail)
            } catch (e: Exception) {
                // Nothing to recover: analytics is the only thing lost.
            }
        }
    }

    private fun logDegraded(detail: String) = logService(ServiceEventType.DEGRADED, detail)

    private fun saveState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putLong(KEY_SESSION_ID, sessionId)
            putLong(KEY_START_TIME, startTime)
        }
    }

    /** Clears the render mirror but leaves the alarm state to [SessionLifecycleRunner.cancel]. */
    private fun clearState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            remove(KEY_SESSION_ID)
            remove(KEY_START_TIME)
        }
        startTime = 0
        sessionId = -1
    }

    /** Loads the advisory mirror so the first post is not blank while the DB read is in flight. */
    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedStartTime = prefs.getLong(KEY_START_TIME, -1)
        if (savedStartTime == -1L) return

        sessionId = prefs.getLong(KEY_SESSION_ID, -1)
        startTime = savedStartTime
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        // Cancels the scope, not just the jobs held by name: nothing launched on it should outlive
        // the service. The log writes deliberately do not run here (see [logService]), so the
        // breadcrumbs for this very teardown still land.
        serviceScope.cancel()
        reconcileJob = null
        super.onDestroy()
    }
}
