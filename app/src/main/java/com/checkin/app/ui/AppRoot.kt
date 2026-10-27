package com.checkin.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.holdsNotificationPermission
import com.checkin.app.notify.notificationsNeedRuntimeGrant
import com.checkin.app.platform.PromptSettings
import com.checkin.app.ui.checkin.CheckOutCelebration
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.habits.HabitPickerScreen
import com.checkin.app.ui.habits.HabitsViewModel
import com.checkin.app.ui.habits.NameHabitScreen
import com.checkin.app.ui.navigation.AppNavScaffold
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import com.checkin.app.ui.presence.PresenceGate
import com.checkin.app.ui.welcome.FirstRun
import com.checkin.app.ui.welcome.WelcomeScreen

/**
 * The four surfaces that can occupy the whole window, in the order they outrank each other:
 * the first-run welcome, the naming step for a habit with no name, the presence gate (with the habit
 * picker that can precede it), then the app — with the celebration over whichever is showing.
 *
 * **The welcome outranks the gate**, because an introduction the app owes is worth more than the
 * few seconds it delays an action, and the reverse ordering delivers the tour *after* the user
 * has already checked in — an introduction to an app they have just used. The reachable case is an
 * install updated from a build without the welcome: it owes the tour while its alarms are armed and
 * its notifications live, so a nudge tap can raise the gate with the tour still owed. The request
 * survives the read (its 10-minute fuse only retires in
 * [MainActivity.onStart][com.checkin.app.MainActivity.onStart]), so the check-in follows immediately.
 *
 * **The naming step outranks the gate for the same reason**: an install updated onto habits owes a
 * name for the record it carried, and a nudge already in its tray can raise the gate before the
 * user has opened the app since. A gate passed first would check in to a habit the Check In screen
 * then cannot name.
 *
 * Two states follow from the ordering. Nothing can preempt the welcome, so its pager position is
 * never disposed mid-read the way the nav controller's would be — the hoist below prevents that for
 * the host. And a pending celebration is never drawn over the tour, since every check-out path sits
 * behind it.
 *
 * A `@Composable` rather than the body of [MainActivity.onCreate][com.checkin.app.MainActivity.onCreate]
 * so each branch is one call site: [AppNavScaffold] in two arms would carry two structural
 * identities, and a step change would discard the NavHost and reset the active tab.
 */
@Composable
fun AppRoot(onGatePassed: () -> Unit, onHabitPicked: (habitId: Long) -> Unit) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as CheckInApplication).container }
    val settings = container.settings
    val gateRequest by PresenceCheckSignal.request.collectAsStateWithLifecycle()
    val resolvingTaps by PresenceCheckSignal.resolvingTaps.collectAsStateWithLifecycle()
    val completed by CheckOutSignal.completed.collectAsStateWithLifecycle()
    // Activity-scoped, shared with the nav scaffold's switcher: the naming step reads the
    // habits from the same instance, so a habit named there is the one the switcher then shows.
    val habitsViewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory)
    val habits by habitsViewModel.uiState.collectAsStateWithLifecycle()

    // Both mirrored so ending the welcome recomposes: PromptSettings is a synchronous prefs read
    // with no Flow behind it, so the writes alone would leave the tour on screen — and a skip
    // resolves the notification ask too, which the step below has to see on the same frame.
    var seenWelcome by rememberSaveable { mutableStateOf(settings.hasSeenWelcome()) }
    var askedNotifications by rememberSaveable { mutableStateOf(settings.hasAskedNotifications()) }
    val step = FirstRun.step(
        seenWelcome = seenWelcome,
        habitNeedsName = if (habits.loading) null else habits.needingName != null,
        askedNotifications = askedNotifications,
    )

    val endWelcome = { exit: FirstRun.Exit ->
        settings.markWelcomeSeen()
        seenWelcome = true
        if (!FirstRun.asksNotificationsAfter(exit)) {
            // Recorded as raised without raising it: the flag's job is to keep the launch-time
            // dialog from appearing twice, and a skip has decided it should not appear at all.
            settings.markNotificationsAsked()
            askedNotifications = true
            container.nudges.launchRearm()
        }
    }

    // Hoisted above the switch so entering and leaving the presence gate never destroys the nav
    // controller — the active tab and back stack survive re-auth.
    val navController = rememberNavController()
    // Each start owes one update check, settled by whichever surface it lands on.
    val updateTurn = remember { UpdateCheckTurn() }
    LifecycleStartEffect(updateTurn) {
        updateTurn.onStart()
        onStopOrDispose { }
    }
    var showingScaffold = false
    when {
        step == FirstRun.Step.WELCOME -> {
            // No BackHandler by design: the branches around it map back to dismiss, which
            // returns the user to what they were doing, and there is nothing here to return to.
            // Back leaves the app and the welcome is owed again — only ending it writes it.
            WelcomeScreen(
                onFinished = { endWelcome(FirstRun.Exit.FINISHED) },
                onSkipped = { endWelcome(FirstRun.Exit.SKIPPED) },
            )
        }

        // The instant before the habits table has answered: nothing is drawn, rather than the app
        // drawn and then replaced by the naming step, or the gate opened ahead of it.
        step == FirstRun.Step.PENDING -> Unit

        step == FirstRun.Step.NAME_HABIT -> habits.needingName?.let { entry ->
            // No BackHandler, for the welcome's reason: there is nothing behind this to return to.
            // Keyed so a second unnamed habit would get fresh fields rather than this one's typing.
            key(entry.habit.id) { NameHabitScreen(entry = entry, viewModel = habitsViewModel) }
        }

        // A tap — a nudge, the widget, a pick — still deciding what it opens: nothing is drawn, for the
        // PENDING step's reason — the nav host drawn and then replaced could be tapped in between.
        resolvingTaps > 0 -> Unit

        // A check-in whose habit is still the user's to say: the picker stands where the gate will,
        // and a pick replaces it with the gate for that habit. Back drops the request, as on the gate.
        gateRequest.choosingHabit -> {
            BackHandler { PresenceCheckSignal.clear() }
            HabitPickerScreen(
                active = habits.active,
                onPick = onHabitPicked,
                onDismiss = { PresenceCheckSignal.clear() },
            )
        }

        gateRequest.reason != Reason.NONE -> {
            // Full-screen modal gate: the nav host is not composed underneath, so nothing behind
            // it is reachable by touch, accessibility focus, or the camera. Back dismisses the
            // gate rather than the (absent) host.
            BackHandler { PresenceCheckSignal.clear() }
            PresenceGate(
                onAuthSuccess = onGatePassed,
                onDismiss = { PresenceCheckSignal.clear() },
            )
        }

        else -> {
            // Gated on the step, not on sitting below the welcome branch: the ordering is the
            // reason the welcome exists, and enforced by branch position it is a rule no test
            // can see. Outside the gate too: PresenceGate raises its own request, and two stacked
            // system dialogs is a dialog the user can't answer.
            val askingNotifications = step == FirstRun.Step.ASK_NOTIFICATIONS
            if (askingNotifications) {
                NotificationPermissionOnFirstOpen(
                    settings,
                    onAsked = container.nudges::launchRearm,
                )
            }
            // Never in the same launch as that ask, so Play's dialog cannot land on top of it, and
            // never under the celebration.
            showingScaffold = true
            AppNavScaffold(
                navController,
                offerUpdates = !askingNotifications && completed == null,
                updateTurn = updateTurn,
            )
        }
    }
    if (!showingScaffold) ForfeitUpdateCheck(updateTurn)

    // Drawn over whatever is showing rather than replacing it: the celebration is a brief
    // acknowledgement, and tearing the host down for it would lose the tab the user was on. It
    // sits outside the switch because a check-out written from the notification resolves the
    // gate first, and the gate clears before the write it started completes.
    completed?.let {
        BackHandler { CheckOutSignal.clear() }
        CheckOutCelebration(completed = it, onDismiss = { CheckOutSignal.clear() })
    }
}

/**
 * Asks for `POST_NOTIFICATIONS` once, the first time the nav host is reached — after the welcome
 * tour and the naming step, never on the first frame of a fresh install.
 *
 * Nudges are on by default and the timer notification is the app's main surface, so waiting for
 * the first check-in to ask — which is when [PresenceGate] would — leaves a new user with every
 * notification silently dead until then, including the one telling them they haven't checked in.
 * Asking before the tour would raise the dialog before a word about the app had been on screen;
 * here it follows the tour and a reminder time the user has just chosen, so it has a reason by
 * the time it appears. Nothing else is asked at launch: the camera and its prominent disclosure
 * stay behind the gate, where a check actually needs them. A *skipped* welcome never reaches here
 * at all — see [FirstRun.asksNotificationsAfter].
 *
 * Asked once, tracked in prefs rather than inferred from the grant, because a refusal and a
 * never-asked look the same through `PackageManager` — and Android drops the dialog silently
 * after two refusals, so re-asking every cold start would be invisible noise. Declining costs
 * only notifications; every post is already guarded by `Notifier`. Below API 33 the permission
 * does not exist, so the flag is written and nothing is asked.
 *
 * [onAsked] runs once the flag is written: it ends first run, which is what the nudge layer waits on.
 */
@Composable
private fun NotificationPermissionOnFirstOpen(settings: PromptSettings, onAsked: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The outcome is deliberately unused: the grant is re-read wherever it matters, and a
        // refusal is a valid answer that must not be re-litigated on the next launch.
    }

    LaunchedEffect(Unit) {
        if (settings.hasAskedNotifications()) return@LaunchedEffect
        settings.markNotificationsAsked()
        onAsked()
        if (notificationsNeedRuntimeGrant() && !context.holdsNotificationPermission()) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
