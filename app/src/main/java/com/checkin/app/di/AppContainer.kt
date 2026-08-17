package com.checkin.app.di

import android.content.Context
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.AndroidNotifier
import com.checkin.app.notify.AndroidStringResolver
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.engagement.AndroidNudgeAlarms
import com.checkin.app.notify.engagement.DefaultEngagementReporter
import com.checkin.app.notify.engagement.EngagementInstall
import com.checkin.app.notify.engagement.EngagementReporter
import com.checkin.app.notify.engagement.NudgeAlarms
import com.checkin.app.notify.engagement.NudgeDispatcher
import com.checkin.app.notify.engagement.SharedPrefsEngagementInstall
import com.checkin.app.notify.log.EngagementDatabase
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.RoomEngagementLog
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.DefaultCsvExporter
import com.checkin.app.platform.DefaultServiceController
import com.checkin.app.platform.PromptSettings
import com.checkin.app.platform.SelfieStorage
import com.checkin.app.platform.ServiceController
import com.checkin.app.platform.SharedPrefsPromptSettings
import com.checkin.app.service.AndroidSessionAlarms
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionReminderRunner
import com.checkin.app.service.SessionWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minimal manual DI: the single place that builds the repository, the side-effect seams
 * ([PromptSettings], [ServiceController], [CsvExporter]), and the app-wide coroutine scope.
 * ViewModels receive these via their factories, so they stay pure and unit-testable with fakes.
 */
interface AppContainer {
    val repository: CheckInRepository
    val settings: PromptSettings
    val serviceController: ServiceController
    val csvExporter: CsvExporter
    val timeSource: TimeSource
    val applicationScope: CoroutineScope

    // Notification plumbing, shared by the foreground service and the engagement layer so that all
    // three notifications are described and built one way.
    val notifier: Notifier
    val notificationFactory: NotificationFactory

    // Engagement layer. Isolated from everything above: its own prefs namespace, its own database,
    // and no writes to the sessions table.
    val engagementInstall: EngagementInstall
    val engagementLog: EngagementLog
    val nudgeDispatcher: NudgeDispatcher
    val engagementReporter: EngagementReporter
    val nudgeAlarms: NudgeAlarms

    // Session mechanics that deliberately do not live inside CheckInService, because both have to
    // work in a process where no service is running: an alarm can be delivered into a process the
    // broadcast just created, and the watchdog exists precisely for when the service is gone.
    val sessionAlarms: SessionAlarms
    val sessionReminderRunner: SessionReminderRunner
    val sessionWatchdog: SessionWatchdog
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val timeSource: TimeSource = SystemTimeSource

    // Outlives any ViewModel/composition: used for fire-and-forget work that must not be cancelled
    // by a screen leaving composition (e.g. deleting a transient selfie after the gate is dismissed).
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Clear any selfie orphaned by process death between capture and its post-detection delete.
        applicationScope.launch(Dispatchers.IO) { SelfieStorage.sweep(appContext) }
    }

    override val settings: PromptSettings = SharedPrefsPromptSettings.create(appContext)

    override val repository: CheckInRepository by lazy {
        CheckInRepository(AppDatabase.getDatabase(appContext).checkInSessionDao(), timeSource)
    }

    override val serviceController: ServiceController = DefaultServiceController(appContext)

    override val csvExporter: CsvExporter = DefaultCsvExporter(appContext)

    override val engagementInstall: EngagementInstall = SharedPrefsEngagementInstall.create(appContext)

    override val notificationFactory = NotificationFactory(appContext)

    override val notifier: Notifier = AndroidNotifier(appContext, notificationFactory)

    override val engagementLog: EngagementLog by lazy {
        RoomEngagementLog(EngagementDatabase.getDatabase(appContext).engagementEventDao())
    }

    override val nudgeDispatcher: NudgeDispatcher by lazy {
        NudgeDispatcher(
            strings = AndroidStringResolver(appContext),
            repository = repository,
            install = engagementInstall,
            notifier = notifier,
            log = engagementLog,
            timeSource = timeSource,
        )
    }

    override val engagementReporter: EngagementReporter by lazy {
        DefaultEngagementReporter(notifier, engagementLog)
    }

    // Eager, like sessionAlarms: it holds the last-armed instant the debug snapshot reads, and two
    // instances would each report only what they themselves armed.
    override val nudgeAlarms: NudgeAlarms = AndroidNudgeAlarms(appContext)

    // One instance, shared: the armed instants live in SharedPreferences, so a second would read the
    // same values — but hoisting it keeps the runner and anything that merely *inspects* the alarms
    // (the debug snapshot) demonstrably looking at one seam rather than two that happen to agree.
    override val sessionAlarms: SessionAlarms = AndroidSessionAlarms(appContext)

    override val sessionReminderRunner: SessionReminderRunner by lazy {
        SessionReminderRunner(
            repository = repository,
            notifier = notifier,
            strings = AndroidStringResolver(appContext),
            alarms = sessionAlarms,
            log = engagementLog,
            timeSource = timeSource,
        )
    }

    override val sessionWatchdog: SessionWatchdog by lazy {
        SessionWatchdog(repository, serviceController, sessionReminderRunner, engagementLog, timeSource)
    }
}
