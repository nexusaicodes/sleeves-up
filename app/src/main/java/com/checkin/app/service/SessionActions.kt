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
 * The gated check-in and check-out with their side effects in order, shared by the Check-In screen
 * and `MainActivity`'s root gate.
 *
 * Runs on the application [scope], so leaving the screen cannot cancel the work between the write
 * and its alarms, and **never throws** (that scope has no handler); what a failure leaves half-done,
 * `SessionWatchdog` repairs at the next start. The un-gated day-boundary close lives in
 * [SessionLifecycleRunner.onDayBoundaryFired].
 */
class SessionActions(
    private val repository: CheckInRepository,
    private val serviceController: ServiceController,
    private val lifecycle: SessionLifecycleRunner,
    private val postedNudges: PostedNudges,
    private val scope: CoroutineScope,
    /** Called with the row a person closed, for the celebration. */
    private val onCheckedOut: suspend (CheckInSession) -> Unit,
) {

    /** Starts a session for [habitId] in the background; see [open]. */
    fun checkIn(habitId: Long) = checkIn { habitId }

    /** As [checkIn], resolving the habit on the application scope too. */
    fun checkIn(resolveHabit: suspend () -> Long) {
        launchGuarded { open(resolveHabit()) }
    }

    /** Closes the open session in the background, recording [closedBy]; see [close]. */
    fun checkOut(closedBy: ClosedBy) {
        launchGuarded { close(closedBy) }
    }

    /**
     * Opens a session for [habitId] and arms it. A session already open (this habit's or another's)
     * is left alone: re-arming would reset its reminder count.
     */
    suspend fun open(habitId: Long): CheckInResult {
        val result = repository.checkIn(habitId)
        if (result !is CheckInResult.Opened) return result
        val session = result.session
        serviceController.startTimer(session.id, session.startedAt)
        // Armed here, not in the service: `startTimer` can be refused, and an unarmed session has no
        // day-boundary close.
        lifecycle.arm(session.startedAt)
        // A nudge asking for a check-in is stale however the check-in was made.
        postedNudges.retire(habitId)
        return result
    }

    /** Closes the open session and returns it; null when nothing was open or another closer won. */
    suspend fun close(closedBy: ClosedBy): CheckInSession? {
        // The runner cancels the alarms with the write; `stop()` is a no-op on a dead service.
        val closed = lifecycle.close(closedBy) ?: return null
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
                // See the class KDoc.
            }
        }
    }
}
