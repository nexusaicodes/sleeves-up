package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.DailyAggregate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory, reactive DAO so ViewModel flows emit on mutation. */
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
    fun seedOpen(dateKey: String, startedAt: Long = 0L) {
        store.value = store.value + CheckInSession(id = nextId++, startedAt = startedAt, dateKey = dateKey)
    }

    fun seedCompleted(dateKey: String, startedAt: Long, durationMs: Long, closedBy: ClosedBy = ClosedBy.IN_APP) {
        store.value = store.value + CheckInSession(
            id = nextId++,
            startedAt = startedAt,
            stoppedAt = startedAt + durationMs,
            duration = durationMs,
            dateKey = dateKey,
            closedBy = closedBy,
        )
    }

    override suspend fun insertSession(session: CheckInSession): Long {
        val stored = session.copy(id = nextId++)
        store.value = store.value + stored
        return stored.id
    }

    override suspend fun updateSession(session: CheckInSession) {
        store.value = store.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun getActiveSession(): CheckInSession? = store.value.firstOrNull { it.stoppedAt == null }

    override fun getActiveSessionFlow(): Flow<CheckInSession?> =
        store.map { list -> list.firstOrNull { it.stoppedAt == null } }

    override suspend fun getSessionById(sessionId: Long): CheckInSession? =
        store.value.firstOrNull { it.id == sessionId }

    override suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> =
        store.value.filter { it.dateKey == dateKey }

    override fun getSessionsByDateFlow(dateKey: String): Flow<List<CheckInSession>> =
        store.map { list -> list.filter { it.dateKey == dateKey } }

    override suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
        aggregate(startDate, endDate)

    override fun getDailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        store.map { aggregate(startDate, endDate) }

    // Mirrors the query's scoping exactly — completed sessions only, keyed on `date_key` — because a
    // fake that filtered differently would let the start-time split describe a set of sessions the
    // aggregates had excluded, and the test could only ever prove the fake right.
    override fun getSessionStartsFlow(startDate: String, endDate: String): Flow<List<Long>> = store.map { list ->
        list.filter { it.stoppedAt != null && it.dateKey in startDate..endDate }
            .map { it.startedAt }
            .sorted()
    }

    override suspend fun getFirstDateKey(): String? = store.value.minOfOrNull { it.dateKey }

    override fun getFirstDateKeyFlow(): Flow<String?> = store.map { list -> list.minOfOrNull { it.dateKey } }

    private fun aggregate(startDate: String, endDate: String): List<DailyAggregate> = store.value
        .filter { it.stoppedAt != null && it.dateKey in startDate..endDate }
        .groupBy { it.dateKey }
        .map { (key, list) ->
            DailyAggregate(
                dateKey = key,
                totalDurationMs = list.sumOf { it.duration ?: 0L },
                sessionCount = list.size,
                firstCheckIn = list.minOf { it.startedAt },
                lastCheckOut = list.maxOf { it.stoppedAt ?: 0L },
                autoClosedSessions = list.count { it.closedBy == ClosedBy.DAY_BOUNDARY },
                inAppCheckOuts = list.count { it.closedBy == ClosedBy.IN_APP },
                timerNotificationCheckOuts = list.count { it.closedBy == ClosedBy.TIMER_NOTIFICATION },
                reminderNotificationCheckOuts = list.count { it.closedBy == ClosedBy.REMINDER_NOTIFICATION },
            )
        }
        .sortedBy { it.dateKey }
}
