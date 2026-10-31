package com.checkin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reminder copy is read every day by someone deciding whether to show up, and it is where a
 * verdict would slip back in: a count of empty days, a streak, "again", a deadline. These read the
 * shipped `strings.xml` files, so an edited line is checked as it ships, in both languages.
 */
class NudgeCopyRulesTest {

    private val languages = listOf("values", "values-ar")

    private fun nudgeCopy(dir: String): Map<String, String> =
        ShippedPatterns.strings(dir).filterKeys { it.matches(COPY_NAME) || it in LEGACY_NAMES }

    @Test
    fun `both languages ship the same reminder copy`() {
        val (en, ar) = languages.map { nudgeCopy(it).keys }
        assertEquals(en, ar)
        assertEquals(VOICES * (TITLES * 2 + BODIES), en.size)
    }

    /** A title names the habit; its unnamed twin stands in for a habit owed a name, and a body takes no argument. */
    @Test
    fun `titles take the habit's name and nothing else does`() {
        languages.forEach { dir ->
            nudgeCopy(dir).forEach { (name, text) ->
                val named = "unnamed" !in name && "title" in name
                assertEquals("$dir/$name", named, text.startsWith("%1\$s: "))
                assertFalse("$dir/$name", text.replace("%1\$s", "").contains('%'))
            }
        }
    }

    /**
     * No count, no lapse noticed, no target, no hours, no hype, and nothing a camera could be said
     * to take. Digits are banned outright, since any figure in a reminder reads as a measurement.
     */
    @Test
    fun `no line counts, grades, notices a lapse or exclaims`() {
        mapOf("values" to BANNED_EN, "values-ar" to BANNED_AR).forEach { (dir, banned) ->
            nudgeCopy(dir).forEach { (name, text) ->
                val line = text.replace("%1\$s", "")
                assertFalse("$dir/$name: \"$line\"", banned.containsMatchIn(line))
                assertFalse("$dir/$name has a digit", line.any { it.isDigit() })
                assertTrue("$dir/$name is empty", line.isNotBlank())
            }
        }
    }

    private companion object {
        const val VOICES = 3
        const val TITLES = 4
        const val BODIES = 12

        val COPY_NAME = Regex("nudge_(not_started|steady|returning)_(title|title_unnamed|body)_\\d+")

        /** The steady voice's first variant, which kept the names it shipped under before voices existed. */
        val LEGACY_NAMES = setOf(
            "nudge_not_checked_in_title",
            "nudge_not_checked_in_unnamed_title",
            "nudge_not_checked_in_body",
        )

        val BANNED_EN = Regex(
            "\\b(streak|miss(ed)?|again|back|behind|yesterday|goal|target|hours?|best|photo|selfie|capture|" +
                "hurry|don't break|keep it up)\\b|!",
            RegexOption.IGNORE_CASE,
        )

        val BANNED_AR = Regex("هدف|سلسلة|أمس|البارحة|مرة أخرى|ساعة|ساعات|صورة|أسرع|!")
    }
}
