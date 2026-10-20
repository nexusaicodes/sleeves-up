package com.checkin.app.notify

/**
 * Every notification id the app posts under, including nudges: posting twice under one id replaces
 * rather than adds, so every sender's ids must be visible in one place.
 *
 * A [com.checkin.app.notify.nudge.Nudge]'s id comes from [habitNudge] — its habit's block plus the
 * nudge's fixed slot, never the enum's ordinal or position — so reordering the enum cannot change any
 * existing id, and two habits can never share one.
 *
 * ### PendingIntent request codes — the whole allocation
 *
 * A separate namespace from the ids above, shared process-wide **and with previously installed
 * versions**, whose notifications and alarms survive an update. `PendingIntent` equality ignores
 * extras, so two senders on one code means one silently rewrites the other's target. Three files
 * allocate out of it, so the bands are listed here — the ids file — rather than being discoverable
 * only by opening all three and hoping:
 *
 * | Band | Owner | Use |
 * |---|---|---|
 * | 1_000+ | `NotificationFactory.CONTENT_REQUEST_BASE` | a notification's tap target |
 * | 10_000+ | `NotificationFactory.ACTION_REQUEST_BASE` | its action buttons, `MAX_ACTIONS` apiece |
 * | 20_000, 20_001 | `service.SessionAlarms` | the session reminder and the day boundary |
 * | 20_010 | `notify.nudge.NudgeAlarms` | the nudge checkpoint |
 * | 30_000 | `NotificationFactory.NUDGE_TAP_REQUEST_CODE` | every habit's nudge tap target |
 *
 * The nudge tap is one code for every habit, told apart by its intent's data URI
 * (`checkin://nudge/<uuid>`) rather than by a code apiece: `PendingIntent` equality ignores extras
 * but not data, and a code per habit would be a band with no upper bound. Its content code would
 * otherwise come from the 1_000 band off a nudge id in the 100_000s — landing it in no band at all.
 *
 * **The values are frozen**, for the same reason the ids are. A new sender takes a fresh band
 * and adds a row here. The three bases live in `NotificationFactory`'s private companion, so the
 * numbers are restated above rather than linked — this table is the readable copy.
 *
 * For how the two `notify` packages divide up, see the tree map in [Notifier].
 */
object NotificationIds {

    /**
     * The ongoing check-in timer. A foreground-service notification, swipeable from Android 14
     * despite being posted `ongoing` — see [NotificationSpec.ongoing]. Nothing re-posts it after a
     * dismissal; the session reminder and the day boundary are the coverage.
     */
    const val TIMER = 1

    /** The periodic "still going?" reminder for an open session; each one replaces the last. */
    const val SESSION_REMINDER = 2

    /**
     * Where habit nudge ids start: far above every fixed id, so no habit's block can reach one.
     * Each habit owns [HABIT_NUDGE_SLOTS] ids from `base + habitId * slots`, one per nudge slot.
     */
    const val HABIT_NUDGE_BASE = 100_000

    /** Ids per habit. One is used today; the rest are room for a second nudge without a re-cut. */
    const val HABIT_NUDGE_SLOTS = 4

    /**
     * The id [slot]'s nudge for [habitId] posts under. Built from the row id rather than the uuid,
     * because an id is only ever held by the tray, which does not survive the fresh database a
     * restore lands on — the one case where a row id is reassigned.
     */
    fun habitNudge(habitId: Long, slot: Int): Int {
        require(slot in 0 until HABIT_NUDGE_SLOTS) { "slot $slot outside a habit's block" }
        return Math.toIntExact(HABIT_NUDGE_BASE + habitId * HABIT_NUDGE_SLOTS + slot)
    }

    /**
     * Ids earlier builds posted nudges under and nothing posts under now: v3.0's three checkpoints,
     * all about the single record such an install had. Never reused, since a new notification on one
     * would replace a stale one still in an updated install's tray rather than sit beside it; still
     * cancelled by `PostedNudges.retire`, so that stale one clears at the next check-in like any
     * other. Append, never replace.
     */
    val RETIRED_NUDGES: List<Int> = listOf(10, 11, 12)
}
