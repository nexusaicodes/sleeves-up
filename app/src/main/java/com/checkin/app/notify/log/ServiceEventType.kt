package com.checkin.app.notify.log

/**
 * What happened to a piece of the app's background machinery — the foreground service, a session
 * alarm, or the nudge checkpoint alarm. Stored in the same `event` column as [EngagementEventType],
 * and safe to share it because every query that reads that column is also scoped to a
 * [EngagementSource] — these names only ever appear against [EngagementSource.SERVICE].
 *
 * The engagement checkpoint is logged here despite living in `notify/` rather than `service/`: what
 * these rows record is a wake-up that fired, and the alternative — a nudge-scoped source — would put
 * it in front of the daily cap and conversion attribution, which must only ever see real impressions.
 */
enum class ServiceEventType {
    /** The service entered the foreground for a session. */
    STARTED,

    /** The service tore itself down: check-out, or a reconcile that found no open session. */
    STOPPED,

    /** The watchdog found an open session with no service and restarted it. */
    REVIVED,

    /** A session alarm was set, with the target instant as the detail. */
    ALARM_SET,

    /** A session alarm fired and was handled. */
    ALARM_FIRED,

    /**
     * The nudge checkpoint alarm fired and a dispatch pass ran. Distinct from [ALARM_FIRED] because
     * the diagnostics card prints these names verbatim, and a reader chasing a missing nudge needs to
     * tell "the checkpoint never woke" from "a session alarm fired" at a glance.
     */
    CHECKPOINT_FIRED,

    /**
     * A platform call was refused or threw and the app carried on. The single most useful row in
     * this table: it is the difference between "the service died" and "the service died *because*".
     */
    DEGRADED,
}
