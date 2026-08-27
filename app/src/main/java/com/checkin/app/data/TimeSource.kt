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
 * The current local date, recomputed on every [refresh] tick (a screen resume) and re-emitted at
 * each local midnight. The single place the "recompute on resume or at day rollover" trigger lives,
 * shared by every ViewModel so the idiom can't drift between screens.
 *
 * **It emits only when the day actually changes, and that is load-bearing rather than tidiness.**
 * Every caller feeds it into a `flatMapLatest` that builds the screen's whole graph of Room flows,
 * so an emission tears every one of those subscriptions down and opens fresh ones. A tick per
 * resume therefore did that on every return to a screen — including the return from the device
 * credential prompt, which lands at the same instant the check-in it authorised is committing. An
 * invalidation dispatched while the observers are being swapped reaches the ones already detached,
 * and the replacements keep serving the pre-write query they opened with: the screen then shows no
 * session over a session that is open, until something resumes it again. Deduping keeps the
 * subscriptions alive across a resume, so there is no window to lose the notification in.
 *
 * The resume still recomputes rather than merely re-emitting: [currentDay] sleeps until the next
 * midnight and a `delay` does not advance while the device is in deep sleep, so its value can be a
 * day stale on waking. Taking the later of that and [today] is what makes the tick worth having —
 * re-emitting the flow's own stale value, which is what a plain `combine` passes on, would not have
 * moved the date window the tick exists to move.
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
