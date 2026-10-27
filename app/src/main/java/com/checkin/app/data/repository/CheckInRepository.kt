package com.checkin.app.data.repository

import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.local.DailyAggregate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * What [CheckInRepository.checkIn] did. Sealed rather than "the open row", because there is one open
 * session across all habits and a check-in to one habit must never hand back another's row as
 * though it were its own: the caller would start a timer and arm alarms for the wrong record.
 */
sealed interface CheckInResult {
    /** A new session, opened for the habit asked for. */
    data class Opened(val session: CheckInSession) : CheckInResult

    /** That habit already had the open session — two gated paths resolved together. */
    data class AlreadyOpen(val session: CheckInSession) : CheckInResult

    /** Another habit holds the one open session; nothing was written. */
    data class OtherHabitOpen(val session: CheckInSession) : CheckInResult
}

class CheckInRepository(private val dao: CheckInSessionDao, private val timeSource: TimeSource = SystemTimeSource) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

    /** Holds [checkIn]'s read and insert together, so two writers cannot both find nothing open. */
    private val checkInLock = Mutex()

    /**
     * Opens a session for [habitId], unless one is already open — for it or for any other habit.
     * Two gated paths reach a check-in — the Check-In screen and a nudge tap — and either can resolve
     * while the other's gate is still on screen, so the one-open-session invariant is enforced here
     * rather than at each call site, under [checkInLock]: `AppContainer` builds one repository, and
     * the two writers run on different dispatchers. A second open row would never be closed
     * (`getActiveSession()` returns one) and its hours would never reach `duration`. The switcher
     * locks while a session runs, so [CheckInResult.OtherHabitOpen] is the backstop for that lock rather than an expected
     * outcome.
     *
     * **Writing the row is half of a check-in; the caller owns the other half.** This does not start
     * the foreground service and does not arm either session alarm — whoever writes the row arms it,
     * because a service start can be refused (a restricted standby bucket, an OEM declining a
     * `specialUse` foreground start) while a row write cannot, so arming behind the start would let
     * a refusal silently produce a session with no day-boundary close. Both writers do it:
     * `CheckInViewModel.executeCheckIn` and `MainActivity.onRootGatePassed`. A new caller must too.
     */
    suspend fun checkIn(habitId: Long): CheckInResult = checkInLock.withLock {
        dao.getActiveSession()?.let { open ->
            return@withLock if (open.habitId == habitId) {
                CheckInResult.AlreadyOpen(open)
            } else {
                CheckInResult.OtherHabitOpen(open)
            }
        }
        val session = CheckInSession(
            startedAt = timeSource.nowMillis(),
            dateKey = timeSource.today().format(dateFormatter),
            habitId = habitId,
        )
        CheckInResult.Opened(session.copy(id = dao.insertSession(session)))
    }

    suspend fun checkOut(sessionId: Long, closedBy: ClosedBy): CheckInSession? =
        checkOutAt(sessionId, timeSource.nowMillis(), closedBy)

    /**
     * Closes [sessionId] stamped at [atMillis] rather than now, returning the closed row, or null
     * when there was none to close.
     *
     * The day-boundary alarm needs the explicit instant: it is inexact and may land hours late, so
     * the instant it fires is not the instant the session ended. Duration is floored at zero — a
     * stop before the start means a changed system clock or a corrupt row, and a negative duration
     * would poison every total that sums it.
     *
     * [closedBy] records *what* ended the session — which surface the user checked out from, or the
     * midnight alarm. It changes nothing about the row's duration or its immutability; the CSV
     * export is the only thing that ever reads it, and nothing displays it.
     *
     * It has **no default**, deliberately: a new check-out path must state which of the four endings
     * it is, or it does not compile — a default would let one silently claim to be another.
     *
     * The closed row comes back so a caller that wants to report what was recorded reads the stored
     * figure rather than recomputing it: a second subtraction at the call site would be a second
     * copy of the flooring rule, free to disagree with the row it describes.
     *
     * **Only an open row is closed**, and null comes back otherwise: a session is closed once, by
     * whichever closer lands first, and a second must not restamp it — the day boundary would
     * replace a user's real stop with midnight, or a late tap the midnight close with its own time,
     * on a row nothing can edit. `CheckInSessionDao.closeIfOpen` carries the condition, so it holds
     * even when both closers read the row as open.
     */
    suspend fun checkOutAt(sessionId: Long, atMillis: Long, closedBy: ClosedBy): CheckInSession? {
        val session = dao.getSessionById(sessionId)?.takeIf { it.stoppedAt == null } ?: return null
        val closed = session.copy(
            stoppedAt = atMillis,
            duration = (atMillis - session.startedAt).coerceAtLeast(0L),
            closedBy = closedBy,
        )
        val written = dao.closeIfOpen(closed.id, atMillis, checkNotNull(closed.duration), closedBy)
        return closed.takeIf { written > 0 }
    }

    /**
     * Checks out whatever session is open, for callers that don't hold its id. Returns the closed
     * row, or null when nothing was open.
     */
    suspend fun checkOutActiveSession(closedBy: ClosedBy): CheckInSession? {
        val active = dao.getActiveSession() ?: return null
        return checkOut(active.id, closedBy)
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    fun activeSessionFlow(): Flow<CheckInSession?> = dao.getActiveSessionFlow()

    fun dailyAggregatesFlow(habitId: Long, startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        dao.getDailyAggregatesFlow(habitId, startDate, endDate)

    suspend fun getDailySummaries(habitId: Long, startDate: String, endDate: String): Map<String, DailyAggregate> =
        byDateKey(dao.getDailyAggregates(habitId, startDate, endDate))

    /**
     * Where every window of [habitId]'s record ends on [today]: [ConsistencyStats.countedThrough],
     * today once it has been checked out of, otherwise yesterday. Answered by a **one-day** query —
     * "is today's key present" is the whole question — so the record is never read to find it.
     *
     * Deduplicated: Room invalidates per table, so the query re-emits on every write to `sessions`,
     * including the ones that leave the boundary where it was, and each screen rebuilds its window
     * queries off this.
     */
    fun countedThroughFlow(habitId: Long, today: LocalDate): Flow<LocalDate> {
        val todayKey = today.format(dateFormatter)
        return dailyAggregatesFlow(habitId, todayKey, todayKey)
            .map { ConsistencyStats.countedThrough(byDateKey(it), today) }
            .distinctUntilChanged()
    }

    /** [countedThroughFlow], read once — for the CSV export, which reads the clock once too. */
    suspend fun countedThrough(habitId: Long, today: LocalDate): LocalDate {
        val todayKey = today.format(dateFormatter)
        return ConsistencyStats.countedThrough(getDailySummaries(habitId, todayKey, todayKey), today)
    }

    /**
     * Keys aggregates by their day. A day present in the map is a day the user showed up; a day
     * absent from it is one they didn't — which is the whole of what the app decides about a day.
     */
    fun byDateKey(aggregates: List<DailyAggregate>): Map<String, DailyAggregate> = aggregates.associateBy { it.dateKey }

    fun sessionsForDateFlow(habitId: Long, dateKey: String): Flow<List<CheckInSession>> =
        dao.getSessionsByDateFlow(habitId, dateKey)

    /**
     * Start instants of the completed sessions in the range, for the start-time split. Scoped
     * identically to [dailyAggregatesFlow], so the two never describe different sets of sessions.
     */
    fun sessionStartsFlow(habitId: Long, startDate: String, endDate: String): Flow<List<Long>> =
        dao.getSessionStartsFlow(habitId, startDate, endDate)

    suspend fun getSessionsByDate(habitId: Long, dateKey: String): List<CheckInSession> =
        dao.getSessionsByDate(habitId, dateKey)

    /**
     * The day [habitId]'s tracking began — the day of its first session — or null when it has none.
     *
     * Derived rather than stored, and it must stay that way. Storing it is a second copy of a fact
     * the table already holds, and the two can disagree: a cloud restore brings the copy back
     * without the rows it indexes, and every day since reads as a day the user did not show up for,
     * on the one screen they check their consistency in. Read off the sessions it describes, that
     * disagreement is unrepresentable.
     *
     * `distinctUntilChanged` is load-bearing, not tidiness. Room re-runs the query on any write to
     * `sessions` and emits whether or not the value moved, and both History and Reports
     * `flatMapLatest` their whole aggregate pipeline off this flow — so without it every check-in and
     * check-out tears that pipeline down and re-subscribes it to arrive at the same start it already
     * had. The value only ever changes on the habit's very first session.
     */
    fun trackingStartFlow(habitId: Long): Flow<LocalDate?> =
        dao.getFirstDateKeyFlow(habitId).map(::parseDateKey).distinctUntilChanged()

    suspend fun trackingStart(habitId: Long): LocalDate? = parseDateKey(dao.getFirstDateKey(habitId))

    /** Null rather than a throw on a malformed key, matching how the formatters treat one. */
    private fun parseDateKey(dateKey: String?): LocalDate? =
        dateKey?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
}
