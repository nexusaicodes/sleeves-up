package com.checkin.app.ui.reports

import com.checkin.app.data.SessionBand
import com.checkin.app.data.StartBucket
import com.checkin.app.data.local.DailyAggregate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportFiguresTest {

    private val hourMs = 3_600_000L

    // 2026-06-15 00:00 UTC; UTC wall times need no zone rules.
    private val utcJun15Midnight = 1_781_481_600_000L

    private fun day(key: String, ms: Long, sessions: Int) =
        DailyAggregate(key, ms, sessions, 0L, null, 0, sessions, 0, 0, 0)

    private val june = ReportScope.Month(YearMonth(2026, 6))
    private val juneTo15 = ReportWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 15))

    @Test
    fun `missed days are the window less the days shown up and averages keep them in`() {
        val summaries = listOf(
            day("2026-06-02", 2 * hourMs, 1),
            day("2026-06-05", 4 * hourMs, 3),
            day("2026-06-15", 9 * hourMs, 2),
        ).associateBy { it.dateKey }
        val figures = ReportFigures.of(june, juneTo15, summaries, emptyList(), TimeZone.UTC)

        assertEquals(15, figures.totalDays)
        assertEquals(3, figures.showedUpDays)
        assertEquals(12, figures.missedDays)
        assertEquals(15 * hourMs, figures.totalWorkedMs)
        assertEquals(6, figures.totalSessions)
        assertEquals(hourMs, figures.avgDailyMs, "per tracked day, missed days included")
        assertEquals(2f, figures.avgSessionsPerDay, "per day shown up")
        assertEquals(
            mapOf(SessionBand.ONE to 1, SessionBand.TWO to 1, SessionBand.THREE_PLUS to 1),
            figures.sessionBands,
        )
    }

    @Test
    fun `an empty window states zeros without dividing by zero`() {
        val figures = ReportFigures.of(june, juneTo15, emptyMap(), emptyList(), TimeZone.UTC)
        assertEquals(0, figures.showedUpDays)
        assertEquals(15, figures.missedDays)
        assertEquals(0L, figures.avgDailyMs)
        assertEquals(0f, figures.avgSessionsPerDay)
        assertEquals(15, figures.dailySeries.size, "every day plotted, as zero")
        assertTrue(figures.dailySeries.all { it.workedMs == 0L })
    }

    @Test
    fun `starts fall in the hour they began in the zone given`() {
        val starts = listOf(
            utcJun15Midnight + 9 * hourMs,
            utcJun15Midnight + 12 * hourMs,
            utcJun15Midnight + 16 * hourMs,
            utcJun15Midnight + 17 * hourMs,
        )
        val figures = ReportFigures.of(june, juneTo15, emptyMap(), starts, TimeZone.UTC)
        assertEquals(
            mapOf(StartBucket.MORNING to 1, StartBucket.AFTERNOON to 2, StartBucket.EVENING to 1),
            figures.startBuckets,
        )
    }

    @Test
    fun `only all time has a monthly series`() {
        val window = ReportWindow(LocalDate(2026, 4, 20), LocalDate(2026, 6, 15))
        val summaries = mapOf("2026-05-03" to day("2026-05-03", hourMs, 1))
        assertEquals(
            3,
            ReportFigures.of(ReportScope.AllTime, window, summaries, emptyList(), TimeZone.UTC).monthlySeries.size,
        )
        assertTrue(ReportFigures.of(june, juneTo15, summaries, emptyList(), TimeZone.UTC).monthlySeries.isEmpty())
    }
}
