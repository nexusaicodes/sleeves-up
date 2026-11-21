package com.checkin.app.ui.reports

import java.time.LocalDate
import java.time.YearMonth

/**
 * What span of the record Reports is describing.
 *
 * Two kinds and no more, so every card has one rule per kind rather than a matrix of them: a card
 * either shows the scope or it is not rendered. `Hours by Month` is the worked example — under
 * [Month] there is exactly one bar to draw, which is not a chart, so the card is absent.
 */
sealed interface ReportScope {
    data class Month(val month: YearMonth) : ReportScope
    data object AllTime : ReportScope
}

/** An inclusive run of tracked, counted days — the range every figure and every query reads. */
data class ReportWindow(val start: LocalDate, val end: LocalDate) {
    val days: Int = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
}

/**
 * The counted days this scope covers, or null when it covers none.
 *
 * **This is the one place a range is clamped, and that is the reason it exists.** Two clampings that
 * happen to agree is not the same as one clamping, and the export is the half nobody re-reads: a
 * file whose range drifted from the screen it was exported from would assert absences on days the
 * screen was counting. Neither the screen nor `exportCsv` may derive a window of its own.
 *
 * Both ends matter, and for different reasons. The **start** never precedes [trackingStart], or the
 * gap-filling export writes out days before the user had ever opened the app as days they recorded
 * nothing. The **end** never exceeds [countedThrough], which is today only once today has been
 * checked out of — so a mid-month export stops at the last real day instead of writing out dates
 * that have not happened, and the screen never opens the morning showing a day already missed.
 *
 * A null means the scope holds no counted day at all, which is a state with three causes and one
 * answer: nothing is recorded anywhere ([trackingStart] null), the month sits entirely before the
 * record began or entirely after it reaches, or the record's only day is a today still in progress.
 * The caller renders an empty scope rather than a set of zeros, because zeros would assert that the
 * user showed up on none of some number of days — a verdict on days the record does not cover.
 */
fun ReportScope.resolve(trackingStart: LocalDate?, countedThrough: LocalDate): ReportWindow? {
    if (trackingStart == null) return null
    val (rangeStart, rangeEnd) = when (this) {
        is ReportScope.Month -> month.atDay(1) to month.atEndOfMonth()
        // All time is bounded at both ends by the record itself, so the clamp below is a no-op on it
        // — stated as the full range rather than special-cased, so one line does both scopes.
        ReportScope.AllTime -> trackingStart to countedThrough
    }
    val start = maxOf(rangeStart, trackingStart)
    val end = minOf(rangeEnd, countedThrough)
    return ReportWindow(start, end).takeIf { !start.isAfter(end) }
}
