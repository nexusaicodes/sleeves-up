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
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.ui.AppRoot
import com.checkin.app.ui.checkin.CheckOutSignal
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import com.checkin.app.ui.presence.PresenceGate
import com.checkin.app.ui.presence.TapOutcome
import com.checkin.app.ui.presence.TapRouting
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.util.latinDigitsOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val container by lazy { (application as CheckInApplication).container }

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
                    AppRoot(onGatePassed = ::onRootGatePassed, onHabitPicked = ::onHabitPicked)
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
        // The animation setting has no change signal and is changed in system settings, so a return
        // from there is the first moment the widget's cycling can follow it.
        container.applicationScope.launch {
            container.widgetSync.redraw(force = false)
        }
    }

    private fun handlePresenceIntent(intent: Intent?) {
        // One-shot: consume the extra so an Activity recreation (rotation, theme change) doesn't
        // replay the tap and re-open a gate the user already handled.
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
                resolveTap { onNudgeTapped(habitUuid) }
            }
            intent?.getBooleanExtra(LaunchExtras.WIDGET_TAP, false) == true -> {
                intent.removeExtra(LaunchExtras.WIDGET_TAP)
                resolveTap { onWidgetTapped() }
            }
        }
    }

    /**
     * Reads the database to decide what a tap from outside the app opens, then applies it. Every such
     * tap is decided here, at the moment of the tap, by [TapRouting] — never by what the tapped surface
     * showed, which can be hours stale.
     *
     * [PresenceCheckSignal.resolvingTaps] is raised before the launch, so not one frame of the nav host
     * is drawn in between (and the splash screen stays up on a cold start). Failures are swallowed:
     * the application scope has no handler.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun resolveTap(decide: suspend () -> TapOutcome) {
        PresenceCheckSignal.resolvingTaps.update { it + 1 }
        container.applicationScope.launch {
            try {
                applyTap(decide())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing raised; a failed read leaves the nudge posted to tap again.
            } finally {
                PresenceCheckSignal.resolvingTaps.update { it - 1 }
            }
        }
    }

    /**
     * A nudge was tapped, about one habit. The nudge is retired on the tap rather than after the gate
     * resolves: it has served its purpose the moment it is tapped, whether or not the check-in that
     * follows completes, and nothing the app posts is autoCancel. A nudge posted before habits existed
     * carries no habit and is about the seed habit.
     */
    private suspend fun onNudgeTapped(habitUuid: String?): TapOutcome {
        val habits = container.habitRepository
        val habit = if (habitUuid == null) habits.byId(Habit.SEED_ID) else habits.byUuid(habitUuid)
        // Reads before the retire, so a failed read leaves the nudge to tap again.
        val runningHabitId = container.repository.getActiveSession()?.habitId
        habit?.let { container.postedNudges.retire(it.id) }
        return TapRouting.forHabit(habit, runningHabitId)
    }

    /**
     * The home-screen widget's button, which names no habit: the open session's check-out, the gate
     * for the only habit, or the picker — read off the rows now, whatever the widget was showing.
     */
    private suspend fun onWidgetTapped(): TapOutcome = TapRouting.forWidget(
        container.habitRepository.activeHabits(),
        container.repository.getActiveSession()?.habitId,
    )

    /**
     * A habit was picked in the picker: routed exactly as a nudge for that habit would be, so the
     * same checks run against the rows as they are now. The picker's request is dropped first — the
     * root draws nothing while the pick resolves — so an outcome that opens no gate lands in the app
     * rather than leaving the picker up.
     */
    private fun onHabitPicked(habitId: Long) {
        resolveTap {
            PresenceCheckSignal.clear()
            TapRouting.forHabit(
                container.habitRepository.byId(habitId),
                container.repository.getActiveSession()?.habitId,
            )
        }
    }

    private suspend fun applyTap(outcome: TapOutcome) {
        when (outcome) {
            TapOutcome.NoAction -> Unit
            is TapOutcome.RunningElsewhere -> {
                val runningName = container.habitRepository.byId(outcome.runningHabitId)?.name
                withContext(Dispatchers.Main) { showRunningElsewhere(runningName) }
            }
            is TapOutcome.CheckIn -> {
                // Selected before the gate opens, so the screen behind it — and the one the user lands
                // on after it — is the habit the tap was about. A refusal means a session opened since
                // the routing read; the repository's own check would refuse the check-in anyway.
                if (container.habitRepository.select(outcome.habitId)) {
                    PresenceCheckSignal.raise(Reason.CHECK_IN, container.timeSource.nowMillis(), outcome.habitId)
                }
            }
            TapOutcome.CheckOut ->
                PresenceCheckSignal.raise(Reason.CHECK_OUT_FROM_WIDGET, container.timeSource.nowMillis())
            TapOutcome.ChooseHabit -> PresenceCheckSignal.choose(container.timeSource.nowMillis())
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
