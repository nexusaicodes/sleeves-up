package com.checkin.app.ui.reports

import java.time.LocalDate
import java.time.YearMonth

/*
 * What the two line charts plot: one point per day, one per month.
 *
 * Beside ReportScope rather than in the ViewModel file, which otherwise declared three public types
 * where every other screen's declares one -- its own UiState. Both series are gap-filled by the
 * ViewModel, so a day or month with no sessions arrives here as a real zero: a hole in a line chart
 * reads as missing data rather than as an absence.
 */

/** A day's worked time, for the daily-hours chart. */
data class DayPoint(val date: LocalDate, val workedMs: Long)

/** A month's worked time, for the monthly-totals chart. */
data class MonthPoint(val month: YearMonth, val workedMs: Long)
