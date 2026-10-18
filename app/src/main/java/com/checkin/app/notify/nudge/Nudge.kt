package com.checkin.app.notify.nudge

import com.checkin.app.notify.NotificationIds

/**
 * The kinds of nudge the app can send, **per habit**. One today: a habit that has no session yet
 * today is reminded once, around the hour chosen for it. Names are persisted in the send ledger, so
 * renaming a constant orphans its history — and with it the once-a-day dedup for any send already
 * recorded — unless the old name is added to [LEGACY_KEYS].
 *
 * [slot] places the nudge inside its habit's block of notification ids (see
 * [NotificationIds.habitNudge]); it must be a fixed number, never this enum's ordinal, so reordering
 * the enum cannot move an id a posted notification already holds.
 */
enum class Nudge(val slot: Int) {
    /** The habit has no session today. One per habit per day; there is no follow-up. */
    NOT_CHECKED_IN(0),
    ;

    fun notificationIdFor(habitId: Long): Int = NotificationIds.habitNudge(habitId, slot)

    companion object {
        /**
         * Ledger keys an earlier build recorded, mapped to the nudge that now stands for them. Every
         * one of v3.0's three checkpoints asked the same question of the one record that install
         * had, which is the seed habit's, so a send under any of them on the day of the update means
         * that habit was already reminded today. Append, never replace.
         */
        private val LEGACY_KEYS: Map<String, Nudge> = mapOf(
            "NOT_CHECKED_IN_MORNING" to NOT_CHECKED_IN,
            "NOT_CHECKED_IN_AFTERNOON" to NOT_CHECKED_IN,
            "NOT_CHECKED_IN_EVENING" to NOT_CHECKED_IN,
        )

        /** The nudge a ledger [key] records, or null for one nothing now owns. */
        fun fromStoredKey(key: String): Nudge? = entries.firstOrNull { it.name == key } ?: LEGACY_KEYS[key]
    }
}
