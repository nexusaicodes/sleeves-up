package com.checkin.app.ui.checkin

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges a completed check-out to the celebration shown at the UI root.
 *
 * Process-global rather than ViewModel state because **two paths write a check-out** — the Check-In
 * screen's button and the notification's Check Out action, which resolves in
 * [MainActivity][com.checkin.app.MainActivity] through the root presence gate. A celebration owned
 * by `CheckInViewModel` would simply not appear for the notification path, and the user cannot tell
 * which writer closed their session. This is the same shape, and the same reason, as
 * [PresenceCheckSignal][com.checkin.app.ui.presence.PresenceCheckSignal], its sibling one
 * package over.
 *
 * **The day-boundary close deliberately does not raise this.** It runs through
 * `SessionReminderRunner.onDayBoundaryFired` and never reaches either writer, which is exactly
 * right: it closes a session the user forgot about, usually at midnight with the app dead.
 * Congratulating someone for a session the app ended on their behalf would be praise for the one
 * check-out they did not make.
 */
object CheckOutSignal {

    /**
     * What a finished session is worth saying, gathered by the writer at the moment it closed.
     *
     * The figures are carried rather than re-queried because the celebration renders above the nav
     * host, where there is no ViewModel to read them from — and re-reading would race the very
     * write that triggered this.
     */
    data class Completed(
        /** The closed session's recorded duration, read off the stored row, never recomputed. */
        val sessionMs: Long,
        /** Every completed session on the closed session's own day, including this one. */
        val dayTotalMs: Long,
        val daySessionCount: Int,
    )

    /**
     * How long an unshown celebration stays live.
     *
     * Deliberately far shorter than [PresenceCheckSignal.EXPIRY_MS]
     * [com.checkin.app.ui.presence.PresenceCheckSignal.EXPIRY_MS], because the two are waiting for
     * different things. That gate expects a legitimate round trip out of the app — it sends the user
     * to system settings and waits for them to come back. This one is a reaction to something the
     * user *just* did, and nothing about it survives the moment: a congratulation for a session
     * closed hours ago is a non sequitur landing on whatever screen they opened next.
     */
    const val EXPIRY_MS = 2 * 60 * 1000L

    /** The session to celebrate, or null when there is nothing to show. */
    val completed = MutableStateFlow<Completed?>(null)

    private var raisedAtMillis = 0L

    /** Raises the celebration, stamping when so [expireIfStale] can retire an unseen one. */
    fun raise(sessionMs: Long, dayTotalMs: Long, daySessionCount: Int, nowMillis: Long) {
        raisedAtMillis = nowMillis
        completed.value = Completed(sessionMs, dayTotalMs, daySessionCount)
    }

    /** Retires the celebration once it has been dismissed. */
    fun clear() {
        completed.value = null
        raisedAtMillis = 0L
    }

    /**
     * Drops a celebration that was raised but never seen, and reports whether one is still live.
     *
     * Nothing closes this overlay on a timer — a tap and the back gesture are the only ways out — so
     * a raise that lands as the app is being backgrounded has no one to dismiss it. The write and the
     * raise both complete on a scope that outlives the composition, so pressing Home in the instant
     * after a check-out leaves the flow set on a process-global object that nothing else clears, and
     * the next launch would open on a full-screen celebration for a session closed long ago.
     */
    fun expireIfStale(nowMillis: Long): Boolean {
        if (completed.value != null && nowMillis - raisedAtMillis > EXPIRY_MS) {
            clear()
        }
        return completed.value != null
    }
}

/**
 * Gathers what [closed] is worth saying and raises it, shared by both check-out writers so the two
 * cannot drift into showing different things for the same event.
 *
 * The day figures are read against the **closed session's own** `date_key`, not against today: a
 * session belongs wholly to the day it began on, so one started before midnight and checked out
 * after it reports the day it actually belongs to rather than the empty one it ended in.
 *
 * [nowMillis] is passed rather than read off [repository] because the repository's clock is its own
 * private business, and both call sites already hold the injectable one the tests drive.
 */
suspend fun raiseCheckOutCelebration(repository: CheckInRepository, closed: CheckInSession, nowMillis: Long) {
    val day = repository.getDailySummaries(closed.dateKey, closed.dateKey)[closed.dateKey]
    CheckOutSignal.raise(
        sessionMs = closed.duration ?: 0L,
        dayTotalMs = day?.totalDurationMs ?: 0L,
        daySessionCount = day?.sessionCount ?: 0,
        nowMillis = nowMillis,
    )
}
