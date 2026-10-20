package com.checkin.app

import com.checkin.app.util.displayLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

class DisplayLocaleTest {

    private val german = Locale.GERMANY
    private val arabicEgypt = Locale.forLanguageTag("ar-EG")

    @Test
    fun `the resolved language wins over a first preference the app does not ship`() {
        assertEquals(arabicEgypt, displayLocale(listOf(german, arabicEgypt), "ar"))
    }

    @Test
    fun `the matching preference keeps its region`() {
        assertEquals(Locale.US, displayLocale(listOf(Locale.US), "en"))
    }

    @Test
    fun `no matching preference keeps the first preference's region`() {
        val fallback = displayLocale(listOf(german, Locale.JAPAN), "en")
        assertEquals(Locale.forLanguageTag("en-DE"), fallback)
        assertEquals(DayOfWeek.MONDAY, WeekFields.of(fallback).firstDayOfWeek)
    }

    /** The stored per-app language comes first and bare; the region is the system list's. */
    @Test
    fun `a preference with no region is passed over for one that has it`() {
        val arabic = Locale.forLanguageTag("ar")
        assertEquals(arabicEgypt, displayLocale(listOf(arabic, german, arabicEgypt), "ar"))
        assertEquals(Locale.forLanguageTag("ar-DE"), displayLocale(listOf(arabic, german), "ar"))
    }

    @Test
    fun `the fallback keeps the region's preferences but not its script`() {
        val source = Locale.forLanguageTag("zh-Hant-TW-u-fw-mon")
        assertEquals(Locale.forLanguageTag("en-TW-u-fw-mon"), displayLocale(listOf(source), "en"))
    }

    @Test
    fun `no preferences at all falls back to the bare language`() {
        assertEquals(Locale.forLanguageTag("en"), displayLocale(emptyList(), "en"))
    }

    /** The code is how the theme learns which file Android picked, so it must name that file. */
    @Test
    fun `each strings file names its own language`() {
        assertEquals("en", ShippedPatterns.strings("values")["language_code"])
        assertEquals("ar", ShippedPatterns.strings("values-ar")["language_code"])
    }
}
