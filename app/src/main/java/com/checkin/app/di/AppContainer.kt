package com.checkin.app.di

import android.content.Context
import android.os.Build
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.AppDatabase
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.notify.AndroidHabitChannels
import com.checkin.app.notify.AndroidNotifier
import com.checkin.app.notify.AndroidStringResolver
import com.checkin.app.notify.HabitChannels
import com.checkin.app.notify.NotificationFactory
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.nudge.AndroidNudgeAlarms
import com.checkin.app.notify.nudge.NudgeAlarms
import com.checkin.app.notify.nudge.NudgeCoordinator
import com.checkin.app.notify.nudge.NudgeDispatcher
import com.checkin.app.notify.nudge.NudgeSendDatabase
import com.checkin.app.notify.nudge.NudgeSendLog
import com.checkin.app.notify.nudge.PostedNudges
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.RoomNudgeSendLog
import com.checkin.app.notify.nudge.SharedPrefsReminderSettings
import com.checkin.app.notify.nudge.armFallback
import com.checkin.app.platform.AppUpdates
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.DefaultCsvExporter
import com.checkin.app.platform.DefaultServiceController
import com.checkin.app.platform.LanguageSettings
import com.checkin.app.platform.LocaleManagerLanguageSettings
import com.checkin.app.platform.PlayAppUpdates
import com.checkin.app.platform.PromptSettings
import com.checkin.app.platform.ServiceController
import com.checkin.app.platform.SharedPrefsPromptSettings
import com.checkin.app.service.AndroidSessionAlarms
import com.checkin.app.service.CheckInService
import com.checkin.app.service.SessionActions
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionLifecycleRunner
import com.checkin.app.service.SessionWatchdog
import com.checkin.app.ui.checkin.raiseCheckOutCelebration
import com.checkin.app.util.MILLIS_PER_MINUTE
import com.checkin.app.util.MILLIS_PER_SECOND
import com.checkin.app.widget.AndroidHomeWidgets
import com.checkin.app.widget.HomeWidgets
import com.checkin.app.widget.WidgetFace
import com.checkin.app.widget.WidgetSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Minimal manual DI: the single place that builds the repositories, the side-effect seams
 * ([PromptSettings], [LanguageSettings], [ServiceController], [CsvExporter], [HomeWidgets]), and the
 * app-wide coroutine scope.
 * ViewModels receive these via their factories, so they stay pure and unit-testable with fakes.
 */
interface AppContainer {
    val repository: CheckInRepository
    val habitRepository: HabitRepository
    val settings: PromptSettings
    val reminderSettings: ReminderSettings
    val languageSettings: LanguageSettings?
    val serviceController: ServiceController
    val csvExporter: CsvExporter
    val appUpdates: AppUpdates
    val homeWidgets: HomeWidgets
    val widgetSync: WidgetSync
    val timeSource: TimeSource
    val applicationScope: CoroutineScope

    // Notification plumbing, shared by the foreground service and the nudge layer so that all three
    // notifications are described and built one way.
    val notifier: Notifier
    val notificationFactory: NotificationFactory
    val habitChannels: HabitChannels

    // Nudge layer. Isolated from everything above: its own database, and no writes to the sessions
    // table. The ledger holds only what the frequency rules read — see NudgeSendLog for why nothing
    // write-only may be added back to it.
    val nudgeSendLog: NudgeSendLog
    val nudgeDispatcher: NudgeDispatcher
    val postedNudges: PostedNudges
    val nudgeAlarms: NudgeAlarms

    // The fire-and-forget entry points to the dispatcher, guarded; see NudgeCoordinator.
    val nudges: NudgeCoordinator

    /**
     * Keeps every habit's notification channel and the reminder fallback's active set in step with
     * the habits table, and re-arms the nudge alarm whenever the set of habits changes — for the life
     * of the process, from its start.
     *
     * Driven off the table rather than called by each write, so no write path can forget either:
     * a habit created, renamed, archived or restored is one emission, and the first emission is the
     * process start itself, which is what puts the alarm back after a boot, a package replace or a
     * force stop. Never throws: a database that will not open still arms the fallback, and a failed
     * channel write costs that emission's channels, which the dispatcher re-ensures before it posts.
     * A failed read re-subscribes with backoff ([habitSyncRetryDelayMs]) rather than ending the sync.
     */
    fun launchHabitSync() {
        applicationScope.launch {
            habitRepository.allHabitsFlow()
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) return@retryWhen false
                    nudges.armFallback()
                    delay(habitSyncRetryDelayMs(attempt))
                    true
                }
                .collect { habits ->
                    runCatching { habitChannels.sync(habits) }
                    val active = habits.filterNot { it.archived }.mapTo(mutableSetOf()) { it.uuid }
                    runCatching { reminderSettings.rememberActive(active) }
                    nudges.rearmNow()
                }
        }
    }

    /**
     * Re-names every habit's channel, re-posts a running timer and redraws the widgets in the app's
     * current language. A per-app language change recreates the activity but not the process, so
     * nothing else that writes those strings runs again: the habits collector only emits on a table
     * change, and the timer is posted on state changes alone. Never throws, for the same reason as
     * [launchHabitSync].
     */
    fun launchLanguageRefresh() {
        applicationScope.launch {
            runCatching { habitChannels.sync(habitRepository.allHabitsFlow().first()) }
            if (CheckInService.isRunning) runCatching { serviceController.refreshFromDb() }
            widgetSync.redraw(force = true)
        }
    }

    // Session mechanics that deliberately do not live inside CheckInService, because both have to
    // work in a process where no service is running: an alarm can be delivered into a process the
    // broadcast just created, and the watchdog exists precisely for when the service is gone. The
    // alarm seam itself is not exposed — everything goes through the runner, which owns the ordering.
    val sessionLifecycleRunner: SessionLifecycleRunner
    val sessionWatchdog: SessionWatchdog

    // The gated check-in and check-out, shared by the Check-In screen and the root gate.
    val sessionActions: SessionActions
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val timeSource: TimeSource = SystemTimeSource

    // Outlives any ViewModel/composition: used for fire-and-forget work that must not be cancelled by
    // a screen leaving composition, such as a check-out written from a notification action.
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val settings: PromptSettings = SharedPrefsPromptSettings.create(appContext)

    override val reminderSettings: ReminderSettings = SharedPrefsReminderSettings.create(appContext)

    // Null below API 33, which has no per-app locale for it to read or write.
    override val languageSettings: LanguageSettings? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LocaleManagerLanguageSettings(appContext) else null

    override val repository: CheckInRepository by lazy {
        CheckInRepository(AppDatabase.getDatabase(appContext).checkInSessionDao(), timeSource)
    }

    override val habitRepository: HabitRepository by lazy {
        val db = AppDatabase.getDatabase(appContext)
        HabitRepository(db.habitDao(), db.appStateDao(), db.checkInSessionDao(), timeSource)
    }

    override val serviceController: ServiceController = DefaultServiceController(appContext)

    override val csvExporter: CsvExporter = DefaultCsvExporter(appContext)

    override val appUpdates: AppUpdates = PlayAppUpdates(appContext)

    override val homeWidgets: HomeWidgets = AndroidHomeWidgets(appContext)

    override val widgetSync: WidgetSync by lazy {
        WidgetSync(
            widgets = homeWidgets,
            faces = {
                WidgetFace.faces(
                    activeHabits = habitRepository.activeHabitsFlow(),
                    runningHabitId = repository.activeSessionFlow().map { it?.habitId },
                    animationsOn = homeWidgets.animationsOn(),
                )
            },
            scope = applicationScope,
        )
    }

    override val notificationFactory = NotificationFactory(appContext)

    override val notifier: Notifier = AndroidNotifier(appContext, notificationFactory)

    override val habitChannels: HabitChannels = AndroidHabitChannels(appContext)

    override val nudgeSendLog: NudgeSendLog by lazy {
        RoomNudgeSendLog(NudgeSendDatabase.getDatabase(appContext).nudgeSendDao())
    }

    override val nudgeDispatcher: NudgeDispatcher by lazy {
        NudgeDispatcher(
            strings = AndroidStringResolver(appContext),
            repository = repository,
            habits = habitRepository,
            notifier = notifier,
            sendLog = { nudgeSendLog },
            timeSource = timeSource,
            reminderSettings = reminderSettings,
            alarms = nudgeAlarms,
            channels = habitChannels,
        )
    }

    // The dispatcher itself: retiring a nudge is cancelling ids it already owns, so a second
    // implementation would be free to disagree with it about which ids exist.
    override val postedNudges: PostedNudges get() = nudgeDispatcher

    // Stateless — the next instant is derivable from now — and eager, unlike the dispatcher that
    // decides the instant: it is the fallback arm when the dispatcher cannot be built.
    override val nudgeAlarms: NudgeAlarms = AndroidNudgeAlarms(appContext)

    override val nudges: NudgeCoordinator by lazy {
        NudgeCoordinator(
            runOnce = { nudgeDispatcher.runOnce() },
            rearm = { nudgeDispatcher.rearm() },
            alarms = nudgeAlarms,
            reminderSettings = reminderSettings,
            timeSource = timeSource,
            scope = applicationScope,
            firstRunPending = { !settings.hasAskedNotifications() },
        )
    }

    // Held by the runner alone; the watchdog repairs alarms through the runner, not through this.
    private val sessionAlarms: SessionAlarms = AndroidSessionAlarms(appContext)

    override val sessionLifecycleRunner: SessionLifecycleRunner by lazy {
        SessionLifecycleRunner(
            repository = repository,
            notifier = notifier,
            strings = AndroidStringResolver(appContext),
            alarms = sessionAlarms,
            timeSource = timeSource,
        )
    }

    override val sessionActions: SessionActions by lazy {
        SessionActions(
            repository = repository,
            serviceController = serviceController,
            lifecycle = sessionLifecycleRunner,
            postedNudges = postedNudges,
            scope = applicationScope,
            onCheckedOut = { closed ->
                raiseCheckOutCelebration(repository, habitRepository, closed, timeSource.nowMillis())
            },
        )
    }

    override val sessionWatchdog: SessionWatchdog by lazy {
        SessionWatchdog(repository, serviceController, sessionLifecycleRunner, timeSource)
    }
}

private const val HABIT_SYNC_RETRY_MS = 30 * MILLIS_PER_SECOND
private const val HABIT_SYNC_RETRY_MAX_MS = 30 * MILLIS_PER_MINUTE

/** The habits collector's wait after a failed read: doubling from 30s, capped at 30min. */
internal fun habitSyncRetryDelayMs(attempt: Long): Long =
    (HABIT_SYNC_RETRY_MS shl attempt.coerceAtMost(HABIT_SYNC_MAX_DOUBLINGS).toInt())
        .coerceAtMost(HABIT_SYNC_RETRY_MAX_MS)

/** Enough to pass the cap; few enough that the shift cannot overflow. */
private const val HABIT_SYNC_MAX_DOUBLINGS = 6L
