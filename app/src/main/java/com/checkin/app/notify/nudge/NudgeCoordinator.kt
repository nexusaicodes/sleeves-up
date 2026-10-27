package com.checkin.app.notify.nudge

import com.checkin.app.data.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The fire-and-forget entry points to the nudge layer: the checkpoint receiver and the hourly worker
 * run a pass, and the habits collector and every reminder-time chooser re-arm. Each **never throws
 * except to cancel**, because its callers launch on the application scope, which has no exception
 * handler — a throw there kills the process.
 *
 * [runOnce] and [rearm] are `NudgeDispatcher`'s, deferred: both already absorb their own failures,
 * so what this guards is reaching them — the dispatcher is built lazily over both databases, and a
 * failure there arms the plain fallback band instead (see [armFallback]). Losing the retry costs at
 * most a nudge; losing the arm ends the chain the checkpoint alarm exists to keep alive.
 */
class NudgeCoordinator(
    private val runOnce: suspend () -> Unit,
    private val rearm: suspend () -> Unit,
    private val alarms: NudgeAlarms,
    private val reminderSettings: ReminderSettings,
    private val timeSource: TimeSource,
    private val scope: CoroutineScope,
) {

    /** One dispatcher pass, for the checkpoint receiver and the hourly worker. */
    suspend fun runPass() = guarded(runOnce)

    /** Re-arms the checkpoint alarm from the current habits, times and sends. */
    suspend fun rearmNow() = guarded(rearm)

    /**
     * Launches [rearmNow]: the one re-arm every reminder-time chooser hands to `chooseReminderTime`,
     * so no two of them can launch it differently.
     */
    fun launchRearm() {
        scope.launch { rearmNow() }
    }

    /**
     * Arms the next opening of the earliest band any habit could have, readable without a database.
     * For when the habits or the day's sends cannot be read.
     */
    fun armFallback() {
        runCatching { alarms.armFallback(timeSource.nowMillis(), timeSource.zone(), reminderSettings) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun guarded(call: suspend () -> Unit) {
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            armFallback()
        }
    }
}
