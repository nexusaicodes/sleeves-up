package com.checkin.app.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.checkin.app.service.CheckInService

/** Seam over the [CheckInService] foreground-service intents so ViewModels don't hold a Context. */
interface ServiceController {
    /**
     * Starts the timer service for a session that has just begun. **Not the revive path** — see
     * [revive] for one already running; the two take their timing from different places and are not
     * interchangeable. Returns false when the platform refused the start —
     * background foreground-service starts are restricted, and the watchdog calls this from contexts
     * where that refusal is a normal outcome to be logged and retried, not a crash.
     */
    fun startTimer(sessionId: Long, startedAt: Long): Boolean

    /**
     * Restores the notification for a session that is **already** running, after its service was
     * killed. Separate from [startTimer] because that path takes the session's timing from the intent
     * and writes it into the service's render mirror — correct for a session that has not begun, wrong
     * for one already running, whose timing must come from the DB row. Returns false when the platform
     * refused the start.
     */
    fun revive(sessionId: Long, startedAt: Long): Boolean

    fun stop()

    /** Tells a running service the session row changed underneath it, so it redraws from the DB. */
    fun refreshFromDb()
}

class DefaultServiceController(private val context: Context) : ServiceController {

    override fun startTimer(sessionId: Long, startedAt: Long): Boolean =
        startForeground(CheckInService.ACTION_START, sessionId, startedAt)

    override fun revive(sessionId: Long, startedAt: Long): Boolean =
        startForeground(CheckInService.ACTION_REVIVE, sessionId, startedAt)

    private fun startForeground(action: String, sessionId: Long, startedAt: Long): Boolean {
        val intent = Intent(context, CheckInService::class.java).apply {
            this.action = action
            putExtra(CheckInService.EXTRA_SESSION_ID, sessionId)
            putExtra(CheckInService.EXTRA_START_TIME, startedAt)
        }
        return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
    }

    override fun stop() = send(CheckInService.ACTION_STOP)

    override fun refreshFromDb() = send(CheckInService.ACTION_REFRESH)

    /**
     * Delivers a command to an already-running service.
     *
     * Guarded because `startService` throws when the app is in the background and the service is not
     * already running — a real outcome for every one of these, since each is sent in response to
     * something (an alarm, a notification tap) that may arrive after the service has been killed.
     * There is nothing to command in that case, and the watchdog is what puts the service back.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun send(action: String) {
        try {
            context.startService(
                Intent(context, CheckInService::class.java).apply { this.action = action },
            )
        } catch (e: Exception) {
            // No service to receive it. The command is advisory in every case; the DB row is truth.
        }
    }
}
