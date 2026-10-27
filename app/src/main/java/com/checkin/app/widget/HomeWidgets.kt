package com.checkin.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import com.checkin.app.util.animationScaleOn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The home-screen widgets placed on this device: what they show, pushed from the app's side. */
interface HomeWidgets {
    /**
     * Whether the system's animation scale is on, which decides whether the names cycle; now, then on
     * each change. Only while the process lives: no broadcast announces a change, so one made while it
     * is dead shows at the next process start.
     */
    fun animationsOn(): Flow<Boolean>

    /** Whether any widget is placed. */
    fun placed(): Boolean

    /** Draws [face] on every placed widget; false when none is placed, so nothing was drawn. */
    fun show(face: WidgetFace): Boolean
}

class AndroidHomeWidgets(private val context: Context) : HomeWidgets {

    override fun animationsOn(): Flow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(context.animationScaleOn())
            }
        }
        val resolver = context.contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        resolver.registerContentObserver(uri, false, observer)
        trySend(context.animationScaleOn())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    override fun placed(): Boolean = placedIds(manager()).isNotEmpty()

    override fun show(face: WidgetFace): Boolean {
        val manager = manager() ?: return false
        val ids = placedIds(manager)
        if (ids.isEmpty()) return false
        manager.updateAppWidget(ids, WidgetViews.build(context, face))
        return true
    }

    private fun manager(): AppWidgetManager? = AppWidgetManager.getInstance(context)

    private fun placedIds(manager: AppWidgetManager?): IntArray =
        manager?.getAppWidgetIds(ComponentName(context, CheckInWidgetProvider::class.java)) ?: IntArray(0)
}
