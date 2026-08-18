package com.checkin.app.ui.history.components

import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.local.DailyAggregate

/** Month-summary values over the month's counted days. See [computeMonthTiles]. */
data class MonthTiles(
    val showedUp: Int,
    val missed: Int,
    val avgDailyMs: Long,
    /** The month's longest single day, which the card rings against the all-time peak. */
    val peakDayMs: Long,
)

/**
 * Tile values for the month card, over the [trackedDaysInMonth] days of it that count.
 *
 * [summaries] needs no filtering of its own: the aggregate queries keep only completed sessions, so
 * an in-progress today is absent from the map exactly as long as it is absent from
 * [trackedDaysInMonth] — the caller derives both from `ConsistencyStats.countedThrough`, which is
 * what stops the two disagreeing about whether today is in the month.
 *
 * [MonthTiles.missed] is derived by subtraction, so a tracked day with no sessions is a day not shown
 * up for. The daily average divides by [trackedDaysInMonth] rather than by the days that had
 * sessions, which keeps missed days in the denominator.
 */
fun computeMonthTiles(summaries: Map<String, DailyAggregate>, trackedDaysInMonth: Int): MonthTiles {
    val showedUp = summaries.size
    val missed = (trackedDaysInMonth - showedUp).coerceAtLeast(0)
    val totalHoursMs = ConsistencyStats.totalWorkedMs(summaries)
    val avgDailyMs = if (trackedDaysInMonth > 0) totalHoursMs / trackedDaysInMonth else 0L
    return MonthTiles(showedUp, missed, avgDailyMs, ConsistencyStats.peakDayMs(summaries))
}

/**
 * How full a summary ring reads: a month figure as a share of the baseline it is compared against.
 *
 * The baseline is always the user's own all-time equivalent, never a configured number — there is no
 * target in this app and a ring measured against one would be that target under another name. A
 * month that matches the user's best fills the ring; going past it fills the ring and no more, since
 * an arc has nowhere further to go. A zero or absent baseline gives an empty ring rather than a
 * division by zero, which is also the honest reading for a month with nothing behind it yet.
 */
fun statRatio(value: Long, baseline: Long): Float =
    if (baseline <= 0L) 0f else (value.toFloat() / baseline.toFloat()).coerceIn(0f, 1f)

/** [statRatio] for the counted figures — days shown up, streak lengths. */
fun statRatio(value: Int, baseline: Int): Float = statRatio(value.toLong(), baseline.toLong())
