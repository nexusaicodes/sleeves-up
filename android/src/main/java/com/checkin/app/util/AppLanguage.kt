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
     * The locale to hand Android for this choice, or null to follow the system. **Always the bare
     * language**: Android lists the system locales after it, and [displayLocale] takes the region and
     * regional preferences from there, so they follow the system rather than freezing as they stood
     * when the choice was made.
     */
    val locale: Locale? get() = code?.let(Locale::forLanguageTag)

    companion object {
        /**
         * The choice the stored per-app [appLocales] amount to: none stored follows the system, and a
         * stored locale in a language the app does not ship (set by adb, another settings screen or a
         * restore) is null — none of these choices, rather than one it is not.
         */
        fun of(appLocales: List<Locale>): AppLanguage? {
            val language = appLocales.firstOrNull()?.language ?: return SYSTEM
            return entries.firstOrNull { it.code == language }
        }
    }
}
