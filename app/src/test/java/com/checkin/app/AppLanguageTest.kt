package com.checkin.app

import com.checkin.app.platform.LanguageSettings
import com.checkin.app.platform.choose
import com.checkin.app.util.AppLanguage
import com.checkin.app.util.displayLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    private val arabicEmirates = Locale.forLanguageTag("ar-AE")

    private class FakeLanguageSettings(var locales: List<Locale> = emptyList()) : LanguageSettings {
        var writes = 0
        override fun stored(): List<Locale> = locales
        override fun store(locale: Locale?) {
            writes++
            locales = listOfNotNull(locale)
        }
    }

    /** Each choice must name the file it resolves to, or choosing it would show the other language. */
    @Test
    fun `each language names a shipped strings file`() {
        assertEquals(ShippedPatterns.strings("values")["language_code"], AppLanguage.ENGLISH.code)
        assertEquals(ShippedPatterns.strings("values-ar")["language_code"], AppLanguage.ARABIC.code)
    }

    @Test
    fun `system default stores nothing`() {
        assertNull(AppLanguage.SYSTEM.locale)
    }

    /** A region or regional preference stored with the choice would freeze as it stood that day. */
    @Test
    fun `a language is stored bare`() {
        assertEquals(Locale.forLanguageTag("en"), AppLanguage.ENGLISH.locale)
        assertEquals(Locale.forLanguageTag("ar"), AppLanguage.ARABIC.locale)
    }

    /** Android lists the system locales after the stored one; the screen takes its region from them. */
    @Test
    fun `a bare stored language reads the live system region and preferences`() {
        val system = Locale.forLanguageTag("en-AE-u-fw-mon")
        assertEquals(
            Locale.forLanguageTag("ar-AE-u-fw-mon"),
            displayLocale(listOf(AppLanguage.ARABIC.locale!!, system), "ar"),
        )
    }

    @Test
    fun `stored locales read back as their choice`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of(emptyList()))
        assertEquals(AppLanguage.ARABIC, AppLanguage.of(listOf(arabicEmirates)))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(listOf(Locale.UK)))
    }

    /** Set by adb, another settings screen or a restore: not "System default", which it is not. */
    @Test
    fun `a stored locale the app does not ship is none of the choices`() {
        assertNull(AppLanguage.of(listOf(Locale.FRANCE)))
    }

    /** Whatever this screen stores reads back as the same choice. */
    @Test
    fun `a choice round-trips through the locale it stores`() {
        AppLanguage.entries.forEach { choice ->
            assertEquals(choice, AppLanguage.of(listOfNotNull(choice.locale)))
        }
    }

    /** Re-choosing the shown language is how a stale or regioned stored locale is replaced. */
    @Test
    fun `choosing the language already shown still writes`() {
        val settings = FakeLanguageSettings(listOf(Locale.US))
        settings.choose(AppLanguage.ENGLISH)
        assertEquals(1, settings.writes)
        assertEquals(listOf(Locale.forLanguageTag("en")), settings.stored())
    }

    @Test
    fun `system default clears an unshipped stored locale`() {
        val settings = FakeLanguageSettings(listOf(Locale.FRANCE))
        settings.choose(AppLanguage.SYSTEM)
        assertEquals(emptyList<Locale>(), settings.stored())
    }
}
