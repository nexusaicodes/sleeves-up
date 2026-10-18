package com.checkin.app.data

import com.checkin.app.data.local.Habit

/** Why a habit name was refused. */
enum class NameProblem { BLANK, TOO_LONG, DUPLICATE }

/**
 * The pure rules about habits: what a name may be, and which habit the app is showing. No DAO, no
 * clock, so `HabitRulesTest` can pin both exhaustively; [com.checkin.app.data.repository.HabitRepository]
 * applies them.
 */
object HabitRules {

    /**
     * Long enough for any name a person gives a habit, short enough that "Check in to …" stays a
     * button label and "… Reminders" stays a channel name rather than wrapping into a paragraph.
     */
    const val MAX_NAME_LENGTH = 25

    /** The stored form of a name: trimmed, and with runs of whitespace collapsed to one space. */
    fun normalise(name: String): String = name.trim().replace(WHITESPACE, " ")

    /**
     * What is wrong with [name], or null when it may be stored on habit [forId] (null for a new
     * habit). Unique among [activeHabits] ignoring case, because "Gym" and "gym" in one switcher are
     * the same habit to anyone reading it; archived habits do not count, since they are not in the
     * switcher — restoring one into a clash is refused at the restore instead.
     */
    fun nameProblem(name: String, activeHabits: List<Habit>, forId: Long? = null): NameProblem? {
        val normalised = normalise(name)
        return when {
            normalised.isEmpty() -> NameProblem.BLANK
            normalised.length > MAX_NAME_LENGTH -> NameProblem.TOO_LONG
            activeHabits.any { it.id != forId && it.name.equals(normalised, ignoreCase = true) } ->
                NameProblem.DUPLICATE
            else -> null
        }
    }

    /**
     * The habit every screen shows: the one with the open session if there is one, otherwise the
     * stored selection, otherwise the first active habit.
     *
     * **The open session wins, and that is the switcher lock.** There is one open session across all
     * habits, so while it runs the app is about that habit on every tab; showing another would put a
     * Check In button on screen for a habit the repository would then refuse. The fallback covers a
     * stored selection that has since been archived. Null only when no habit is active, which the
     * archive rules make unreachable.
     */
    fun effectiveSelection(storedId: Long?, runningHabitId: Long?, activeHabits: List<Habit>): Habit? =
        activeHabits.firstOrNull { it.id == runningHabitId }
            ?: activeHabits.firstOrNull { it.id == storedId }
            ?: activeHabits.firstOrNull()

    private val WHITESPACE = Regex("\\s+")
}
