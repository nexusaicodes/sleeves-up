package com.checkin.app.ui.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.core.net.toUri

/**
 * Every outbound link in the app.
 *
 * CheckIn's own code makes no network calls — it only ever hands an intent to the system: the
 * browser fetches the policy, the mail app sends the feedback, the Play app handles the review.
 * The merged manifest carries no INTERNET permission at all, which is a claim the listing and the
 * privacy policy both make — a dependency that reintroduces one takes it down with them. Adding a
 * network call here would mean session data leaving the device and a new Data Safety declaration.
 *
 * Each launcher returns `false` rather than throwing when the intent can't be handed off, so the
 * caller can fall back to [copyToClipboard]. A device with no browser or no mail app is unusual but
 * entirely legal, and an uncaught launch failure would otherwise crash the app.
 */
object ExternalLinks {

    /** Also set by hand in the Play Console, which is a separate copy that drifts silently. */
    const val PRIVACY_POLICY_URL = "https://nexusai.world/sleeves-up/privacy"

    fun openUrl(context: Context, url: String): Boolean = launch(context, Intent(Intent.ACTION_VIEW, url.toUri()))

    /**
     * Prefers the Play app's own scheme so the rating sheet opens in place; falls back to the web
     * listing when Play is absent, which is the case on many emulators.
     */
    fun openPlayListing(context: Context): Boolean {
        val id = context.packageName
        return launch(context, Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri())) ||
            openUrl(context, playListingUrl(context))
    }

    /** The web listing, for the clipboard fallback when neither Play nor a browser is present. */
    fun playListingUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    /**
     * Opens a pre-filled draft. The subject and body travel as extras rather than encoded into the
     * mailto URI: extras survive every mail client, whereas percent-encoded newlines in a `body=`
     * parameter do not.
     */
    fun sendFeedback(context: Context, draft: FeedbackDraft): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            // ACTION_SENDTO with a mailto: URI resolves to mail apps only — unlike ACTION_SEND,
            // which would offer every share target on the device.
            // The recipient goes in the URI *and* in EXTRA_EMAIL: clients disagree about which one
            // is authoritative, and one that reads only the URI would otherwise open an empty To:.
            data = "mailto:${Uri.encode(Feedback.ADDRESS)}".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(Feedback.ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
        }
        return launch(context, intent)
    }

    /**
     * Returns false when the clipboard is unreachable, so a caller never claims to have copied
     * something it didn't. A null manager is rare but real on restricted profiles.
     */
    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        val manager = context.getSystemService<ClipboardManager>() ?: return false
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        return true
    }

    /**
     * [SecurityException] is caught alongside the expected [ActivityNotFoundException]: a handler in
     * another profile, or one guarded by a permission this app doesn't hold, throws that instead, and
     * it would escape the boolean contract and crash the very fallback the callers rely on.
     */
    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
