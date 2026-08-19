package com.checkin.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system's animation scale is on — false when the user has chosen "remove animations".
 *
 * An animation that ignores that setting is exactly what it exists to stop, and the two callers that
 * must honour it read it from here rather than each carrying the lookup: an infinite pulse that kept
 * breathing and a pager that kept sliding would be the same bug found twice.
 *
 * Read once per context rather than observed. The setting changes from system settings, which
 * restarts the activity, and a live subscription for a value that moves that rarely is machinery
 * with nothing to do.
 */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
