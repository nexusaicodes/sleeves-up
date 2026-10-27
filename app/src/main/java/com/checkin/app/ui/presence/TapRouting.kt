package com.checkin.app.ui.presence

import com.checkin.app.data.local.Habit

/** What a tap from outside the app — a nudge, the home-screen widget, a pick in the picker — resolves to. */
sealed interface TapOutcome {
    /** Nothing to ask for: the habit is archived or unknown, or its session is already open. */
    data object NoAction : TapOutcome

    /** Another habit's session is open. Said, and no gate opened: the repository would refuse it. */
    data class RunningElsewhere(val runningHabitId: Long) : TapOutcome

    /** Select [habitId], then open the gate for its check-in. */
    data class CheckIn(val habitId: Long) : TapOutcome

    /** Open the gate for the open session's check-out. */
    data object CheckOut : TapOutcome

    /** Open the picker: which habit the check-in is for is the user's to say. */
    data object ChooseHabit : TapOutcome
}

/**
 * The rules every tap from outside the app is routed by, read off the database at the moment of the
 * tap — never off whatever the surface that was tapped happened to show, since a notification or a
 * widget can be hours stale. Pure, so `TapRoutingTest` pins each branch;
 * [MainActivity][com.checkin.app.MainActivity] reads the rows and applies the outcome.
 *
 * **There is one open session across all habits**, which decides most of it: a check-in can only be
 * asked for when nothing is open, and while something is, the only gated action left is its check-out.
 */
object TapRouting {

    /**
     * A tap about one habit: a nudge for it, or a row picked in the picker. [habit] is null when no
     * habit carries the tapped uuid — a restored database can orphan one — which asks for nothing.
     */
    fun forHabit(habit: Habit?, runningHabitId: Long?): TapOutcome = when {
        habit == null || habit.archived -> TapOutcome.NoAction
        // Already checked in since the surface was drawn: the tap has nothing left to ask for.
        runningHabitId == habit.id -> TapOutcome.NoAction
        runningHabitId != null -> TapOutcome.RunningElsewhere(runningHabitId)
        else -> TapOutcome.CheckIn(habit.id)
    }

    /**
     * The widget's single button, which names no habit: the open session's check-out if there is one,
     * otherwise a check-in — straight to the gate when there is only one habit it could be for, and
     * through the picker when there is a choice to make. [active] is the active habits, any order.
     */
    fun forWidget(active: List<Habit>, runningHabitId: Long?): TapOutcome = when {
        runningHabitId != null -> TapOutcome.CheckOut
        active.size == 1 -> forHabit(active.single(), runningHabitId = null)
        active.isEmpty() -> TapOutcome.NoAction
        else -> TapOutcome.ChooseHabit
    }
}
