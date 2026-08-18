package com.checkin.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.checkin.app.notify.EngagementTag
import com.checkin.app.service.CheckInService
import com.checkin.app.service.PresenceCheckSignal
import com.checkin.app.service.PresenceCheckSignal.Reason
import com.checkin.app.ui.checkin.CheckOutCelebration
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.checkin.raiseCheckOutCelebration
import com.checkin.app.ui.navigation.AppNavScaffold
import com.checkin.app.ui.presence.PresenceGate
import com.checkin.app.ui.theme.CheckInAppTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handlePresenceIntent(intent)

        setContent {
            CheckInAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val gateReason by PresenceCheckSignal.request.collectAsStateWithLifecycle()
                    val completed by CheckOutSignal.completed.collectAsStateWithLifecycle()

                    // Hoisted above the gate/host switch so entering and leaving the presence gate
                    // never destroys the nav controller — the active tab and back stack survive
                    // re-auth.
                    val navController = rememberNavController()
                    if (gateReason != Reason.NONE) {
                        // Full-screen modal gate: the nav host is not composed underneath, so
                        // nothing behind it is reachable by touch, accessibility focus, or the
                        // camera. Back dismisses the gate rather than the (absent) host.
                        BackHandler { PresenceCheckSignal.clear() }
                        PresenceGate(
                            onAuthSuccess = { onRootGatePassed() },
                            onDismiss = { PresenceCheckSignal.clear() },
                        )
                    } else {
                        // Only outside the gate: PresenceGate raises its own request, and two system
                        // dialogs stacked on one another is a dialog the user can't answer.
                        NotificationPermissionOnFirstOpen()
                        AppNavScaffold(navController)
                    }

                    // Drawn over whatever is showing rather than replacing it: the celebration is a
                    // brief acknowledgement, and tearing the host down for it would lose the tab the
                    // user was on. It sits outside the gate branch because a check-out written from
                    // the notification resolves the gate first, and the gate clears before the
                    // write it started completes.
                    completed?.let {
                        BackHandler { CheckOutSignal.clear() }
                        CheckOutCelebration(completed = it, onDismiss = { CheckOutSignal.clear() })
                    }
                }
            }
        }
    }

    /**
     * Asks for `POST_NOTIFICATIONS` once, on the first launch that reaches the nav host.
     *
     * Nudges are on by default and the timer notification is the app's main surface, so waiting for
     * the first check-in to ask — which is when [PresenceGate] would — leaves a new user with every
     * notification silently dead until then, including the one telling them they haven't checked in.
     * Nothing else is asked at launch: the camera and its prominent disclosure stay behind the gate,
     * where a check actually needs them.
     *
     * Asked once, tracked in prefs rather than inferred from the grant, because a refusal and a
     * never-asked look the same through `PackageManager` — and Android drops the dialog silently
     * after two refusals, so re-asking every cold start would be invisible noise. Declining costs
     * only notifications; every post is already guarded by `Notifier`.
     */
    @Composable
    private fun NotificationPermissionOnFirstOpen() {
        val settings = remember { (application as CheckInApplication).container.settings }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The outcome is deliberately unused: the grant is re-read wherever it matters, and a
            // refusal is a valid answer that must not be re-litigated on the next launch.
        }

        LaunchedEffect(Unit) {
            if (settings.hasAskedNotifications()) return@LaunchedEffect
            settings.markNotificationsAsked()
            if (!hasNotificationPermission()) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePresenceIntent(intent)
    }

    /**
     * Drops a gate the user opened and walked away from, and a celebration nobody was there to see.
     *
     * Checked on every start rather than on stop, because leaving is exactly what the gate does on
     * its legitimate path: the camera-recovery screen sends the user to system settings and expects
     * them back. That round trip is seconds; an abandoned request is hours, and only the clock can
     * tell the two apart. Both signals are process-global and outlive the composition, so this is
     * the one place either can be retired.
     */
    override fun onStart() {
        super.onStart()
        val container = (application as CheckInApplication).container
        val now = container.timeSource.nowMillis()
        PresenceCheckSignal.expireIfStale(now)
        // Same hazard, shorter fuse: nothing dismisses the celebration on a timer, so one raised as
        // the app was being backgrounded would otherwise be waiting on the next launch.
        CheckOutSignal.expireIfStale(now)

        // The most reliable revive point there is: a visible Activity is always allowed to start a
        // foreground service, where the background callers may be refused. Without it, opening the
        // app on a session whose service was killed shows a running timer — rendered from the row —
        // with nothing behind it, and the session stays lost.
        container.applicationScope.launch {
            container.sessionWatchdog.reviveIfNeeded(source = "app open")
        }
    }

    private fun handlePresenceIntent(intent: Intent?) {
        // One-shot: consume the extra so an Activity recreation (rotation, theme change) doesn't
        // replay the notification tap and re-open a gate the user already handled.
        when {
            intent?.getBooleanExtra(CheckInService.EXTRA_CHECK_OUT, false) == true -> {
                intent.removeExtra(CheckInService.EXTRA_CHECK_OUT)
                requestPresenceCheck(Reason.CHECK_OUT)
            }
            intent?.getBooleanExtra(CheckInService.EXTRA_CHECK_IN, false) == true -> {
                intent.removeExtra(CheckInService.EXTRA_CHECK_IN)
                // Carried by the notification itself, so the open is attributed to the one tapped
                // rather than to whichever the log holds as most recently shown. Absent on a
                // notification posted by a release that predates the tag; the reporter falls back.
                val key = intent.getStringExtra(EngagementTag.EXTRA_KEY)
                val variant = intent.getIntExtra(EngagementTag.EXTRA_VARIANT, 0)
                // The tap itself is worth recording even when the gate can't run — it is what the
                // user did with the notification, not what the app managed to do about it.
                (application as CheckInApplication).container.let { container ->
                    container.applicationScope.launch {
                        container.engagementReporter.onNudgeOpened(container.timeSource.nowMillis(), key, variant)
                    }
                }
                requestPresenceCheck(Reason.CHECK_IN)
            }
        }
    }

    /**
     * Raises the gate unconditionally. [PresenceGate] owns the disclosure and both permissions, so
     * there is no screen a request can arrive behind and queue up on — it always opens on the spot.
     * What it can still do is sit unanswered if the user walks away from it, which is what the
     * timestamp is for; [onStart] retires anything stale before it can reopen.
     */
    private fun requestPresenceCheck(reason: Reason) {
        val container = (application as CheckInApplication).container
        PresenceCheckSignal.raise(reason, container.timeSource.nowMillis())
    }

    /** Resolves the root gate: a check-out request closes the session, and a nudge tap opens one. */
    private fun onRootGatePassed() {
        val container = (application as CheckInApplication).container
        when (PresenceCheckSignal.request.value) {
            Reason.CHECK_OUT -> container.applicationScope.launch {
                val closed = container.repository.checkOutActiveSession()
                // Before stop(), which is a no-op when the service has already been killed and
                // would otherwise leave both alarms standing over a closed session.
                container.sessionReminderRunner.cancel()
                container.serviceController.stop()
                // The Check-In screen's button raises this too: a check-out from the notification
                // earned the same acknowledgement as one made from inside the app.
                closed?.let {
                    raiseCheckOutCelebration(container.repository, it, container.timeSource.nowMillis())
                }
            }
            Reason.CHECK_IN -> container.applicationScope.launch {
                // Guard against a stale nudge: the user may have already checked in between the
                // notification being posted and being tapped.
                if (container.repository.getActiveSession() == null) {
                    val session = container.repository.checkIn()
                    container.serviceController.startTimer(session.id, session.startedAt)
                    // Armed by the writer, not the service: a refused foreground start must not
                    // cost the session its day-boundary close.
                    container.sessionReminderRunner.arm(session.startedAt)
                    container.engagementReporter.onCheckedIn(session.startedAt)
                }
            }
            Reason.NONE -> {}
        }
        PresenceCheckSignal.clear()
    }
}
