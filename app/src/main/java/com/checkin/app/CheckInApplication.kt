package com.checkin.app

import android.app.Application
import com.checkin.app.di.AppContainer
import com.checkin.app.di.DefaultAppContainer
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.nudge.NudgeSendDatabase
import com.checkin.app.notify.nudge.NudgeWorker
import kotlinx.coroutines.launch

/** Owns the app-wide [AppContainer] (manual DI — no framework). */
class CheckInApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Registered here rather than in the service, so a channel exists before anything tries to
        // post to it — the hourly pass can run without the service ever having started.
        NotificationChannels.ensureAll(this)
        NudgeWorker.schedule(this)
        // Alarms do not survive a force stop, a package replace or a reboot, and every one of those is
        // followed by a process start — the receivers for boot and replace run in one — so this is
        // the single site that puts the alarm back: the habits collector's first emission re-arms,
        // and every later one (a habit created, renamed, archived or restored) re-arms again and
        // brings that habit's notification channel into step. A nudge it finds already due is put
        // off rather than fired, since the start is usually the user opening the app to check in.
        //
        // Off the main thread: it reads the habits, each one's hour from prefs, and today's sessions
        // and sends from both databases, on every cold start including the background ones — the
        // price of arming a band that is open but unsent rather than skipping to tomorrow, and
        // nothing on the first frame waits for it. It never throws, so a database that will not open
        // is not a crash.
        container.launchHabitSync()
        // Off the main thread for the same reason: it is filesystem work, and every path that reaches
        // the ledger reaches it during the first frame. Guarded so a file that will not delete is not
        // a crash on launch.
        container.applicationScope.launch {
            runCatching { NudgeSendDatabase.deleteRetiredFile(this@CheckInApplication) }
        }
    }
}
