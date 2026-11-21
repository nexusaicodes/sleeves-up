package com.checkin.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.surface.Surface
import com.checkin.app.launch.TapNotices
import com.checkin.app.launch.TapResolver
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.ui.AppRoot
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.fadeIntoApp
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import com.checkin.app.ui.presence.PresenceGate
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.util.latinDigitsOverride
import com.checkin.app.util.useLatinDigitsByDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val container by lazy { (application as CheckInApplication).container }

    /** Decides every tap from outside the app; the toasts it needs are this activity's. */
    private val tapResolver by lazy {
        TapResolver(
            habits = container.habitRepository,
            sessions = container.checkInRepository,
            postedNudges = container.postedNudges,
            timeSource = container.timeSource,
            scope = container.applicationScope,
            notices = object : TapNotices {
                override fun runningElsewhere(runningName: String?) = showRunningElsewhere(runningName)
                override fun notResolved() = showTapNotResolved()
            },
            noticeContext = Dispatchers.Main,
        )
    }

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
        installSplashScreen().apply {
            setKeepOnScreenCondition { PresenceCheckSignal.resolvingTaps.value > 0 }
            fadeIntoApp(this@MainActivity)
        }
        super.onCreate(savedInstanceState)
        useLatinDigitsByDefault()
        // Transparent bars on every level: below 35 the platform otherwise keeps the theme's opaque ones.
        enableEdgeToEdge()

        // A restored activity, or one reopened from Recents, carries the intent that first launched
        // the task, extras and all: that tap was handled when it was made.
        if (savedInstanceState == null && !intent.launchedFromHistory()) handlePresenceIntent(intent)

        setContent {
            CheckInAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SparkTheme.colors.background,
                ) {
                    AppRoot(onGatePassed = ::onRootGatePassed, onHabitPicked = tapResolver::habitPicked)
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
        // One-shot: consume the extra so nothing reading this intent again acts on the tap twice.
        when {
            intent?.getBooleanExtra(LaunchExtras.CHECK_OUT_FROM_TIMER, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_OUT_FROM_TIMER)
                requestPresenceCheck(Reason.CHECK_OUT_FROM_TIMER)
            }
            intent?.getBooleanExtra(LaunchExtras.CHECK_OUT_FROM_REMINDER, false) == true -> {
                intent.removeExtra(LaunchExtras.CHECK_OUT_FROM_REMINDER)
                requestPresenceCheck(Reason.CHECK_OUT_FROM_REMINDER)
            }
            intent?.getBooleanExtra(LaunchExtras.NUDGE_TAP, false) == true -> {
                intent.removeExtra(LaunchExtras.NUDGE_TAP)
                val habitUuid = intent.getStringExtra(LaunchExtras.HABIT_UUID)
                intent.removeExtra(LaunchExtras.HABIT_UUID)
                tapResolver.nudgeTapped(habitUuid)
            }
            intent?.getBooleanExtra(LaunchExtras.WIDGET_TAP, false) == true -> {
                intent.removeExtra(LaunchExtras.WIDGET_TAP)
                tapResolver.widgetTapped()
            }
        }
    }

    private fun Intent.launchedFromHistory() = (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0

    /**
     * A toast rather than a snackbar: the tap can land on any surface, including the welcome tour and
     * the naming step, and only the nav host carries a snackbar host.
     */
    private fun showRunningElsewhere(runningName: String?) {
        val message = runningName?.let { getString(R.string.habit_running_check_out_first, it) }
            ?: getString(R.string.habit_running_check_out_first_unnamed)
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun showTapNotResolved() {
        Toast.makeText(applicationContext, R.string.tap_not_resolved, Toast.LENGTH_LONG).show()
    }

    /**
     * Raises the gate unconditionally. [PresenceGate] owns the disclosure and both permissions, so
     * there is no screen a request can arrive behind and queue up on — it always opens on the spot.
     * What it can still do is sit unanswered if the user walks away from it, which is what the
     * timestamp is for; [onStart] retires anything stale before it can reopen.
     */
    private fun requestPresenceCheck(reason: Reason) {
        PresenceCheckSignal.raise(reason, container.timeSource.nowMillis())
    }

    /** Resolves the root gate: a check-out request closes the session, and a check-in request opens one. */
    private fun onRootGatePassed() {
        val request = PresenceCheckSignal.request.value
        // Exhaustive, so a new Reason does not compile until it is given a meaning here.
        when (val reason = request.reason) {
            // Every check-out reason differs only in the ending the row records, which the Reason
            // itself carries.
            Reason.CHECK_OUT_FROM_TIMER, Reason.CHECK_OUT_FROM_REMINDER, Reason.CHECK_OUT_FROM_WIDGET ->
                container.sessionActions.checkOut(requireNotNull(reason.closedBy) { "$reason carries no ending" })
            // The repository re-checks for an open session: the user may have checked in — to this
            // habit or another — between the gate opening and passing.
            Reason.CHECK_IN -> request.habitId?.let(container.sessionActions::checkIn)
            Reason.NONE -> {}
        }
        PresenceCheckSignal.clear()
    }
}
