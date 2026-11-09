package com.checkin.app.notify

import com.checkin.app.data.local.Habit
import com.checkin.app.notify.nudge.NudgeCoordinator
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.util.MILLIS_PER_MINUTE
import com.checkin.app.util.MILLIS_PER_SECOND
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Keeps every habit's notification channel and the reminder fallback's active set in step with
 * the habits table, and re-arms the nudge alarm whenever the set of habits changes — for the life
 * of the process, from its start.
 *
 * Driven off the table rather than called by each write, so no write path can forget either: a habit
 * created, renamed, archived or restored is one emission, and the first emission is the process start
 * itself, which is what puts the alarm back after a boot, a package replace or a force stop.
 *
 * Never throws: a database that will not open still arms the fallback, and a failed channel write
 * costs that emission's channels, which the dispatcher re-ensures before it posts. A failed read
 * re-subscribes with backoff ([habitSyncRetryDelayMs]) rather than ending the sync.
 */
class HabitSync(
    /** Every habit, archived ones included: an archived habit's channel is deleted. */
    private val habits: Flow<List<Habit>>,
    private val channels: HabitChannels,
    private val reminderSettings: ReminderSettings,
    private val nudges: NudgeCoordinator,
    private val scope: CoroutineScope,
    private val retryDelayMs: (attempt: Long) -> Long = ::habitSyncRetryDelayMs,
) {

    /** Starts following the table; call once, at process start. */
    fun start(): Job = scope.launch {
        habits
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                nudges.armFallback()
                delay(retryDelayMs(attempt))
                true
            }
            .collect { all ->
                runCatching { channels.sync(all) }
                val active = all.filterNot { it.archived }.mapTo(mutableSetOf()) { it.uuid }
                runCatching { reminderSettings.rememberActive(active) }
                nudges.rearmNow()
            }
    }
}

private const val HABIT_SYNC_RETRY_MS = 30 * MILLIS_PER_SECOND
private const val HABIT_SYNC_RETRY_MAX_MS = 30 * MILLIS_PER_MINUTE

/** Enough to pass the cap; few enough that the shift cannot overflow. */
private const val HABIT_SYNC_MAX_DOUBLINGS = 6L

/** [HabitSync]'s wait after a failed read: doubling from 30s, capped at 30min. */
internal fun habitSyncRetryDelayMs(attempt: Long): Long =
    (HABIT_SYNC_RETRY_MS shl attempt.coerceAtMost(HABIT_SYNC_MAX_DOUBLINGS).toInt())
        .coerceAtMost(HABIT_SYNC_RETRY_MAX_MS)
