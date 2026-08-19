package com.checkin.app.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Read/write seam over the `prompt_prefs` namespace so callers never touch SharedPreferences
 * directly (makes them pure-JVM testable).
 *
 * Everything here records that the app has already raised a one-time prompt, so it doesn't raise it
 * twice. Every flag describes an interaction with *this device*, not anything about the user's
 * record — which is why none of them is restored from a cloud backup (see
 * `data_extraction_rules.xml`).
 *
 * Nothing about the record belongs here for that same reason: the day tracking began is read from
 * the sessions table (`CheckInRepository.trackingStartFlow`), never stored, so a restore cannot
 * bring it back without the rows it indexes. There is no setting to seed or read.
 *
 * **A new one-time prompt is a new key here, never a new prefs file.** Both backup rules exclude
 * `prompt_prefs.xml` by name, so a key added here inherits that; a separate namespace would need the
 * same exclusion added to two XML files, and missing either restores onto a device asserting that a
 * prompt was raised where it never was. It follows that a prompt added after an install base exists
 * defaults false and is shown to everyone once — that is the design, not a regression.
 */
interface PromptSettings {
    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(): Boolean

    /** Records that the camera prominent-disclosure screen has been shown and accepted. */
    fun markCameraDisclosureSeen()

    /** Whether the launch-time notification permission request has already been made. */
    fun hasAskedNotifications(): Boolean

    /** Records that it has, so the app asks once rather than on every cold start. */
    fun markNotificationsAsked()

    /** Whether the first-run welcome tour has already been shown. */
    fun hasSeenWelcome(): Boolean

    /** Records that it has, so it runs once rather than on every launch. */
    fun markWelcomeSeen()
}

class SharedPrefsPromptSettings(private val prefs: SharedPreferences) : PromptSettings {

    companion object {
        const val NAME = "prompt_prefs"

        private const val KEY_CAMERA_DISCLOSURE_SEEN = "camera_disclosure_seen"
        private const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"
        private const val KEY_WELCOME_SEEN = "welcome_seen"

        fun create(context: Context) = SharedPrefsPromptSettings(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    override fun hasSeenCameraDisclosure(): Boolean = prefs.getBoolean(KEY_CAMERA_DISCLOSURE_SEEN, false)

    override fun markCameraDisclosureSeen() {
        prefs.edit { putBoolean(KEY_CAMERA_DISCLOSURE_SEEN, true) }
    }

    /**
     * Persisted rather than derived from the grant state, because "refused" and "not yet asked" look
     * identical through [android.content.pm.PackageManager] — without this the app would re-request
     * on every cold start, and Android silently drops the dialog after two refusals, so the user
     * would see nothing while the app kept asking.
     */
    override fun hasAskedNotifications(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)

    override fun markNotificationsAsked() {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS_ASKED, true) }
    }

    override fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_WELCOME_SEEN, false)

    override fun markWelcomeSeen() {
        prefs.edit { putBoolean(KEY_WELCOME_SEEN, true) }
    }
}
