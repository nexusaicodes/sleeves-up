package com.checkin.app.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthStepsTest {

    @Test
    fun `a month stepper stops at the month today falls in across a year end`() {
        val today = LocalDate(2027, 1, 1)
        assertTrue(MonthSteps.hasMonthAfter(YearMonth(2026, 12), today))
        assertFalse(MonthSteps.hasMonthAfter(YearMonth(2027, 1), today))
        assertFalse(MonthSteps.hasMonthAfter(YearMonth(2027, 2), today))
    }
}
