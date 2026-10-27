package com.checkin.app.ui.presence

import com.checkin.app.data.local.ClosedBy
import com.checkin.app.util.MILLIS_PER_MINUTE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges a notification tap to the full-screen presence gate shown at the UI root. Whatever posted
 * the notification — the timer, the session reminder or a nudge —
 * [MainActivity][com.checkin.app.MainActivity] flips this to the matching [Reason] on the resulting
 * intent, and the root composable shows the auth gate regardless of the active tab.
 */
object PresenceCheckSignal {
    /**
     * Why the root gate is showing — decides what a successful auth does.
     *
     * The two check-out reasons differ only in the [ClosedBy] they carry: both close the active
     * session, and the gate itself renders identically for either. They are separate values rather
     * than one value plus a payload because the set of endings is closed and small, so an enum
     * value apiece costs nothing. The habit a check-in is for is the opposite — an open set the user
     * grows — which is why it rides on [Request] beside the reason rather than in it.
     */
    enum class Reason(val closedBy: ClosedBy? = null) {
        /** No gate requested. */
        NONE,

        /** Ongoing timer notification's "Check Out" action: success checks the active session out. */
        CHECK_OUT_FROM_TIMER(ClosedBy.TIMER_NOTIFICATION),

        /** Session reminder's "Check Out" action: success checks the active session out. */
        CHECK_OUT_FROM_REMINDER(ClosedBy.REMINDER_NOTIFICATION),

        /** Nudge tap: success starts a session for [Request.habitId]. */
        CHECK_IN,
    }

    /**
     * A gate request: why, and — for [Reason.CHECK_IN] alone — which habit. One value in one flow,
     * so the reason and its habit can never be read from two different moments. Check-out needs no
     * habit: it closes the one open session, whichever habit holds it.
     */
    data class Request(val reason: Reason, val habitId: Long? = null) {
        companion object {
            val NONE = Request(Reason.NONE)
        }
    }

    /**
     * How long an unanswered request stays live.
     *
     * Long enough to cover the round trip the gate legitimately makes out of the app — the recovery
     * screen sends the user to system settings to grant the camera and they come back — and far
     * short of the gap that makes a request meaningless. Anything the user abandoned and returned to
     * hours later is a different intention than the one they tapped.
     */
    const val EXPIRY_MS = 10 * MILLIS_PER_MINUTE

    private val mutableRequest = MutableStateFlow(Request.NONE)

    /** The live request; [raise] is the only writer, so each carries the stamp [expireIfStale] reads. */
    val request: StateFlow<Request> = mutableRequest.asStateFlow()

    /**
     * How many nudge taps are being resolved — read off the database to decide whether each opens a
     * gate, says another habit is running, or does nothing. The root draws nothing while any is (and
     * the splash screen stays up on a cold start), so the nav host cannot appear for the wrong habit
     * and take a tap before the gate replaces it. A count rather than a flag, so the first of two
     * quick taps to finish cannot uncover the host while the second is still deciding.
     */
    val resolvingTaps = MutableStateFlow(0)

    // Written under this object's lock with the request: writers span two threads, and a request
    // left stamped 0 would expire at the next start.
    private var raisedAtMillis = 0L

    /** Raises the gate, stamping when it was asked for so [expireIfStale] can retire it. */
    @Synchronized
    fun raise(reason: Reason, nowMillis: Long, habitId: Long? = null) {
        raisedAtMillis = nowMillis
        mutableRequest.value = Request(reason, habitId)
    }

    /** Retires the request once it has been answered or dismissed. */
    @Synchronized
    fun clear() {
        mutableRequest.value = Request.NONE
        raisedAtMillis = 0L
    }

    /**
     * Drops a request the user walked away from, and reports whether one is still live.
     *
     * The gate can park indefinitely — on the disclosure, on the camera-recovery screen, or on the
     * check itself — and pressing Home leaves the reason set on a process-global flow that nothing
     * else clears. Without this a nudge tapped at 09:00 and abandoned would reopen its gate on the
     * next launch and, on success, write a check-in stamped at whatever time and day it had become —
     * onto a row the app deliberately gives no way to edit or delete.
     */
    @Synchronized
    fun expireIfStale(nowMillis: Long): Boolean {
        if (request.value.reason != Reason.NONE && nowMillis - raisedAtMillis > EXPIRY_MS) {
            clear()
        }
        return request.value.reason != Reason.NONE
    }
}
