package com.checkin.app.util

import java.util.Locale

/**
 * The locale the screen is written in: the user's first preferred locale in [resourceLanguage] that
 * names a region, or that language in the region of the first preference that names one.
 *
 * [resourceLanguage] is the `language_code` string, read from the same resources as the copy, so it
 * names the language Android actually resolved — not the user's first preference, which the app may
 * not ship. A device preferring `[de, ar]` reads Arabic copy with German first in its list; taking
 * `preferred[0]` there would spell months in German inside Arabic sentences and set Arabic copy in a
 * Latin-only face. Matching inside the list rather than taking the language bare keeps the region the
 * user chose, which is where the calendar's week start comes from. The fallback keeps it for the
 * same reason: a `[de-DE]` device reads the English copy, and its week still starts on Monday.
 *
 * **A preference with no region is passed over.** The configuration's list is the stored per-app
 * locale followed by the live system list (AOSP `PackageConfigPersister.updateConfigIfNeeded`), and
 * the app stores a bare language (see [AppLanguage]), so the region and any regional preferences
 * (`-u-fw-`, `-u-mu-`) come from the system list as it stands rather than a copy of it.
 */
fun displayLocale(preferred: List<Locale>, resourceLanguage: String): Locale =
    preferred.firstOrNull { it.language == resourceLanguage && it.country.isNotEmpty() }
        ?: inRegionOf(preferred.firstOrNull { it.country.isNotEmpty() }, resourceLanguage)

// The source's region and Unicode extensions, in [language]; its script and variant belong to its
// own language. A source the builder refuses (a legacy, non-BCP 47 locale) leaves the language bare.
private fun inRegionOf(source: Locale?, language: String): Locale = runCatching {
    Locale.Builder().setLocale(source ?: Locale.ROOT).setLanguage(language).setScript("").setVariant("").build()
}.getOrElse { Locale.forLanguageTag(language) }
