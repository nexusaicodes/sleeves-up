package com.checkin.app.util

import java.util.Locale

/**
 * The locale the screen is written in: the user's first preferred locale whose language is
 * [resourceLanguage], or that language alone when none of them is.
 *
 * [resourceLanguage] is the `language_code` string, read from the same resources as the copy, so it
 * names the language Android actually resolved — not the user's first preference, which the app may
 * not ship. A device preferring `[de, ar]` reads Arabic copy with German first in its list; taking
 * `preferred[0]` there would spell months in German inside Arabic sentences and set Arabic copy in a
 * Latin-only face. Matching inside the list rather than taking the language bare keeps the region the
 * user chose, which is where the calendar's week start comes from.
 */
fun displayLocale(preferred: List<Locale>, resourceLanguage: String): Locale =
    preferred.firstOrNull { it.language == resourceLanguage } ?: Locale.forLanguageTag(resourceLanguage)
