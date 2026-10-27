package com.checkin.app.notify.nudge

import com.checkin.app.data.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The fire-and-forget entry points to the nudge layer: the receiver and worker run a pass, the
 * habits collector and time choosers re-arm. Each **never throws except to cancel** — callers launch
 * on the application scope, which has no handler.
 *
 * [runOnce] and [rearm] are `NudgeDispatcher`'s, deferred because building it over both databases
 * can throw; then [armFallback] runs instead, so the checkpoint alarm's chain never ends.
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

    /** Launches [rearmNow]; what every time chooser hands to `chooseReminderTime`. */
    fun launchRearm() {
        scope.launch { rearmNow() }
    }

    /** Arms the earliest band any habit could have, which needs no database. */
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
