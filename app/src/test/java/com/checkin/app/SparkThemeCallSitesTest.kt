package com.checkin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `SparkTheme` is called in `ui/theme/Theme.kt` and nowhere else.
 *
 * A `SparkTheme` called without the app's font family falls back to Spark's Nunito Sans, which the
 * release build discards (`res/raw/keep.xml`), so it crashes with `Resources.NotFoundException` the
 * moment it draws text, in release only — debug still carries the font, so no debug run shows it. Without
 * the app's feature flags it would also swap the button family under it. The two calls that pass both
 * are `CheckInAppTheme` and `SparkColorScope`, and every other subtree that needs different colours
 * goes through the latter. A call is matched with arguments or as a bare trailing lambda, `SparkTheme { }`.
 */
class SparkThemeCallSitesTest {

    private val sources = File("src/main/java")
    private val call = Regex("""\bSparkTheme\s*[({]""")

    @Test
    fun sparkThemeIsCalledOnlyInTheme() {
        assertTrue("source root not found from ${File("").absolutePath}", sources.isDirectory)
        val callers = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> call.containsMatchIn(file.readText()) }
            .map { it.relativeTo(sources).invariantSeparatorsPath }
            .toList()
        assertEquals(listOf("com/checkin/app/ui/theme/Theme.kt"), callers)
    }

    @Test
    fun themeCallsSparkThemeExactlyTwice() {
        val theme = File(sources, "com/checkin/app/ui/theme/Theme.kt").readText()
        assertEquals(
            "CheckInAppTheme and SparkColorScope are the only SparkTheme calls",
            2,
            call.findAll(theme).count(),
        )
    }
}
