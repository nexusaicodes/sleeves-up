package com.checkin.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Abstraction over wall-clock reads so date-dependent logic is deterministically testable. */
interface TimeSource {
    fun nowMillis(): Long
    fun today(): LocalDate

    /**
     * The zone [today] is read in, for callers that have to turn a stored instant back into a local
     * time of day.
     *
     * Defaulted rather than abstract so the fakes that only ever pin a date stay as they are; a test
     * that cares about the zone overrides it, and nothing has to invent one.
     */
    fun zone(): ZoneId = ZoneId.systemDefault()

    /** Emits the current local date immediately, then re-emits whenever it rolls over (at midnight). */
    fun currentDay(): Flow<LocalDate>
}

/**
 * The current local date: recomputed on every [refresh] tick (a screen resume), re-emitted at each
 * local midnight, and **emitted only when the day actually changes**.
 *
 * Both halves are load-bearing. Every ViewModel feeds this into a `flatMapLatest` that rebuilds its
 * whole graph of Room flows, so an emission per resume closes and reopens every DB subscription and
 * a write landing in that window loses its invalidation. And [currentDay] can be a day stale on
 * waking, so the tick reads [today] rather than passing its own value on. See the resume entry under
 * Conventions in CLAUDE.md for the failure that produced both.
 */
fun TimeSource.dayTrigger(refresh: Flow<Int>): Flow<LocalDate> =
    combine(refresh, currentDay()) { _, day -> maxOf(day, today()) }.distinctUntilChanged()

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
