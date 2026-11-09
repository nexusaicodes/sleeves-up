package com.checkin.app.testing

import com.checkin.app.platform.LanguageSettings
import java.util.Locale

class FakeLanguageSettings(var locales: List<Locale> = emptyList()) : LanguageSettings {
    var writes = 0
    override fun stored(): List<Locale> = locales
    override fun store(locale: Locale?) {
        writes++
        locales = listOfNotNull(locale)
    }
}
