package com.checkin.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInResult
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.ui.AppRoot
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.checkin.raiseCheckOutCelebration
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import com.checkin.app.ui.presence.PresenceGate
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.util.latinDigitsOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    /**
     * Every string this activity formats numbers in Latin digits, whatever the language — see
     * `util/LatinDigits` for why. An override configuration rather than a wrapped context, so the
     * activity's own resources carry it and Compose's `stringResource` and plurals pick it up with
     * no call site involved. A language change recreates the activity, which re-applies it.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(latinDigitsOverride(newBase.resources.configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { PresenceCheckSignal.resolvingTaps.value > 0 }
        super.onCreate(savedInstanceState)
        // Transparent bars on every level: below 35 the platform otherwise keeps the theme's opaque ones.
        enableEdgeToEdge()

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
        // foreground service, where the background callers may be refused (see SessionWatchdog).
        container.applicationScope.launch {
            container.sessionWatchdog.reviveIfNeeded()
        }
    }

    private fun handlePresenceIntent(intent: Intent?) {
        // One-shot: consume the extra so an Activity recreation (rotation, theme change) doesn't
        // replay the notification tap and re-open a gate the user already handled.
        when {
            intent?.getBooleanExtra(LaunchExtras.CHECK_OUT_FROM_TIMER, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_OUT_FROM_TIMER)
                requestPresenceCheck(Reason.CHECK_OUT_FROM_TIMER)
            }
            intent?.getBooleanExtra(LaunchExtras.CHECK_OUT_FROM_REMINDER, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_OUT_FROM_REMINDER)
                requestPresenceCheck(Reason.CHECK_OUT_FROM_REMINDER)
            }
            intent?.getBooleanExtra(LaunchExtras.CHECK_IN, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_IN)
                val habitUuid = intent.getStringExtra(LaunchExtras.HABIT_UUID)
                intent.removeExtra(LaunchExtras.HABIT_UUID)
                val container = (application as CheckInApplication).container
                // Set before the launch, so not one frame of the nav host is drawn in between.
                PresenceCheckSignal.resolvingTaps.update { it + 1 }
                container.applicationScope.launch {
                    try {
                        onNudgeTapped(habitUuid)
                    } finally {
                        PresenceCheckSignal.resolvingTaps.update { it - 1 }
                    }
                }
            }
        }
    }

    /**
     * A nudge was tapped: open the gate for that habit's check-in, unless another habit's session is
     * running — then say so and open nothing, since there is one open session across all habits and
     * the gate would end in a check-in the repository refuses.
     *
     * The nudge is retired on the tap rather than after the gate resolves: it has served its purpose
     * the moment it is tapped, whether or not the check-in that follows completes. Nothing the app
     * posts is autoCancel, so this cancel is the app's job. A nudge posted before habits existed
     * carries no habit and is about the seed habit; one for a habit since archived opens nothing, and
     * so does one whose habit is not in the database at all — it is about no habit there is.
     */
    private suspend fun onNudgeTapped(habitUuid: String?) {
        val container = (application as CheckInApplication).container
        val habits = container.habitRepository
        val habit = (if (habitUuid == null) habits.byId(Habit.SEED_ID) else habits.byUuid(habitUuid)) ?: return
        container.postedNudges.retire(habit.id)
        if (habit.archived) return
        val running = container.repository.getActiveSession()
        when {
            // Already checked in since the nudge was posted: the tap has nothing left to ask for.
            running?.habitId == habit.id -> Unit
            running != null -> {
                val runningName = habits.byId(running.habitId)?.name
                withContext(Dispatchers.Main) { showRunningElsewhere(runningName) }
            }
            else -> {
                // Selected before the gate opens, so the screen behind it — and the one the user
                // lands on after it — is the habit the nudge was about.
                habits.select(habit.id)
                PresenceCheckSignal.raise(Reason.CHECK_IN, container.timeSource.nowMillis(), habit.id)
            }
        }
    }

    /**
     * A toast rather than a snackbar: the tap can land on any surface, including the welcome tour and
     * the naming step, and only the nav host carries a snackbar host.
     */
    private fun showRunningElsewhere(runningName: String?) {
        val message = runningName?.let { getString(R.string.habit_running_check_out_first, it) }
            ?: getString(R.string.habit_running_check_out_first_unnamed)
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
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
        val request = PresenceCheckSignal.request.value
        when (val reason = request.reason) {
            // Both check-out reasons take the same path and differ only in the ending the row
            // records, which the Reason itself carries.
            Reason.CHECK_OUT_FROM_TIMER, Reason.CHECK_OUT_FROM_REMINDER -> container.applicationScope.launch {
                val closed = container.repository.checkOutActiveSession(reason.closedBy!!)
                // Before stop(), which is a no-op when the service has already been killed and
                // would otherwise leave both alarms standing over a closed session.
                container.sessionLifecycleRunner.cancel()
                container.serviceController.stop()
                // The Check-In screen's button raises this too: a check-out from the notification
                // earned the same acknowledgement as one made from inside the app.
                closed?.let {
                    raiseCheckOutCelebration(
                        container.repository,
                        container.habitRepository,
                        it,
                        container.timeSource.nowMillis(),
                    )
                }
            }
            Reason.CHECK_IN -> request.habitId?.let { habitId ->
                container.applicationScope.launch {
                    // The repository re-checks for an open session: the user may have checked in —
                    // to this habit or another — between the gate opening and passing. Only a new
                    // session is started and armed.
                    val result = container.repository.checkIn(habitId)
                    if (result is CheckInResult.Opened) {
                        val session = result.session
                        container.serviceController.startTimer(session.id, session.startedAt)
                        // Armed by the writer, not the service: a refused foreground start must not
                        // cost the session its day-boundary close.
                        container.sessionLifecycleRunner.arm(session.startedAt)
                        // A nudge asking for a check-in is stale the moment one happens. Left posted,
                        // tapping it later runs the full presence gate and resolves to nothing.
                        container.postedNudges.retire(habitId)
                    }
                }
            }
            Reason.NONE -> {}
        }
        PresenceCheckSignal.clear()
    }
}
