package com.checkin.app.util

import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/*
 * Latin digits in every language the app speaks.
 *
 * A locale may number in its own digits — Arabic's default is ٠١٢٣ — and the app's numbers come from
 * three places that would not agree about it: `java.time` always prints ASCII, Kotlin string
 * templates print ASCII, while `String.format` and every `%d` a string resource is filled with
 * follow the locale's numbering system. One screen would then show "2h 15m" beside "٣ أيام". Latin is
 * the reading the Gulf and the Maghreb use anyway, and it keeps the gauge's tabular clock in one face.
 *
 * The `nu-latn` Unicode extension says "this language, Latin numerals": the language, its plural
 * rules and its text are untouched, and only the numbering system is overridden.
 */

/** This locale, numbering in Latin digits. */
fun Locale.withLatinDigits(): Locale = Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("nu", "latn").build()

/**
 * An override [Configuration] carrying [base]'s locales, each numbering in Latin digits — for
 * `applyOverrideConfiguration`, so every resource the activity formats picks it up.
 */
fun latinDigitsOverride(base: Configuration): Configuration {
    val locales = base.locales
    val tags = (0 until locales.size()).joinToString(",") { locales[it].withLatinDigits().toLanguageTag() }
    return Configuration().apply { setLocales(LocaleList.forLanguageTags(tags)) }
}
