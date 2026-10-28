package com.checkin.app.service

import com.checkin.app.R
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.LaunchExtras
import com.checkin.app.notify.NotificationAction
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.NotificationIds
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.StringResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Owns what happens to a session while it is open: arming both alarms, reminding the user it is
 * still running, and closing it at the day boundary.
 *
 * The closing job is the load-bearing one: [onDayBoundaryFired] is the app's only un-gated
 * check-out and the only write to `sessions` anywhere in this package.
 *
 * Nothing here verifies anything and nothing is deducted — the reminder only asks, and ignoring it
 * costs nothing at all. **Never make it stop the clock**: a question nobody saw would then silently
 * delete hours the user worked. The one thing that ends a forgotten session is the day boundary,
 * which is a fact about the calendar rather than a judgement about the user.
 *
 * Every decision reads the **database**, never the service's in-memory mirror, because the process
 * this runs in may have been created by the alarm broadcast moments earlier.
 *
 * **Every entry point holds one lock** across its session read and alarm writes: `onStart`'s
 * [ensureArmed] runs beside a check-in's [arm], and unlocked could cancel the alarms just set.
 */
class SessionLifecycleRunner(
    private val repository: CheckInRepository,
    private val notifier: Notifier,
    private val strings: StringResolver,
    private val alarms: SessionAlarms,
    private val zones: SessionZones,
    private val timeSource: TimeSource,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val lock = Mutex()

    /** What a fired alarm turned out to mean. Returned so the caller and the tests can assert on it. */
    sealed interface Outcome {
        /** The session closed before the alarm landed; the alarm was stale and is now cancelled. */
        data object NoSession : Outcome

        /**
         * The post was refused — in practice notifications revoked, switched off for the app, or
         * this channel blocked. The next reminder stays armed and the count is **not** advanced, so
         * restoring notifications resumes the reminders instead of leaving the session silent.
         */
        data object Refused : Outcome

        /** The reminder was posted and the next one armed. */
        data object Reminded : Outcome

        /** The day boundary closed the session, stamped at [atMillis]. */
        data class Closed(val atMillis: Long) : Outcome

        /** Closed by another closer first: nothing written or cancelled, as what is armed may be newer. */
        data object AlreadyClosed : Outcome
    }

    /**
     * Arms both alarms for a session that has just begun, with the reminder cadence anchored at
     * [anchorMs]. A no-op when nothing is open.
     *
     * Called by whoever wrote the row — not by the service, whose start the platform is allowed to
     * refuse. Anything repairing an *existing* session's alarms wants [ensureArmed] instead: this
     * one cancels first, which resets the reminder count.
     *
     * The boundary comes from the session's own `date_key`, not from [anchorMs], and an instant
     * already in the past is left as-is — the platform delivers a past-due alarm immediately, which
     * is exactly the wanted behaviour for a session that outlived its boundary.
     *
     * Records the zone the session began in ([SessionZones]), so a restore elsewhere still finds
     * the midnight its `date_key` means.
     */
    suspend fun arm(anchorMs: Long) = lock.withLock {
        alarms.cancelAll()
        val active = repository.getActiveSession() ?: return@withLock
        zones.record(active.id, zone())

        val reminderAt = SessionSchedule.nextReminderAt(anchorMs)
        alarms.scheduleReminderAt(reminderAt)

        val boundaryAt = dayBoundaryFor(active)
        alarms.scheduleDayBoundaryAt(boundaryAt)
    }

    /**
     * Puts both alarms back for an open session, without disturbing anything already standing.
     * Returns whether there was a session to arm.
     *
     * This exists because **an alarm is less durable than the row it belongs to**. A force stop and
     * a package replace both cancel a package's alarms and leave the open session untouched, and
     * `START_STICKY` restoring the service does not restore them — so without a repair path a
     * session that survives an app update keeps no reminder and, far worse, no day-boundary close,
     * running until the user notices and writing a multi-day duration onto a row nothing can edit.
     *
     * Separate from [arm] rather than folded into it, because [arm] is written for a *fresh* session:
     * it cancels first, which resets the reminder count, and it anchors the cadence at the instant it
     * is given. Both are wrong for a repair — the count belongs to the session, not to the process
     * that noticed, and re-anchoring on every call would push the reminder permanently out of reach
     * of a user who opens the app more often than the interval.
     *
     * Safe to call on every app open, which is what its callers do (see [SessionWatchdog]).
     */
    suspend fun ensureArmed(nowMs: Long): Boolean = lock.withLock {
        val active = repository.getActiveSession() ?: run {
            // Alarms outliving their session: they would drop themselves on firing, but only after
            // waking the device to find that out.
            alarms.cancelAll()
            zones.clear()
            return@withLock false
        }

        // A stored instant still in the future is re-armed exactly as it was. A past one has already
        // been missed — re-derive rather than let it stand, or a device coming back from a long
        // sleep or a reboot fires the reminder the moment it finishes starting up.
        val storedReminder = alarms.nextReminderAt
        val reminderAt = storedReminder.takeIf { it > nowMs } ?: SessionSchedule.nextReminderAt(nowMs)
        alarms.scheduleReminderAt(reminderAt)

        // The boundary is re-armed at its stored instant even when that instant has passed: the
        // platform delivers a past-due alarm immediately, and that is precisely what closes a
        // session which outlived its boundary while nothing was armed to notice.
        val storedBoundary = alarms.dayBoundaryAt
        val boundaryAt = storedBoundary.takeIf { it > 0L } ?: dayBoundaryFor(active)
        alarms.scheduleDayBoundaryAt(boundaryAt)
        true
    }

    /**
     * Closes the open session and cancels both alarms in one locked step, so a check-in landing in
     * between keeps its alarms. Cancels nothing if nothing was closed. Returns the closed row.
     */
    suspend fun close(closedBy: ClosedBy): CheckInSession? = lock.withLock {
        repository.checkOutActiveSession(closedBy)?.also {
            alarms.cancelAll()
            zones.clear()
        }
    }

    /** Handles a fired reminder alarm. Safe to call in a process with no running service. */
    suspend fun onReminderFired(): Outcome = lock.withLock {
        repository.getActiveSession() ?: return@withLock stale()

        val count = alarms.remindersSent + 1
        // Only the first reminder of a session alerts. A two-hour cadence that buzzes every time
        // would wake a user all night over a session they may have left running deliberately.
        val silent = count > 1
        val firedAt = timeSource.nowMillis()

        if (!notifier.show(reminderSpec(silent))) return@withLock refused(firedAt)

        alarms.remindersSent = count

        alarms.scheduleReminderAt(SessionSchedule.nextReminderAt(firedAt))
        Outcome.Reminded
    }

    /**
     * Handles the day boundary: closes the open session and drops both alarms.
     *
     * The check-out is **un-gated**, the only one in the app that is. It is bounded in a way no
     * other check-out is — it can only ever *end* a session, always follows a gated check-in, and
     * writes an instant fixed by the calendar rather than by anything the caller chooses — so there
     * is nothing for a gate to protect. Requiring one would defeat the purpose: the whole point is
     * to close a session the user has forgotten, and a forgotten session is precisely the one nobody
     * is present to authenticate.
     *
     * Stamped from the instant the alarm was *armed* for, never from the fire time. The alarm is
     * inexact and may land hours late; stamping when it fired would hand a forgotten session hours
     * on a day it does not belong to, on a row the app deliberately gives no way to edit.
     *
     * The armed instant is taken from [SessionAlarms.dayBoundaryAt] rather than recomputed here,
     * because recomputing reads the device's **current** time zone while the alarm was set from the
     * zone at check-in — the zone the session's own `date_key` names. A session that crossed a zone
     * change in between would otherwise be stamped hours into the future travelling west, or hours
     * short travelling east, and stamping short silently deletes worked hours.
     */
    suspend fun onDayBoundaryFired(): Outcome = lock.withLock {
        val active = repository.getActiveSession() ?: return@withLock stale()

        // Clamped at both ends as a last guard: a stop in the future is never right, and one
        // before the check-in would put the row's stop ahead of its start. Neither is reachable
        // through a stored instant or a recorded zone; both guard a fallback read in a zone other
        // than the session's (a session opened by a build that recorded no zone, then restored or
        // carried across zones). That fallback errs in both directions and only one is clamped: a
        // late-evening check-in read further east lands before its start and is held there, while
        // one read further west lands hours past its true midnight and passes, since nothing in
        // the row distinguishes it from a session that really ran that long. A genuinely late
        // alarm still back-stamps to its own midnight, which is the point of deriving the instant.
        val closeAt = (alarms.dayBoundaryAt.takeIf { it > 0L } ?: dayBoundaryFor(active))
            .coerceAtLeast(active.startedAt)
            .coerceAtMost(timeSource.nowMillis())

        // The only writer of ClosedBy.DAY_BOUNDARY.
        repository.checkOutAt(active.id, closeAt, ClosedBy.DAY_BOUNDARY)
            ?: return@withLock Outcome.AlreadyClosed
        alarms.cancelAll()
        zones.clear()
        notifier.cancel(NotificationIds.SESSION_REMINDER)
        Outcome.Closed(closeAt)
    }

    /**
     * The midnight that ends [session]'s own day.
     *
     * A malformed `date_key` falls back to the boundary of the day the session *started* in, so a
     * corrupt row cannot strand a session open forever: wrong by at most a day beats unbounded.
     */
    /**
     * The midnight ending [session]'s day, read in the zone it was checked in under when that was
     * recorded, else the device's current zone.
     */
    private fun dayBoundaryFor(session: CheckInSession): Long {
        val sessionZone = zones.zoneOf(session.id) ?: zone()
        return SessionSchedule.dayBoundaryOf(session.dateKey, sessionZone)
            ?: SessionSchedule.nextDayBoundaryAfter(session.startedAt, sessionZone)
    }

    /** An alarm with nothing left to act on. Dropped rather than left to fire again. Called under [lock]. */
    private fun stale(): Outcome {
        alarms.cancelAll()
        zones.clear()
        return Outcome.NoSession
    }

    /**
     * The platform would not display the reminder. Re-armed **without advancing the count**, so the
     * first reminder the user can actually see still alerts — silence is not an answer, and a
     * reminder nobody saw must not be treated as one that was ignored. Advancing here would spend the
     * session's one alerting reminder on a post nobody received.
     */
    private fun refused(firedAt: Long): Outcome {
        alarms.scheduleReminderAt(SessionSchedule.nextReminderAt(firedAt))
        return Outcome.Refused
    }

    private fun reminderSpec(silent: Boolean) = NotificationSpec(
        id = NotificationIds.SESSION_REMINDER,
        channelId = NotificationChannels.REMINDER,
        title = strings.get(R.string.reminder_title),
        body = strings.get(R.string.reminder_text),
        actions = listOf(
            // Check-out from here runs the root gate exactly like the timer notification's action —
            // an ordinary check-out is still never un-gated. Only the day boundary is.
            NotificationAction(
                iconRes = R.drawable.ic_stat_check_out,
                label = strings.get(R.string.notification_action_stop),
                launchExtra = LaunchExtras.CHECK_OUT_FROM_REMINDER,
            ),
        ),
        silent = silent,
    )
}
