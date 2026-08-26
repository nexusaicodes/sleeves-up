package com.checkin.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.ui.AppRoot
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.checkin.raiseCheckOutCelebration
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
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
                    AppRoot(onGatePassed = ::onRootGatePassed)
                }
            }
        }
    }

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
            container.sessionWatchdog.reviveIfNeeded()
        }
    }

    private fun handlePresenceIntent(intent: Intent?) {
        // One-shot: consume the extra so an Activity recreation (rotation, theme change) doesn't
        // replay the notification tap and re-open a gate the user already handled.
        when {
            intent?.getBooleanExtra(LaunchExtras.CHECK_OUT, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_OUT)
                requestPresenceCheck(Reason.CHECK_OUT)
            }
            intent?.getBooleanExtra(LaunchExtras.CHECK_IN, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_IN)
                // Retired on the tap rather than after the gate resolves: the notification has served
                // its purpose the moment it is tapped, whether or not the check-in that follows
                // completes. Nothing the app posts is autoCancel, so this cancel is the app's job.
                (application as CheckInApplication).container.postedNudges.retireAll()
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
                container.sessionLifecycleRunner.cancel()
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
                    container.sessionLifecycleRunner.arm(session.startedAt)
                    // A nudge asking for a check-in is stale the moment one happens. Left posted,
                    // tapping it later runs the full presence gate and resolves to nothing.
                    container.postedNudges.retireAll()
                }
            }
            Reason.NONE -> {}
        }
        PresenceCheckSignal.clear()
    }
}
