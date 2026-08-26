package com.checkin.app.ui.presence

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges a notification tap to the full-screen presence gate shown at the UI root. Whatever posted
 * the notification — the timer, the session reminder or a nudge —
 * [MainActivity][com.checkin.app.MainActivity] flips this to the matching [Reason] on the resulting
 * intent, and the root composable shows the auth gate regardless of the active tab.
 */
object PresenceCheckSignal {
    /** Why the root gate is showing — decides what a successful auth does. */
    enum class Reason {
        /** No gate requested. */
        NONE,

        /** Notification "Check Out" action: success checks the active session out. */
        CHECK_OUT,

        /** Nudge tap: success starts a session. */
        CHECK_IN,
    }

    /**
     * How long an unanswered request stays live.
     *
     * Long enough to cover the round trip the gate legitimately makes out of the app — the recovery
     * screen sends the user to system settings to grant the camera and they come back — and far
     * short of the gap that makes a request meaningless. Anything the user abandoned and returned to
     * hours later is a different intention than the one they tapped.
     */
    const val EXPIRY_MS = 10 * 60 * 1000L

    val request = MutableStateFlow(Reason.NONE)

    private var raisedAtMillis = 0L

    /** Raises the gate, stamping when it was asked for so [expireIfStale] can retire it. */
    fun raise(reason: Reason, nowMillis: Long) {
        raisedAtMillis = nowMillis
        request.value = reason
    }

    /** Retires the request once it has been answered or dismissed. */
    fun clear() {
        request.value = Reason.NONE
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
    fun expireIfStale(nowMillis: Long): Boolean {
        if (request.value != Reason.NONE && nowMillis - raisedAtMillis > EXPIRY_MS) {
            clear()
        }
        return request.value != Reason.NONE
    }
}
