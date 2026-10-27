package com.checkin.app.widget

import com.checkin.app.data.local.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * What the home-screen widget shows — and, as importantly, all it ever shows: the habits' names and
 * whether a session is open. **No record**: no today state, no count, no hours, no timer. A widget
 * listing every habit beside how each one's day went would rank them, and a figure that sits on the
 * home screen all day is read as a score.
 *
 * It is a picture only. The widget's one button is routed at the tap by
 * [TapRouting][com.checkin.app.ui.presence.TapRouting] against the rows as they are then, so a face
 * drawn before a change it has not caught up with can mislead nobody into a wrong write.
 *
 * A name of `null` is a habit still owed one, drawn as plain "Check In". Names keep the order they
 * are given — creation order, the switcher's — and each holds the flipper for the same interval, so
 * no habit's place in the cycle says anything.
 */
sealed interface WidgetFace {
    /** More than one habit, animations on: "Check in to …" cycles through every name. */
    data class Cycling(val names: List<String?>) : WidgetFace

    /** The one active habit, named outright. */
    data class Single(val name: String?) : WidgetFace

    /** A session is open: "Check out of …", with the mark at full colour. */
    data class Running(val name: String?) : WidgetFace

    /** More than one habit with animations off — a still "Check In", since a cycle is motion. */
    data object Plain : WidgetFace

    companion object {
        /** [active] is the active habits in display order; [runningHabitId] is the open session's habit. */
        fun of(active: List<Habit>, runningHabitId: Long?, animationsOn: Boolean): WidgetFace = when {
            runningHabitId != null -> Running(active.firstOrNull { it.id == runningHabitId }?.name)
            active.size == 1 -> Single(active.single().name)
            active.size > 1 && animationsOn -> Cycling(active.map { it.name })
            else -> Plain
        }

        /**
         * The face for every change to the habits, the open session or the animation setting. Not
         * deduplicated here: `WidgetSync` skips a face identical to the one on screen, which a dedupe
         * against this flow's own last emission cannot know.
         */
        fun faces(
            activeHabits: Flow<List<Habit>>,
            runningHabitId: Flow<Long?>,
            animationsOn: Flow<Boolean>,
        ): Flow<WidgetFace> = combine(activeHabits, runningHabitId, animationsOn) { habits, running, animations ->
            of(habits, running, animations)
        }
    }
}
