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
import com.checkin.app.notify.nudge.AndroidNudgeAlarms
import com.checkin.app.notify.nudge.NudgeAlarms
import com.checkin.app.notify.nudge.NudgeDispatcher
import com.checkin.app.notify.nudge.NudgeSendDatabase
import com.checkin.app.notify.nudge.NudgeSendLog
import com.checkin.app.notify.nudge.PostedNudges
import com.checkin.app.notify.nudge.RoomNudgeSendLog
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.DefaultCsvExporter
import com.checkin.app.platform.DefaultServiceController
import com.checkin.app.platform.PromptSettings
import com.checkin.app.platform.ServiceController
import com.checkin.app.platform.SharedPrefsPromptSettings
import com.checkin.app.service.AndroidSessionAlarms
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionLifecycleRunner
import com.checkin.app.service.SessionWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    // Notification plumbing, shared by the foreground service and the nudge layer so that all three
    // notifications are described and built one way.
    val notifier: Notifier
    val notificationFactory: NotificationFactory

    // Nudge layer. Isolated from everything above: its own database, and no writes to the sessions
    // table. The ledger holds only what the frequency rules read — see NudgeSendLog for why nothing
    // write-only may be added back to it.
    val nudgeSendLog: NudgeSendLog
    val nudgeDispatcher: NudgeDispatcher
    val postedNudges: PostedNudges
    val nudgeAlarms: NudgeAlarms

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

    override val repository: CheckInRepository by lazy {
        CheckInRepository(AppDatabase.getDatabase(appContext).checkInSessionDao(), timeSource)
    }

    override val serviceController: ServiceController = DefaultServiceController(appContext)

    override val csvExporter: CsvExporter = DefaultCsvExporter(appContext)

    override val notificationFactory = NotificationFactory(appContext)

    override val notifier: Notifier = AndroidNotifier(appContext, notificationFactory)

    override val nudgeSendLog: NudgeSendLog by lazy {
        RoomNudgeSendLog(NudgeSendDatabase.getDatabase(appContext).nudgeSendDao())
    }

    override val nudgeDispatcher: NudgeDispatcher by lazy {
        NudgeDispatcher(
            strings = AndroidStringResolver(appContext),
            repository = repository,
            notifier = notifier,
            log = nudgeSendLog,
            timeSource = timeSource,
        )
    }

    // The dispatcher itself: retiring a nudge is cancelling ids it already owns, so a second
    // implementation would be free to disagree with it about which ids exist.
    override val postedNudges: PostedNudges get() = nudgeDispatcher

    // Stateless — a checkpoint is derivable from now — so this is just the one arming seam its callers
    // share: the receiver re-arming from the alarm it just handled, which is what makes the chain
    // self-sustaining, plus every process start, the deferrable worker repair, and boot/package replace.
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
