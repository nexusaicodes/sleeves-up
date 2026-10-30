package com.checkin.app

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import com.checkin.app.di.AppContainer
import com.checkin.app.di.DefaultAppContainer
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.nudge.NudgeSendDatabase
import com.checkin.app.notify.nudge.NudgeWorker
import com.checkin.app.util.useLatinDigitsByDefault
import kotlinx.coroutines.launch

/** Owns the app-wide [AppContainer] (manual DI — no framework). */
class CheckInApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        useLatinDigitsByDefault()
        container = DefaultAppContainer(this)
        // Registered here rather than in the service, so a channel exists before anything tries to
        // post to it — the hourly pass can run without the service ever having started.
        NotificationChannels.ensureAll(this)
        NudgeWorker.schedule(this)
        // Alarms do not survive a force stop, a package replace or a reboot, and every one of those is
        // followed by a process start — the receivers for boot and replace run in one — so the habits
        // collector's first emission is the single site that puts the nudge alarm back. It runs off
        // the main thread and never throws; see AppContainer.launchHabitSync.
        container.launchHabitSync()
        // Same pattern, same reason: a placed widget follows the tables from the process start onward.
        container.widgetSync.start()
        // Off the main thread: blocking filesystem work nothing waits on, and every path that reaches
        // the ledger reaches it during the first frame. Guarded so a file that will not delete is not
        // a crash on launch.
        container.applicationScope.launch {
            runCatching { NudgeSendDatabase.deleteRetiredFile(this@CheckInApplication) }
        }
        languages = resources.configuration.locales
    }

    // The languages every notification string was last written in. Channel names live in the system,
    // not in this process, so a language change has to rewrite them rather than just re-read them.
    private var languages: LocaleList = LocaleList.getEmptyLocaleList()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        useLatinDigitsByDefault()
        if (newConfig.locales == languages) return
        languages = newConfig.locales
        NotificationChannels.ensureAll(this)
        container.launchLanguageRefresh()
    }
}
