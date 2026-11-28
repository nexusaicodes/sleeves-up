package com.checkin.app.testing

import com.checkin.app.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneId

class FakeTimeSource(private val now: Long, date: LocalDate, private val zone: ZoneId = ZoneId.of("UTC")) : TimeSource {
    val day = MutableStateFlow(date)

    /** A date [today] reads ahead of [currentDay], as when the midnight emission stalls in deep sleep. */
    var todayAhead: LocalDate? = null
    override fun nowMillis(): Long = now
    override fun today(): LocalDate = todayAhead ?: day.value

    /** UTC by default so a test's start instants read back as the hour it wrote them at. */
    override fun zone(): ZoneId = zone
    override fun currentDay(): Flow<LocalDate> = day
}
