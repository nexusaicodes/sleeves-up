package com.checkin.app

import com.checkin.app.util.useLatinDigitsByDefault
import com.checkin.app.util.withLatinDigits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The rule every formatted number in the app goes through: Latin digits, in any language. */
class LatinDigitsTest {

    private val arabic = Locale.forLanguageTag("ar")

    @Test
    fun `arabic numbers in its own digits unless told otherwise`() {
        // The premise: without the override, String.format would print ١٥.
        assertEquals("١٥", String.format(arabic, "%d", 15))
    }

    @Test
    fun `the override keeps the language and numbers in latin digits`() {
        val latin = arabic.withLatinDigits()

        assertEquals("ar", latin.language)
        assertEquals("15", String.format(latin, "%d", 15))
        assertEquals("1.5", String.format(latin, "%.1f", 1.5f))
    }

    @Test
    fun `the default locale numbers in latin digits once set, keeping its language`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(arabic)
            // The premise: the time picker's NumberFormat would print ١٥ under the bare default.
            assertEquals("١٥", java.text.NumberFormat.getIntegerInstance(Locale.getDefault()).format(15))
            useLatinDigitsByDefault()
            assertEquals("ar", Locale.getDefault().language)
            assertEquals("15", java.text.NumberFormat.getIntegerInstance(Locale.getDefault()).format(15))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
