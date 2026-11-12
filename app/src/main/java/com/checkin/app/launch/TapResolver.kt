package com.checkin.app.launch

import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.notify.nudge.PostedNudges
import com.checkin.app.time.TimeSource
import com.checkin.app.ui.presence.PresenceCheckSignal
import com.checkin.app.ui.presence.PresenceCheckSignal.Reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** What a tap could not do, said where the user is. Called on [TapResolver]'s notice context. */
interface TapNotices {
    /** The tapped habit cannot be checked in to: [runningName]'s session is open (null when unnamed). */
    fun runningElsewhere(runningName: String?)

    /** The tap could not be resolved; nothing was raised or written, so it can be made again. */
    fun notResolved()
}

/**
 * Decides and applies what a tap from outside the app opens: a nudge, the home-screen widget, or a
 * pick in the widget's habit picker. Every such tap is decided here, at the moment of the tap, by
 * [TapRouting] against the rows as they are now — never by what the tapped surface showed, which can
 * be hours stale.
 *
 * The order inside each is the point: the rows are read before a nudge is retired, so a failed read
 * leaves the nudge to tap again, and a habit is selected before its gate is raised, so the screen
 * behind the gate is the habit the tap was about.
 *
 * Runs on the application [scope], which has no exception handler, so nothing here throws: a failure
 * raises nothing, writes nothing and reports [TapNotices.notResolved]. [PresenceCheckSignal.resolvingTaps]
 * is raised for the whole of each resolution, so the root draws nothing (and a cold start keeps its
 * splash screen) until the outcome lands.
 */
class TapResolver(
    private val habits: HabitRepository,
    private val sessions: CheckInRepository,
    private val postedNudges: PostedNudges,
    private val timeSource: TimeSource,
    private val scope: CoroutineScope,
    private val notices: TapNotices,
    /** Where [notices] are called: the main thread in the app, since they show toasts. */
    private val noticeContext: CoroutineContext,
) {

    /**
     * A nudge was tapped, about one habit. The nudge is retired on the tap rather than after the gate
     * resolves: it has served its purpose the moment it is tapped, whether or not the check-in that
     * follows completes, and nothing the app posts is autoCancel. A nudge posted before habits existed
     * carries no [habitUuid] and is about the seed habit.
     */
    fun nudgeTapped(habitUuid: String?) = resolve {
        val habit = if (habitUuid == null) habits.byId(Habit.SEED_ID) else habits.byUuid(habitUuid)
        val runningHabitId = sessions.getActiveSession()?.habitId
        habit?.let { postedNudges.retire(it.id) }
        TapRouting.forHabit(habit, runningHabitId)
    }

    /**
     * The home-screen widget's button, which names no habit: the open session's check-out, the gate
     * for the only habit, or the picker — read off the rows now, whatever the widget was showing.
     */
    fun widgetTapped() = resolve {
        TapRouting.forWidget(habits.activeHabits(), sessions.getActiveSession()?.habitId)
    }

    /**
     * A habit was picked in the picker: routed exactly as a nudge for that habit would be, so the
     * same checks run against the rows as they are now. The picker's request is dropped first — the
     * root draws nothing while the pick resolves — so an outcome that opens no gate lands in the app
     * rather than leaving the picker up. A second pick landing before the first has resolved is
     * ignored, since it would race the first to the gate.
     */
    fun habitPicked(habitId: Long) {
        if (PresenceCheckSignal.resolvingTaps.value > 0) return
        resolve {
            PresenceCheckSignal.clear()
            TapRouting.forHabit(habits.byId(habitId), sessions.getActiveSession()?.habitId)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun resolve(decide: suspend () -> TapOutcome) {
        // Raised before the launch, so not one frame of the nav host is drawn in between.
        PresenceCheckSignal.resolvingTaps.update { it + 1 }
        scope.launch {
            try {
                apply(decide())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(noticeContext) { notices.notResolved() }
            } finally {
                PresenceCheckSignal.resolvingTaps.update { it - 1 }
            }
        }
    }

    private suspend fun apply(outcome: TapOutcome) {
        when (outcome) {
            TapOutcome.NoAction -> Unit
            is TapOutcome.RunningElsewhere -> {
                val runningName = habits.byId(outcome.runningHabitId)?.name
                withContext(noticeContext) { notices.runningElsewhere(runningName) }
            }
            is TapOutcome.CheckIn ->
                // A refused select means a session opened since the routing read; the repository's
                // own check would refuse the check-in anyway.
                if (habits.select(outcome.habitId)) {
                    PresenceCheckSignal.raise(Reason.CHECK_IN, timeSource.nowMillis(), outcome.habitId)
                } else {
                    withContext(noticeContext) { notices.notResolved() }
                }
            TapOutcome.CheckOut -> PresenceCheckSignal.raise(Reason.CHECK_OUT_FROM_WIDGET, timeSource.nowMillis())
            TapOutcome.ChooseHabit -> PresenceCheckSignal.choose(timeSource.nowMillis())
        }
    }
}
