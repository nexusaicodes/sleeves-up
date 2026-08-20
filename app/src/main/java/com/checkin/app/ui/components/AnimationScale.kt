package com.checkin.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system's animation scale is on — false when the user has chosen "remove animations".
 *
 * An animation that ignores that setting is what it exists to stop, so both callers read it here
 * rather than each carrying the lookup. Read once per context: the setting only changes from system
 * settings, which restarts the activity.
 */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
