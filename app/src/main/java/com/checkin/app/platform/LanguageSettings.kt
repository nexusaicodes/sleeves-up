package com.checkin.app.platform

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
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

class LocaleManagerLanguageSettings(context: Context) : LanguageSettings {
    // API 33, the minSdk, so the service is always there.
    private val manager = requireNotNull(context.getSystemService(LocaleManager::class.java))

    override fun stored(): List<Locale> = manager.applicationLocales.let { list -> List(list.size()) { list[it] } }

    override fun store(locale: Locale?) {
        manager.applicationLocales = if (locale == null) LocaleList.getEmptyLocaleList() else LocaleList(locale)
    }
}
