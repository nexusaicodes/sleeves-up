package com.checkin.app.platform

import com.checkin.app.data.local.DailyAggregate
import kotlinx.datetime.toKotlinTimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The shared `CsvFormat` against the JVM formatting it spells out by hand: `String.format(Locale.US,
 * "%.2f")` for Total Hours and `hh:mm a` in `Locale.US` for the clocks. People script against the
 * file, so the bytes Android writes must not move, and these sweeps are the check. They run under
 * an Arabic default locale, the case where a locale-following formatter would write other digits.
 * JVM-only, since the reference formatters are; `CsvFormatTest` pins sample values on both platforms.
 */
class CsvFormatParityTest {

    private val defaultLocale = Locale.getDefault()

    @Before
    fun arabicDefault() = Locale.setDefault(Locale.forLanguageTag("ar"))

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun row(durationMs: Long, firstCheckIn: Long, lastCheckOut: Long, zone: ZoneId): List<String> {
        val summary = DailyAggregate("2026-06-15", durationMs, 1, firstCheckIn, lastCheckOut, 0, 1, 0, 0, 0)
        return csvRow("2026-06-15", summary, zone.toKotlinTimeZone()).trimEnd('\n').split(",")
    }

    @Test
    fun `total hours match the JVM format across every rounding boundary`() {
        // Every 18 seconds is a hundredth of an hour or exactly halfway to the next, so this walks
        // each boundary for ten hours, then strides on for a year's worth of milliseconds.
        val boundaries = (-36_000L..36_000_000L step 18_000L).flatMap { listOf(it - 1, it, it + 1) }
        val strides = (0L..31_536_000_000L step 7_919_311L)
        (boundaries + strides).forEach { ms ->
            val expected = String.format(Locale.US, "%.2f", ms / 3_600_000.0)
            assertEquals("$ms ms", expected, row(ms, 0L, 0L, ZoneId.of("UTC"))[3])
        }
    }

    @Test
    fun `clock columns match the JVM format for every minute of a DST day`() {
        val clock = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
        val zones = listOf("UTC", "Asia/Kolkata", "America/New_York").map(ZoneId::of)
        // 2026-03-08, New York's spring-forward day, swept a minute at a time in every zone.
        val dayStart = 1_772_928_000_000L
        zones.forEach { zone ->
            (0 until 26 * 60).forEach { minute ->
                val at = dayStart + minute * 60_000L
                val expected = Instant.ofEpochMilli(at).atZone(zone).format(clock)
                assertEquals("$zone at $at", expected, row(0L, at, at, zone)[1])
            }
        }
    }
}
