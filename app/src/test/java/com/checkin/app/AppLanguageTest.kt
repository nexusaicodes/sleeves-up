package com.checkin.app

import com.checkin.app.util.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    private val englishEmirates = Locale.forLanguageTag("en-AE")
    private val arabicEmirates = Locale.forLanguageTag("ar-AE")

    /** Each choice must name the file it resolves to, or choosing it would show the other language. */
    @Test
    fun `each language names a shipped strings file`() {
        assertEquals(ShippedPatterns.strings("values")["language_code"], AppLanguage.ENGLISH.code)
        assertEquals(ShippedPatterns.strings("values-ar")["language_code"], AppLanguage.ARABIC.code)
    }

    @Test
    fun `system default stores nothing`() {
        assertNull(AppLanguage.SYSTEM.localeFor(listOf(englishEmirates)))
    }

    @Test
    fun `a language keeps the user's own region in it`() {
        val system = listOf(englishEmirates, arabicEmirates)
        assertEquals(arabicEmirates, AppLanguage.ARABIC.localeFor(system))
        assertEquals(englishEmirates, AppLanguage.ENGLISH.localeFor(system))
    }

    @Test
    fun `a language the user has no locale in borrows the first one's region`() {
        assertEquals(Locale.forLanguageTag("ar-DE"), AppLanguage.ARABIC.localeFor(listOf(Locale.GERMANY)))
    }

    @Test
    fun `stored locales read back as their choice`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of(emptyList()))
        assertEquals(AppLanguage.ARABIC, AppLanguage.of(listOf(arabicEmirates)))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(listOf(Locale.UK)))
    }

    /** Whatever the system screen stores reads back as the same choice here. */
    @Test
    fun `a choice round-trips through the locale it stores`() {
        val system = listOf(Locale.US)
        AppLanguage.entries.forEach { choice ->
            assertEquals(choice, AppLanguage.of(listOfNotNull(choice.localeFor(system))))
        }
    }
}
