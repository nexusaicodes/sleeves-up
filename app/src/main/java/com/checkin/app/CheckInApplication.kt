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
        // Alarms do not survive a force stop or a package replace, and every process start is a free
        // chance to put this one back. Idempotent — it always resolves to the same next instant.
        //
        // Off the main thread: it reads the reminder hour from prefs and the day's sends from the
        // ledger, both disk work nothing on the first frame waits for. `rearm` absorbs its own
        // failures, so a refused arm is not a crash on launch; nothing reads back whether a
        // checkpoint is armed, so what bounds the cost is the other arming sites — the receiver's own
        // re-arm, the worker's repair and the boot/package-replace receiver — any of which puts it
        // back.
        container.applicationScope.launch { container.nudgeDispatcher.rearm() }
        // Off the main thread for the same reason: it is filesystem work, and every path that reaches
        // the ledger reaches it during the first frame. Guarded so a file that will not delete is not
        // a crash on launch.
        container.applicationScope.launch {
            runCatching { NudgeSendDatabase.deleteRetiredFile(this@CheckInApplication) }
        }
    }
}
