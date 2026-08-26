package com.checkin.app.data.local

/**
 * What ended a session — the surface the check-out came from, or the alarm that closed it unasked.
 *
 * A record of what happened, never a judgement on it. The row is immutable whichever value it
 * carries, the duration is identical, and **nothing user-facing may ever compare these** — "you
 * mostly check out from the notification" grades how a person uses the app, which is the same
 * mistake as the deleted daily target one level further out. The CSV export is the only reader.
 *
 * [storedValue] is what goes in the column and in the file, so it is **frozen**: people script
 * against the export, and the string is also what a migration back-fills against. Rename the
 * constant if you must; never the value.
 *
 * The four are the app's four ways a session can end, and each has exactly one writer — see
 * [com.checkin.app.data.repository.CheckInRepository.checkOutAt], which takes this rather than
 * defaulting it. The default is what let the boolean this replaced be inherited silently by every
 * path that never mentioned it.
 */
enum class ClosedBy(val storedValue: String) {
    /** The Check-In screen's own button. */
    IN_APP("in_app"),

    /** The ongoing timer notification's Check Out action, through the root presence gate. */
    TIMER_NOTIFICATION("timer_notification"),

    /**
     * The session reminder's Check Out action, through the root presence gate.
     *
     * Split from [TIMER_NOTIFICATION] because the reminder only exists to catch a session the user
     * has stopped noticing, so a check-out from it means something the timer's does not.
     */
    REMINDER_NOTIFICATION("reminder_notification"),

    /**
     * The midnight alarm, closing a session the user forgot. The one un-gated check-out in the app.
     *
     * This is the value the export was built for: the stop instant on such a row is a plausible
     * time nobody chose, and nothing else in the file distinguishes it from one the user did.
     */
    DAY_BOUNDARY("day_boundary"),
    ;

    companion object {
        /** Resolves a stored string, or null for an unknown one — a hand-edited or corrupt row. */
        fun fromStored(value: String?): ClosedBy? = value?.let { stored -> entries.find { it.storedValue == stored } }
    }
}
