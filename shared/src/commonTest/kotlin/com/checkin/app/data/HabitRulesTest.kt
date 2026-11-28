package com.checkin.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HabitRulesTest {

    private data class TestHabit(
        override val id: Long,
        override val name: String?,
        override val archived: Boolean = false,
    ) : HabitFacts {
        override val uuid: String = "u$id"
    }

    private fun habit(id: Long, name: String?, archived: Boolean = false) = TestHabit(id, name, archived)

    @Test
    fun `names are trimmed and their inner whitespace collapsed`() {
        assertEquals("Morning run", HabitRules.normalise("  Morning \t  run "))
    }

    @Test
    fun `a blank or over-long name is refused`() {
        assertEquals(NameProblem.BLANK, HabitRules.nameProblem("   ", emptyList()))
        assertEquals(
            NameProblem.TOO_LONG,
            HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH + 1), emptyList()),
        )
        assertNull(HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH), emptyList()))
    }

    /** The limit is what the user sees: an emoji is one character, and stripped spaces are none. */
    @Test
    fun `the length limit counts code points of the normalised name`() {
        val emoji = "\uD83C\uDFC3" // one code point, two UTF-16 units
        assertNull(HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH - 1) + emoji, emptyList()))
        assertNull(HabitRules.nameProblem("  " + "x".repeat(HabitRules.MAX_NAME_LENGTH) + "  ", emptyList()))
        assertEquals(
            NameProblem.TOO_LONG,
            HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH) + emoji, emptyList()),
        )
    }

    @Test
    fun `a duplicate is judged ignoring case and against other habits only`() {
        val active = listOf(habit(1, "Gym"))

        assertEquals(NameProblem.DUPLICATE, HabitRules.nameProblem(" gym ", active))
        // Renaming a habit to its own name is not a clash with itself.
        assertNull(HabitRules.nameProblem("GYM", active, forId = 1))
    }

    @Test
    fun `the open session's habit outranks the stored selection`() {
        val active = listOf(habit(1, "Gym"), habit(2, "Writing"))

        assertEquals(2L, HabitRules.effectiveSelection(storedId = 1, runningHabitId = 2, activeHabits = active)?.id)
        assertEquals(1L, HabitRules.effectiveSelection(storedId = 1, runningHabitId = null, activeHabits = active)?.id)
        assertEquals(1L, HabitRules.effectiveSelection(storedId = 9, runningHabitId = null, activeHabits = active)?.id)
        assertNull(HabitRules.effectiveSelection(storedId = 1, runningHabitId = null, activeHabits = emptyList()))
    }

    /**
     * Only ASCII whitespace collapses, the set Java's `\s` matches, so a name stored by one app
     * reads the same in the other. A no-break space or an em space inside a name is kept as typed.
     */
    @Test
    fun `only ASCII whitespace collapses inside a name`() {
        assertEquals("a b", HabitRules.normalise("a\u000B\u000C b"))
        assertEquals("a\u00A0b", HabitRules.normalise("a\u00A0b"))
        assertEquals("a\u2003b", HabitRules.normalise("a\u2003b"))
    }

    /** As Java counts them: a lone surrogate is one code point, a pair is one too. */
    @Test
    fun `an unpaired surrogate counts as one code point`() {
        val lone = "\uD83C"
        assertNull(HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH - 1) + lone, emptyList()))
        assertEquals(
            NameProblem.TOO_LONG,
            HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH) + lone, emptyList()),
        )
    }

    @Test
    fun `a duplicate ignores case beyond ASCII`() {
        assertEquals(NameProblem.DUPLICATE, HabitRules.nameProblem("écrire", listOf(habit(1, "ÉCRIRE"))))
    }
}
