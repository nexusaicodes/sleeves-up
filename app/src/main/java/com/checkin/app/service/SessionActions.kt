package com.checkin.app.service

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.CheckInResult
import com.checkin.app.notify.nudge.PostedNudges
import com.checkin.app.platform.ServiceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The gated check-in and check-out, with every side effect each one owes, in the order it owes them.
 * Both gated writers — the Check-In screen and the root gate `MainActivity` raises for a notification,
 * nudge or widget tap — call this, so the ordering rules below live in one place.
 *
 * Each runs on the application [scope], not the caller's: leaving the screen the moment the gate
 * passes must not cancel the work between the row's write and its alarms. And each **never throws**,
 * since that scope has no exception handler. A failure part-way leaves what the next app open
 * repairs — `SessionWatchdog` re-arms an open session's alarms and revives its service, and the
 * service's reconcile tears down a timer over a closed row.
 *
 * The day-boundary close is not here: it is the one un-gated check-out, and it lives in
 * [SessionLifecycleRunner.onDayBoundaryFired], under the runner's lock.
 */
class SessionActions(
    private val repository: CheckInRepository,
    private val serviceController: ServiceController,
    private val lifecycle: SessionLifecycleRunner,
    private val postedNudges: PostedNudges,
    private val scope: CoroutineScope,
    /** Raised for a check-out a person made — the closed row, for the celebration's figures. */
    private val onCheckedOut: suspend (CheckInSession) -> Unit,
) {

    /** Starts a session for [habitId] in the background; see [open]. */
    fun checkIn(habitId: Long) {
        launchGuarded { open(habitId) }
    }

    /** Closes the open session in the background, recording [closedBy]; see [close]. */
    fun checkOut(closedBy: ClosedBy) {
        launchGuarded { close(closedBy) }
    }

    /**
     * Opens a session for [habitId] and arms it. Only a new session is started: one already open for
     * this habit — two gated paths resolving together — keeps its alarms, since re-arming would reset
     * its reminder count, and another habit's open session means writing nothing is the only safe
     * answer. The repository re-checks under its own lock, so this holds whatever the caller read.
     */
    suspend fun open(habitId: Long): CheckInResult {
        val result = repository.checkIn(habitId)
        if (result !is CheckInResult.Opened) return result
        val session = result.session
        serviceController.startTimer(session.id, session.startedAt)
        // Armed by the writer, not the service: `startTimer` can be refused — a restricted standby
        // bucket, an OEM that declines the foreground start — and a session with no day-boundary
        // close runs until the user notices, writing a multi-day duration onto a row nothing can edit.
        lifecycle.arm(session.startedAt)
        // A nudge asking for a check-in is stale whichever way the check-in was made, and one left
        // posted sends the user back through the gate to reach a session already open.
        postedNudges.retire(habitId)
        return result
    }

    /**
     * Closes the open session, whichever habit holds it, and returns the closed row — or null when
     * nothing was open, or another closer got there first. Only the call that closed the row cancels
     * and stops: whoever else closed it did both, and a cancel with nothing closed here could land on
     * the alarms of a session opened since.
     */
    suspend fun close(closedBy: ClosedBy): CheckInSession? {
        val closed = repository.checkOutActiveSession(closedBy) ?: return null
        // Before `stop()`, and not left to it: that command is a no-op when the service has already
        // been killed, which would leave both alarms standing over a closed session.
        lifecycle.cancel()
        serviceController.stop()
        onCheckedOut(closed)
        return closed
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun launchGuarded(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // See the class KDoc: what is left half-done, the next app open repairs.
            }
        }
    }
}
