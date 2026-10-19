package com.checkin.app.util

import java.util.Locale

/**
 * The locale the screen is written in: the user's first preferred locale whose language is
 * [resourceLanguage], or that language in the first preference's region when none of them is.
 *
 * [resourceLanguage] is the `language_code` string, read from the same resources as the copy, so it
 * names the language Android actually resolved — not the user's first preference, which the app may
 * not ship. A device preferring `[de, ar]` reads Arabic copy with German first in its list; taking
 * `preferred[0]` there would spell months in German inside Arabic sentences and set Arabic copy in a
 * Latin-only face. Matching inside the list rather than taking the language bare keeps the region the
 * user chose, which is where the calendar's week start comes from. The fallback keeps it for the
 * same reason: a `[de-DE]` device reads the English copy, and its week still starts on Monday.
 */
fun displayLocale(preferred: List<Locale>, resourceLanguage: String): Locale =
    preferred.firstOrNull { it.language == resourceLanguage } ?: inRegionOf(preferred.firstOrNull(), resourceLanguage)

// A region the builder refuses (a legacy, non-BCP 47 country) leaves the language bare.
private fun inRegionOf(first: Locale?, language: String): Locale = runCatching {
    Locale.Builder().setLanguage(language).setRegion(first?.country.orEmpty()).build()
}.getOrElse { Locale.forLanguageTag(language) }
