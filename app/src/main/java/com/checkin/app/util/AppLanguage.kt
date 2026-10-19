package com.checkin.app.util

import java.util.Locale

/**
 * The languages Settings offers: following the system, or one of the two the app ships.
 *
 * The choice is stored by Android as the app's per-app locale, the same setting its own App language
 * screen writes, so the two can never disagree and the app keeps no copy. [code] is the language of
 * the `values` folder the choice resolves to (`AppLanguageTest` pins each against that file's
 * `language_code`); [SYSTEM] has none.
 */
enum class AppLanguage(val code: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ARABIC("ar"),
    ;

    /**
     * The locale to hand Android for this choice, given the user's [system] locales, or null to
     * follow them. A language is never stored bare: the user's own locale in it keeps its region (and
     * any regional preferences), which is where the week start comes from, and with none the first
     * system locale lends its region — the same rule [displayLocale] reads the screen's locale by.
     */
    fun localeFor(system: List<Locale>): Locale? = code?.let { displayLocale(system, it) }

    companion object {
        /** The choice the stored per-app [appLocales] amount to; none stored follows the system. */
        fun of(appLocales: List<Locale>): AppLanguage {
            val language = appLocales.firstOrNull()?.language ?: return SYSTEM
            return entries.firstOrNull { it.code == language } ?: SYSTEM
        }
    }
}
