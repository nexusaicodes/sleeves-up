package com.checkin.app.platform

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.checkin.app.util.AppLanguage
import java.util.Locale

/**
 * Read/write seam over the platform's per-app locale — the setting Android's own App language screen
 * writes, so the app keeps no copy of the choice. Both calls are binder calls to the system.
 */
interface LanguageSettings {
    /** The per-app locales Android holds for the app; empty while it follows the system. */
    fun stored(): List<Locale>

    /** Stores [locale] as the app's language, or follows the system for null. */
    fun store(locale: Locale?)
}

/**
 * Stores [choice], **even when it is the choice already shown**: re-choosing is how a locale stored
 * some other way (a region picked on the system screen, a language the app does not ship) is replaced
 * with the choice's own. Writing an unchanged locale changes no configuration and recreates nothing.
 */
fun LanguageSettings.choose(choice: AppLanguage) = store(choice.locale)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LocaleManagerLanguageSettings(context: Context) : LanguageSettings {
    // Present from API 33, which this class requires.
    private val manager = requireNotNull(context.getSystemService(LocaleManager::class.java))

    override fun stored(): List<Locale> = manager.applicationLocales.let { list -> List(list.size()) { list[it] } }

    override fun store(locale: Locale?) {
        manager.applicationLocales = if (locale == null) LocaleList.getEmptyLocaleList() else LocaleList(locale)
    }
}
