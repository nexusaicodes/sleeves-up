package com.checkin.app.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** How far History's and Reports' month steppers may go. */
object MonthSteps {

    /**
     * Whether a month stepper showing [shown] may step forward: only up to the month [today] falls
     * in, since a month that has not begun holds no day the record could cover. Both steppers ask
     * here, for the arrow they draw and for the step itself. Stepping back has no bound: a month
     * before the record is an empty state, not a disabled arrow.
     */
    fun hasMonthAfter(shown: YearMonth, today: LocalDate): Boolean = shown < today.yearMonth
}
