package com.checkin.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.checkin.app.R
import com.checkin.app.ui.components.SectionCard

/**
 * The app's whole notification surface: a pointer to Android's settings, which is where every one of
 * these is turned on and off. There are no in-app switches — an opt-out is a notification channel,
 * and a pref beside one could only ever agree with it or lie about it.
 *
 * It renders in both states rather than only the bad one. Healthy, it answers "how do I stop this?"
 * in the place a user looks for the answer; blocked, it takes the title and copy of whichever
 * [NotificationBlock] applies, because that state is otherwise invisible. A denied
 * POST_NOTIFICATIONS takes out the running timer, the session reminder and every nudge while leaving
 * every screen looking exactly as it does when all of it works — and the permission is asked for in
 * only two places (first open, then the presence gate at the first check-in), with Android dropping
 * the dialog silently after two refusals, so an install can sit that way permanently.
 *
 * Read on resume rather than held in the ViewModel: the only route to fixing it is system settings,
 * which returns with no result, so the grant has to be re-read on the way back.
 */
@Composable
internal fun NotificationsCard() {
    val context = LocalContext.current
    // Seeded with NONE rather than with a read: the resume effect fires on the first resume too, so
    // reading here as well would run the platform lookups twice to reach the same answer. Seeding
    // *healthy* is the right way round — the card renders either way now, so the one frame before
    // the read shows the neutral copy, and a warning that flashes up and disappears is worse than
    // one that appears a frame late.
    var block by remember { mutableStateOf(NotificationBlock.NONE) }

    LifecycleResumeEffect(Unit) {
        block = context.notificationBlock()
        onPauseOrDispose { }
    }

    SectionCard(title = stringResource(block.titleRes)) {
        HelpText(stringResource(block.helpRes))
        OutlinedButton(onClick = { context.openNotificationSettings() }) {
            Text(stringResource(R.string.settings_notifications_action))
        }
    }
}

/**
 * Opens this app's notification settings, falling back to its app-details page.
 *
 * The direct screen is one tap closer to the switch that matters, but it is not guaranteed to be
 * handled on every device, and the app-details page always is. Both are wrapped: an unhandled intent
 * throws, and a warning card that crashes the app is worse than the state it is warning about.
 */
@Suppress("SwallowedException")
private fun Context.openNotificationSettings() {
    val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))

    for (intent in listOf(direct, fallback)) {
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next one; there is nothing useful to tell the user if neither resolves.
        }
    }
}
