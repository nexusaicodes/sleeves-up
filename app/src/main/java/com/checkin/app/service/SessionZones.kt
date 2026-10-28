package com.checkin.app.service

import android.content.Context
import androidx.core.content.edit
import java.time.DateTimeException
import java.time.ZoneId

/**
 * The time zone the open session was checked in under, kept so its day boundary can be found
 * after a cloud restore.
 *
 * A session's midnight is the end of its `date_key` **in the zone that `date_key` was written in**.
 * [SessionAlarms] holds that instant once armed, but its file stays off a cloud backup (it describes
 * alarms the new device does not have), so a session restored open arrives with the row and nothing
 * else. Read in the restoring device's zone, the fallback is wrong in a way the row cannot reveal: a
 * 23:00 check-in in Tokyo is 07:00 the same date in Los Angeles, and LA's midnight would close it
 * 17 hours on instead of one, on a row nothing can edit.
 *
 * So this file is the one piece of live session state that **does** travel: one session id and one
 * zone id, written at check-in, cleared when the session closes. It is its own namespace because
 * `checkin_timer_prefs` is excluded from cloud backup and `data_extraction_rules.xml` excludes whole
 * files. The id guards against a stale entry: a zone recorded for any other session is ignored.
 */
interface SessionZones {
    /** Records [zone] as the one [sessionId] was checked in under, replacing any earlier entry. */
    fun record(sessionId: Long, zone: ZoneId)

    /** The zone recorded for [sessionId], or null when none was, or the stored id is unknown here. */
    fun zoneOf(sessionId: Long): ZoneId?

    fun clear()
}

class AndroidSessionZones(private val context: Context) : SessionZones {

    private val prefs
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun record(sessionId: Long, zone: ZoneId) = prefs.edit {
        putLong(KEY_SESSION_ID, sessionId)
        putString(KEY_ZONE_ID, zone.id)
    }

    // A zone id this device's tz database does not know reads as no record, falling back to the
    // device's own zone rather than failing the close.
    @Suppress("SwallowedException")
    override fun zoneOf(sessionId: Long): ZoneId? {
        if (prefs.getLong(KEY_SESSION_ID, NO_SESSION) != sessionId) return null
        val id = prefs.getString(KEY_ZONE_ID, null) ?: return null
        return try {
            ZoneId.of(id)
        } catch (e: DateTimeException) {
            null
        }
    }

    override fun clear() = prefs.edit { clear() }

    companion object {
        /**
         * Backed up with `_app`, whose session ids it names; excluded, a restored session would
         * close at the restoring device's midnight.
         */
        const val PREFS_NAME = "session_zone_prefs"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_ZONE_ID = "zone_id"
        private const val NO_SESSION = -1L
    }
}
