package com.checkin.app.util

private const val SYNC_RETRY_MS = 30 * MILLIS_PER_SECOND
private const val SYNC_RETRY_MAX_MS = 30 * MILLIS_PER_MINUTE

/** Enough to pass the cap; few enough that the shift cannot overflow. */
private const val SYNC_MAX_DOUBLINGS = 6L

/** A table collector's wait before re-subscribing after a failed read: doubling from 30s, capped at 30min. */
internal fun syncRetryDelayMs(attempt: Long): Long =
    (SYNC_RETRY_MS shl attempt.coerceAtMost(SYNC_MAX_DOUBLINGS).toInt())
        .coerceAtMost(SYNC_RETRY_MAX_MS)
