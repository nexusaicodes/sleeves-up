package com.checkin.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.checkin.app.util.animationScaleOn

/** The home-screen widgets placed on this device: what they show, pushed from the app's side. */
interface HomeWidgets {
    /** Whether the system's animation scale is on, which decides whether the names cycle. */
    fun animationsOn(): Boolean

    /** Whether any widget is placed. */
    fun placed(): Boolean

    /** Draws [face] on every placed widget; nothing when none is placed. */
    fun show(face: WidgetFace)
}

class AndroidHomeWidgets(private val context: Context) : HomeWidgets {

    override fun animationsOn(): Boolean = context.animationScaleOn()

    override fun placed(): Boolean = placedIds(AppWidgetManager.getInstance(context)).isNotEmpty()

    override fun show(face: WidgetFace) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = placedIds(manager)
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, WidgetViews.build(context, face))
    }

    private fun placedIds(manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(context, CheckInWidgetProvider::class.java))
}
