package com.checkin.app.notify.log

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** What happened to a notification we sent. */
enum class EngagementEventType {
    /** Posted to the system tray. */
    SHOWN,

    /** The user tapped it. */
    OPENED,

    /** The user swiped it away. */
    DISMISSED,

    /** The user checked in soon enough after a SHOWN for it to plausibly be the cause. */
    CONVERTED,
}

/**
 * Which subsystem sent the notification an event belongs to.
 *
 * This is what lets every sender share one table without interfering. Nudge frequency capping and
 * conversion attribution both ask "what was shown most recently" — questions that must only ever see
 * [NUDGE] rows. A session reminder counted toward the daily cap would silence that day's real nudge,
 * and one sitting at the head of the log would absorb a tap or a check-in that belonged to a nudge.
 */
enum class EngagementSource {
    /** An optional encouragement nudge, on by default and experiment-tracked. */
    NUDGE,

    /**
     * The periodic session reminder. Recorded for visibility only; it drives no rules.
     *
     * The name does not match the wording used elsewhere for that reminder, and is frozen anyway:
     * this string is stored in `engagement.db` and is what the cap and attribution queries scope on,
     * so renaming it orphans every row already written under it.
     */
    PRESENCE,

    /**
     * Background-machinery lifecycle — the foreground service, the session alarms, and the nudge
     * checkpoint alarm. Recorded for visibility only; it drives no rules.
     *
     * These rows are the only trace a session that silently loses its service leaves: the
     * notification is gone, the DB row still looks open, and the app keeps rendering a running timer
     * from it. Without them, diagnosis means inferring backwards from a wrong duration. The nudge
     * checkpoint shares the source because it is the same kind of fact — a wake-up fired — and
     * because anything scoped [NUDGE] is counted as an impression by the cap and by attribution.
     */
    SERVICE,
}

/**
 * One notification lifecycle event. [key], [source] and [event] are stored as names rather than
 * ordinals so reordering an enum can't silently reinterpret history.
 */
@Entity(tableName = "engagement_events")
data class EngagementEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "at")
    val at: Long,

    /** The nudge's enum name for a nudge; [PRESENCE_CHECK_KEY] for the session reminder. */
    @ColumnInfo(name = "nudge")
    val key: String,

    /** Which copy variant was used, so conversion can be compared across variants. Always 0 for PRESENCE. */
    @ColumnInfo(name = "variant")
    val variant: Int,

    @ColumnInfo(name = "event")
    val event: String,

    /** Defaulted to match the v1→v2 backfill: every row predating the column was a nudge. */
    @ColumnInfo(name = "source", defaultValue = "NUDGE")
    val source: String = EngagementSource.NUDGE.name,
)

/**
 * The [EngagementEvent.key] the session reminder is logged under.
 *
 * Deliberately a bare constant rather than a `Nudge` entry: adding it to that enum would make it
 * selectable by `NudgeEligibility`, listed in the Settings nudge loop, and force-sendable from the
 * debug harness — none of which apply to a reminder that belongs to one open session.
 *
 * Frozen for the same reason as [EngagementSource.PRESENCE]: the string is stored in `engagement.db`.
 */
const val PRESENCE_CHECK_KEY = "PRESENCE_CHECK"
