package com.checkin.app.ui.reports

import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.SessionBand
import com.checkin.app.data.SessionRhythm
import com.checkin.app.data.StartBucket
import com.checkin.app.data.local.DailyAggregate
import kotlinx.datetime.TimeZone

/**
 * Every figure Reports states for one resolved [window], for both apps' screens.
 *
 * Totals and averages are stated and left alone: nothing here is measured against a target or the
 * user's own best, and no field has a direction in which higher reads as better except days shown
 * up.
 */
data class ReportFigures(
    val window: ReportWindow,
    val showedUpDays: Int,
    /** The window's days with no completed session: the window minus the days shown up. */
    val missedDays: Int,
    val totalWorkedMs: Long,
    val totalSessions: Int,
    /**
     * Worked time per *tracked* day, missed days kept in the denominator. Printed as a row, never as
     * a ring: the only denominator to fill one against is the user's own best, which ratchets.
     */
    val avgDailyMs: Long,
    /** Mean sessions per day shown up: a rhythm, not a score. */
    val avgSessionsPerDay: Float,
    /** Completed sessions by the hour they began. No bucket is preferable. */
    val startBuckets: Map<StartBucket, Int>,
    /** Days grouped by how many sessions they held. More is not better. */
    val sessionBands: Map<SessionBand, Int>,
    /** The plotted days, gap-filled so a missed day reads as zero rather than vanishing. */
    val dailySeries: List<DayPoint>,
    /** Empty under a month scope, where one bar is not a chart. */
    val monthlySeries: List<MonthPoint>,
) {
    /** Every day in the window, days with no sessions included. Never zero. */
    val totalDays: Int get() = window.days

    companion object {
        /**
         * The figures for [window], the window [scope] resolved to.
         *
         * [summaries] and [sessionStarts] must be read over the identical range: the aggregates say
         * which days count and the starts say when within them each session began, so a range that
         * differed would let the start-time split describe sessions the rest of the screen excluded.
         * The starts are needed at all because `DailyAggregate.firstCheckIn` is each day's first
         * session only. [zone] places each start in its hour.
         */
        fun of(
            scope: ReportScope,
            window: ReportWindow,
            summaries: Map<String, DailyAggregate>,
            sessionStarts: List<Long>,
            zone: TimeZone,
        ): ReportFigures {
            val showedUp = ConsistencyStats.showedUpDays(summaries)
            val totalWorkedMs = ConsistencyStats.totalWorkedMs(summaries)
            return ReportFigures(
                window = window,
                showedUpDays = showedUp,
                missedDays = (window.days - showedUp).coerceAtLeast(0),
                totalWorkedMs = totalWorkedMs,
                totalSessions = ConsistencyStats.totalSessions(summaries),
                // `resolve` returns null rather than an empty window, so `window.days` is never zero.
                avgDailyMs = totalWorkedMs / window.days,
                avgSessionsPerDay = SessionRhythm.averageSessionsPerDay(summaries),
                startBuckets = SessionRhythm.startBuckets(sessionStarts, zone),
                sessionBands = SessionRhythm.sessionsPerDayBands(summaries),
                dailySeries = dailySeries(summaries, scope, window),
                monthlySeries = monthlySeries(summaries, scope, window),
            )
        }
    }
}
