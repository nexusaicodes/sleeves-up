package com.checkin.app.di

import android.content.Context
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
import com.checkin.app.notify.nudge.NudgeDispatcher
import com.checkin.app.notify.nudge.NudgeSendDatabase
import com.checkin.app.notify.nudge.NudgeSendLog
import com.checkin.app.notify.nudge.PostedNudges
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.RoomNudgeSendLog
import com.checkin.app.notify.nudge.SharedPrefsReminderSettings
import com.checkin.app.notify.nudge.armFallback
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.DefaultCsvExporter
import com.checkin.app.platform.DefaultServiceController
import com.checkin.app.platform.PromptSettings
import com.checkin.app.platform.ServiceController
import com.checkin.app.platform.SharedPrefsPromptSettings
import com.checkin.app.service.AndroidSessionAlarms
import com.checkin.app.service.CheckInService
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionLifecycleRunner
import com.checkin.app.service.SessionWatchdog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Minimal manual DI: the single place that builds the repositories, the side-effect seams
 * ([PromptSettings], [ServiceController], [CsvExporter]), and the app-wide coroutine scope.
 * ViewModels receive these via their factories, so they stay pure and unit-testable with fakes.
 */
interface AppContainer {
    val repository: CheckInRepository
    val habitRepository: HabitRepository
    val settings: PromptSettings
    val reminderSettings: ReminderSettings
    val serviceController: ServiceController
    val csvExporter: CsvExporter
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

    /**
     * [NudgeDispatcher.runOnce] for a fire-and-forget caller — the checkpoint receiver and the hourly
     * worker. Never throws except to cancel; see [armNudgeFallback] for what a failure arms.
     */
    suspend fun runNudgePass() {
        guardNudgeArm { nudgeDispatcher.runOnce() }
    }

    /**
     * [NudgeDispatcher.rearm] for a fire-and-forget caller: process start. Never throws except to
     * cancel, because it runs on [applicationScope], which has no exception handler — a throw there
     * kills the process.
     */
    suspend fun rearmNudges() {
        guardNudgeArm { nudgeDispatcher.rearm() }
    }

    /**
     * Launches [rearmNudges] on [applicationScope]: the one re-arm every reminder-time chooser hands
     * to `chooseReminderTime`, so no two of them can launch it differently.
     */
    fun launchNudgeRearm() {
        applicationScope.launch { rearmNudges() }
    }

    /**
     * Both dispatcher calls already absorb their own failures, so what this guards is reaching them:
     * the dispatcher is built lazily over both databases, and a failure there falls back to the plain
     * fallback band from the members it is built without. Losing the retry costs at most a nudge;
     * losing the arm ends the chain the checkpoint alarm exists to keep alive.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun guardNudgeArm(call: suspend () -> Unit) {
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            armNudgeFallback()
        }
    }

    private fun armNudgeFallback() {
        runCatching { nudgeAlarms.armFallback(timeSource.nowMillis(), timeSource.zone(), reminderSettings) }
    }

    /**
     * Keeps every habit's notification channel in step with the habits table, and re-arms the nudge
     * alarm whenever the set of habits changes — for the life of the process, from its start.
     *
     * Driven off the table rather than called by each write, so no write path can forget either:
     * a habit created, renamed, archived or restored is one emission, and the first emission is the
     * process start itself, which is what puts the alarm back after a boot, a package replace or a
     * force stop. Never throws: a database that will not open still arms the fallback, and a failed
     * channel write costs that emission's channels, which the dispatcher re-ensures before it posts.
     */
    fun launchHabitSync() {
        applicationScope.launch {
            habitRepository.allHabitsFlow()
                .catch { armNudgeFallback() }
                .collect { habits ->
                    runCatching { habitChannels.sync(habits) }
                    rearmNudges()
                }
        }
    }

    /**
     * Re-names every habit's channel and re-posts a running timer in the app's current language.
     * A per-app language change recreates the activity but not the process, so nothing else that
     * writes those strings runs again: the habits collector only emits on a table change, and the
     * timer is posted on state changes alone. Never throws, for the same reason as [launchHabitSync].
     */
    fun launchLanguageRefresh() {
        applicationScope.launch {
            runCatching { habitChannels.sync(habitRepository.allHabitsFlow().first()) }
            if (CheckInService.isRunning) runCatching { serviceController.refreshFromDb() }
        }
    }

    // Session mechanics that deliberately do not live inside CheckInService, because both have to
    // work in a process where no service is running: an alarm can be delivered into a process the
    // broadcast just created, and the watchdog exists precisely for when the service is gone. The
    // alarm seam itself is not exposed — everything goes through the runner, which owns the ordering.
    val sessionLifecycleRunner: SessionLifecycleRunner
    val sessionWatchdog: SessionWatchdog
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val timeSource: TimeSource = SystemTimeSource

    // Outlives any ViewModel/composition: used for fire-and-forget work that must not be cancelled by
    // a screen leaving composition, such as a check-out written from a notification action.
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val settings: PromptSettings = SharedPrefsPromptSettings.create(appContext)

    override val reminderSettings: ReminderSettings = SharedPrefsReminderSettings.create(appContext)

    override val repository: CheckInRepository by lazy {
        CheckInRepository(AppDatabase.getDatabase(appContext).checkInSessionDao(), timeSource)
    }

    override val habitRepository: HabitRepository by lazy {
        val db = AppDatabase.getDatabase(appContext)
        HabitRepository(db.habitDao(), db.appStateDao(), db.checkInSessionDao(), timeSource)
    }

    override val serviceController: ServiceController = DefaultServiceController(appContext)

    override val csvExporter: CsvExporter = DefaultCsvExporter(appContext)

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

    // One instance, shared by the runner and the watchdog below. The armed instants live in
    // SharedPreferences, so a second would read the same values; hoisting it keeps both callers
    // demonstrably on one seam rather than on two that happen to agree.
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

    override val sessionWatchdog: SessionWatchdog by lazy {
        SessionWatchdog(repository, serviceController, sessionLifecycleRunner, timeSource)
    }
}
