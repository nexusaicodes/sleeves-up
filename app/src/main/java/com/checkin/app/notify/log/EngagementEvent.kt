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
 * One notification lifecycle event. [key], [source] and [event] are stored as names rather than
 * ordinals so reordering an enum can't silently reinterpret history.
 */
@Entity(tableName = "engagement_events")
data class EngagementEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "at")
    val at: Long,

    /**
     * The nudge's enum name for a nudge; [PRESENCE_CHECK_KEY] for the session reminder; the free-text
     * detail for a SERVICE row.
     */
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
