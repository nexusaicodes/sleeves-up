package com.checkin.app.notify.engagement

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * The install's identity for engagement purposes, and deliberately nothing else.
 *
 * **No notification preference belongs here.** An opt-out is the notification's channel, which the
 * user owns and the app cannot override — see the "A notification's opt-out is its channel" entry in
 * `CLAUDE.md` for why a pref beside one can only ever agree with it or lie about it.
 *
 * The bucketing id is genuinely the app's to keep: it decides which wording of a nudge this install
 * sees, so it has to be stable for the life of the install and identical across every read.
 */
interface EngagementInstall {
    /** Stable per-install id for variant bucketing. Random, local, and never leaves the device. */
    fun installId(): String
}

class SharedPrefsEngagementInstall(private val prefs: SharedPreferences) : EngagementInstall {

    companion object {
        /**
         * Frozen, both of them. The id stored under this key is what [VariantAssigner] buckets on, so
         * renaming either the namespace or the key hands every install a fresh id and reshuffles the
         * variant assignment of an experiment already in flight — silently, and with the old and new
         * cohorts averaged together in the log.
         *
         * The namespace keeps its `engagement_prefs` name for the same reason `engagement.db` keeps
         * its own: the name is a half-truth now that only an id lives here, and a rename is a new file
         * that orphans what the old one holds.
         */
        const val NAME = "engagement_prefs"
        private const val KEY_INSTALL_ID = "install_id"

        fun create(context: Context) = SharedPrefsEngagementInstall(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    override fun installId(): String = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_INSTALL_ID, it) }
    }
}
