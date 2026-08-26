package com.checkin.app.service

import com.checkin.app.data.local.CheckInSession

/**
 * Decides whether a restarted [CheckInService] has a session to keep running: the active-session
 * row is the authority, so this reads it alone and the restored timer-prefs mirror gets no vote.
 * That is the whole reconciliation — a row means adopt it, no row means the notification is an
 * orphan. Pure so it is JVM-unit-testable, which the Service is not.
 */
object ServiceReconciler {

    sealed interface Result {
        /** No active session in the DB — the timer is an orphan; tear the service down. */
        data object Stop : Result

        /** Adopt the DB row's truth (it wins over any stale timer-prefs mirror). */
        data class Adopt(val sessionId: Long, val startTime: Long) : Result
    }

    fun reconcile(dbActive: CheckInSession?): Result = dbActive?.let {
        Result.Adopt(sessionId = it.id, startTime = it.startedAt)
    } ?: Result.Stop
}
