package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.local.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory, reactive DAO so ViewModel flows emit on mutation. Scoped exactly as the real queries
 * are — every record read by habit, the open-session reads across all of them — so a leak test
 * against this fake is a test of the scoping the SQL states.
 */
class FakeCheckInSessionDao : CheckInSessionDao {
    private val store = MutableStateFlow<List<CheckInSession>>(emptyList())
    val sessions: List<CheckInSession> get() = store.value
    private var nextId = 1L

    /**
     * An open session on [dateKey] — a check-in that was never checked out.
     *
     * It is how a test says "tracking began on this day": the start is the earliest `date_key`, so
     * any row sets it. Deliberately open — it contributes to that minimum without contributing to
     * any aggregate, since every summary query filters `stopped_at IS NOT NULL`.
     */
    fun seedOpen(dateKey: String, startedAt: Long = 0L, habitId: Long = Habit.SEED_ID) {
        store.value = store.value +
            CheckInSession(id = nextId++, startedAt = startedAt, dateKey = dateKey, habitId = habitId)
    }

    fun seedCompleted(
        dateKey: String,
        startedAt: Long,
        durationMs: Long,
        closedBy: ClosedBy = ClosedBy.IN_APP,
        habitId: Long = Habit.SEED_ID,
    ) {
        store.value = store.value + CheckInSession(
            id = nextId++,
            startedAt = startedAt,
            stoppedAt = startedAt + durationMs,
            duration = durationMs,
            dateKey = dateKey,
            closedBy = closedBy,
            habitId = habitId,
        )
    }

    override suspend fun insertSession(session: CheckInSession): Long {
        val stored = session.copy(id = nextId++)
        store.value = store.value + stored
        return stored.id
    }

    override suspend fun closeIfOpen(id: Long, stoppedAt: Long, duration: Long, closedBy: ClosedBy): Int {
        val open = store.value.firstOrNull { it.id == id && it.stoppedAt == null } ?: return 0
        val closed = open.copy(stoppedAt = stoppedAt, duration = duration, closedBy = closedBy)
        store.value = store.value.map { if (it.id == id) closed else it }
        return 1
    }

    override suspend fun getActiveSession(): CheckInSession? = store.value.firstOrNull { it.stoppedAt == null }

    override suspend fun countDaysWithSession(habitId: Long): Int =
        store.value.filter { it.habitId == habitId && it.stoppedAt != null }.map { it.dateKey }.distinct().size

    override fun getActiveSessionFlow(): Flow<CheckInSession?> =
        store.map { list -> list.firstOrNull { it.stoppedAt == null } }

    override suspend fun getSessionById(sessionId: Long): CheckInSession? =
        store.value.firstOrNull { it.id == sessionId }

    override suspend fun getSessionsByDate(habitId: Long, dateKey: String): List<CheckInSession> =
        store.value.filter { it.habitId == habitId && it.dateKey == dateKey }

    override fun getSessionsByDateFlow(habitId: Long, dateKey: String): Flow<List<CheckInSession>> =
        store.map { list -> list.filter { it.habitId == habitId && it.dateKey == dateKey } }

    override suspend fun getDailyAggregates(habitId: Long, startDate: String, endDate: String): List<DailyAggregate> =
        aggregate(habitId, startDate, endDate)

    override fun getDailyAggregatesFlow(habitId: Long, startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        store.map { aggregate(habitId, startDate, endDate) }

    // Mirrors the query's scoping exactly — completed sessions only, keyed on `date_key` — because a
    // fake that filtered differently would let the start-time split describe a set of sessions the
    // aggregates had excluded, and the test could only ever prove the fake right.
    override fun getSessionStartsFlow(habitId: Long, startDate: String, endDate: String): Flow<List<Long>> =
        store.map { list ->
            list.filter { it.habitId == habitId && it.stoppedAt != null && it.dateKey in startDate..endDate }
                .map { it.startedAt }
                .sorted()
        }

    override suspend fun getFirstDateKey(habitId: Long): String? =
        store.value.filter { it.habitId == habitId }.minOfOrNull { it.dateKey }

    override fun getFirstDateKeyFlow(habitId: Long): Flow<String?> =
        store.map { list -> list.filter { it.habitId == habitId }.minOfOrNull { it.dateKey } }

    private fun aggregate(habitId: Long, startDate: String, endDate: String): List<DailyAggregate> = store.value
        .filter { it.habitId == habitId && it.stoppedAt != null && it.dateKey in startDate..endDate }
        .groupBy { it.dateKey }
        .map { (key, list) ->
            DailyAggregate(
                dateKey = key,
                totalDurationMs = list.sumOf { it.duration ?: 0L },
                sessionCount = list.size,
                firstCheckIn = list.minOf { it.startedAt },
                lastCheckOut = list.maxOf { it.stoppedAt ?: 0L },
                dayBoundaryCheckOuts = list.count { it.closedBy == ClosedBy.DAY_BOUNDARY },
                inAppCheckOuts = list.count { it.closedBy == ClosedBy.IN_APP },
                timerNotificationCheckOuts = list.count { it.closedBy == ClosedBy.TIMER_NOTIFICATION },
                reminderNotificationCheckOuts = list.count { it.closedBy == ClosedBy.REMINDER_NOTIFICATION },
                widgetCheckOuts = list.count { it.closedBy == ClosedBy.WIDGET },
            )
        }
        .sortedBy { it.dateKey }
}
