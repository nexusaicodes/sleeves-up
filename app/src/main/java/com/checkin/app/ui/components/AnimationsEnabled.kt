package com.checkin.app.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether the system's animation scale is on — false when the user has chosen "remove animations".
 *
 * An animation that ignores that setting is what it exists to stop, so both callers read it here
 * rather than each carrying the lookup. **Re-read on resume, not cached for the life of the
 * composition**: changing it raises no configuration change and recreates no activity, so a value
 * read once would leave the pulse breathing and the pager sliding for a user who had just turned
 * animations off and come back. The only way in is system settings, which is a round trip out of the
 * app and back — so resume is exactly when the answer can have changed.
 */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(context.animationScaleOn()) }

    LifecycleResumeEffect(context) {
        enabled = context.animationScaleOn()
        onPauseOrDispose { }
    }

    return enabled
}

private fun Context.animationScaleOn(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
