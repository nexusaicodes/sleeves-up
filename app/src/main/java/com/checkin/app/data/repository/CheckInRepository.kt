package com.checkin.app.data.repository

import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.DailyAggregate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CheckInRepository(private val dao: CheckInSessionDao, private val timeSource: TimeSource = SystemTimeSource) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

    /**
     * Opens a session, or returns the one already open. Two gated paths reach a check-in — the
     * Check-In screen and a nudge tap — and either can resolve while the other's gate is still on
     * screen, so the one-open-session invariant is enforced here rather than at each call site. A
     * second open row would never be closed (`getActiveSession()` returns one) and its hours would
     * never reach `duration`.
     *
     * **Writing the row is half of a check-in; the caller owns the other half.** This does not start
     * the foreground service and does not arm either session alarm — whoever writes the row arms it,
     * because a service start can be refused (a restricted standby bucket, an OEM declining a
     * `specialUse` foreground start) while a row write cannot, so arming behind the start would let
     * a refusal silently produce a session with no day-boundary close. Both writers do it:
     * `CheckInViewModel.executeCheckIn` and `MainActivity.onRootGatePassed`. A new caller must too.
     */
    suspend fun checkIn(): CheckInSession {
        dao.getActiveSession()?.let { return it }
        val session = CheckInSession(
            startedAt = timeSource.nowMillis(),
            dateKey = timeSource.today().format(dateFormatter),
        )
        return session.copy(id = dao.insertSession(session))
    }

    suspend fun checkOut(sessionId: Long): CheckInSession? = checkOutAt(sessionId, timeSource.nowMillis())

    /**
     * Closes [sessionId] stamped at [atMillis] rather than now, returning the closed row, or null
     * when there was none to close.
     *
     * The day-boundary alarm needs the explicit instant: it is inexact and may land hours late, so
     * the instant it fires is not the instant the session ended. Duration is floored at zero — a
     * stop before the start means a changed system clock or a corrupt row, and a negative duration
     * would poison every total that sums it.
     *
     * [autoClosed] records that the boundary closed the session rather than the user, and defaults
     * to false so every gated path stays as it was. It changes nothing about the row's duration or
     * its immutability; the CSV export is the only thing that ever reads it.
     *
     * The closed row comes back so a caller that wants to report what was recorded reads the stored
     * figure rather than recomputing it: a second subtraction at the call site would be a second
     * copy of the flooring rule, free to disagree with the row it describes.
     */
    suspend fun checkOutAt(sessionId: Long, atMillis: Long, autoClosed: Boolean = false): CheckInSession? {
        val session = dao.getSessionById(sessionId) ?: return null
        val closed = session.copy(
            stoppedAt = atMillis,
            duration = (atMillis - session.startedAt).coerceAtLeast(0L),
            autoClosed = autoClosed,
        )
        dao.updateSession(closed)
        return closed
    }

    /**
     * Checks out whatever session is open, for callers that don't hold its id. Returns the closed
     * row, or null when nothing was open.
     */
    suspend fun checkOutActiveSession(): CheckInSession? {
        val active = dao.getActiveSession() ?: return null
        return checkOut(active.id)
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    fun activeSessionFlow(): Flow<CheckInSession?> = dao.getActiveSessionFlow()

    fun dailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        dao.getDailyAggregatesFlow(startDate, endDate)

    suspend fun getDailySummaries(startDate: String, endDate: String): Map<String, DailyAggregate> =
        byDateKey(dao.getDailyAggregates(startDate, endDate))

    /**
     * Keys aggregates by their day. A day present in the map is a day the user showed up; a day
     * absent from it is one they didn't — which is the whole of what the app now decides about a day.
     */
    fun byDateKey(aggregates: List<DailyAggregate>): Map<String, DailyAggregate> = aggregates.associateBy { it.dateKey }

    fun sessionsForDateFlow(dateKey: String): Flow<List<CheckInSession>> = dao.getSessionsByDateFlow(dateKey)

    /**
     * Start instants of the completed sessions in the range, for the start-time split. Scoped
     * identically to [dailyAggregatesFlow], so the two never describe different sets of sessions.
     */
    fun sessionStartsFlow(startDate: String, endDate: String): Flow<List<Long>> =
        dao.getSessionStartsFlow(startDate, endDate)

    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> = dao.getSessionsByDate(dateKey)

    /**
     * The day tracking began — the day of the first session — or null when nothing is recorded yet.
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
     * had. The value only ever changes on the very first session.
     */
    fun trackingStartFlow(): Flow<LocalDate?> = dao.getFirstDateKeyFlow().map(::parseDateKey).distinctUntilChanged()

    suspend fun trackingStart(): LocalDate? = parseDateKey(dao.getFirstDateKey())

    /** Null rather than a throw on a malformed key, matching how the formatters treat one. */
    private fun parseDateKey(dateKey: String?): LocalDate? =
        dateKey?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
}
