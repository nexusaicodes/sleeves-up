package com.checkin.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZonedDateTime

/** Abstraction over wall-clock reads so date-dependent logic is deterministically testable. */
interface TimeSource {
    fun nowMillis(): Long
    fun today(): LocalDate

    /** Emits the current local date immediately, then re-emits whenever it rolls over (at midnight). */
    fun currentDay(): Flow<LocalDate>
}

/**
 * The current local date, re-emitted on every [refresh] tick (a screen resume) and at each local
 * midnight. The single place the "recompute on resume or at day rollover" trigger lives,
 * shared by every ViewModel so the idiom can't drift between screens.
 */
fun TimeSource.dayTrigger(refresh: Flow<Int>): Flow<LocalDate> = combine(refresh, currentDay()) { _, day -> day }

object SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()

    override fun currentDay(): Flow<LocalDate> = flow {
        var last = LocalDate.now()
        emit(last)
        while (true) {
            delay(DayClock.millisUntilNextMidnight(ZonedDateTime.now()))
            val current = LocalDate.now()
            if (current != last) {
                last = current
                emit(current)
            }
        }
    }
}
