package com.checkin.app

import android.app.Application
import com.checkin.app.di.AppContainer
import com.checkin.app.di.DefaultAppContainer
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.engagement.NudgeWorker

/** Owns the app-wide [AppContainer] (manual DI — no framework). */
class CheckInApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Registered here rather than in the service, so a channel exists before anything tries to
        // post to it — the engagement pass can run without the service ever having started.
        NotificationChannels.ensureAll(this)
        NudgeWorker.schedule(this)
        // Alarms do not survive a force stop or a package replace, and every process start is a free
        // chance to put this one back. Idempotent — it always resolves to the same next checkpoint.
        //
        // Wrapped because this runs on the main thread of every cold start with nothing above it to
        // catch: `setAndAllowWhileIdle` throws past the platform's 500-alarm cap, and a missing nudge
        // must not be a crash on launch. Nothing reads back whether a checkpoint is armed, so what
        // bounds the cost is the other arming sites — the receiver's own re-arm, the worker's repair
        // and the boot/package-replace receiver — any of which puts it back.
        runCatching { container.nudgeAlarms.armNext(container.timeSource.nowMillis()) }
    }
}
