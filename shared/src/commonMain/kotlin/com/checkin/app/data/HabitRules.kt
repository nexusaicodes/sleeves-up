package com.checkin.app.data

/** Why a habit name was refused. */
enum class NameProblem { BLANK, TOO_LONG, DUPLICATE }

/**
 * The pure rules about habits: what a name may be, and which habit the app is showing. No DAO, no
 * clock, so `HabitRulesTest` can pin both exhaustively; the Android app's `HabitRepository` applies
 * them. They read a habit through [HabitFacts], so either app's stored type can be passed as it is.
 */
object HabitRules {

    /**
     * Long enough for any name a person gives a habit, short enough that "Check in to …" stays a
     * button label and "… Reminders" stays a channel name rather than wrapping into a paragraph.
     * Counted in code points of the normalised name, so an emoji is one character and the spaces
     * [normalise] strips count for nothing.
     */
    const val MAX_NAME_LENGTH = 25

    /**
     * The stored form of a name: trimmed, and with runs of ASCII whitespace collapsed to one space.
     * Inner non-ASCII spaces (a no-break space, an em space) are kept as typed.
     */
    fun normalise(name: String): String = name.trim().replace(WHITESPACE, " ")

    /**
     * What is wrong with [name], or null when it may be stored on habit [forId] (null for a new
     * habit). Unique among [activeHabits] ignoring case, because "Gym" and "gym" in one switcher are
     * the same habit to anyone reading it; archived habits do not count, since they are not in the
     * switcher — restoring one into a clash is refused at the restore instead.
     */
    fun nameProblem(name: String, activeHabits: List<HabitFacts>, forId: Long? = null): NameProblem? {
        val normalised = normalise(name)
        return when {
            normalised.isEmpty() -> NameProblem.BLANK
            codePointCount(normalised) > MAX_NAME_LENGTH -> NameProblem.TOO_LONG
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
    fun <H : HabitFacts> effectiveSelection(storedId: Long?, runningHabitId: Long?, activeHabits: List<H>): H? =
        activeHabits.firstOrNull { it.id == runningHabitId }
            ?: activeHabits.firstOrNull { it.id == storedId }
            ?: activeHabits.firstOrNull()

    /**
     * Java's `\s`, spelled out: Kotlin/Native's regex engine is not Java's, and naming the class keeps
     * both apps collapsing exactly the same characters.
     */
    private val WHITESPACE = Regex("[ \t\n\u000B\u000C\r]+")

    /**
     * Code points in [text], as Java's `String.codePointCount` counts them: a surrogate pair is one,
     * and an unpaired surrogate is one on its own. The standard library has no common equivalent.
     */
    private fun codePointCount(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val pair = text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
            i += if (pair) 2 else 1
            count++
        }
        return count
    }
}
