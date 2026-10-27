package com.checkin.app.util

/**
 * The millisecond ladder every duration constant is built from, so no file writes `60 * 60 * 1000`
 * inline or keeps its own copy of a minute. Long, for `System.currentTimeMillis` arithmetic; the CSV
 * and the Reports charts keep their own fractional `MILLIS_PER_HOUR`, since they divide by it.
 */
internal const val MILLIS_PER_SECOND = 1_000L
internal const val MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND
internal const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
