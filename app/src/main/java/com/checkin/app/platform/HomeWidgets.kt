package com.checkin.app.platform

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.checkin.app.ui.components.animationScaleOn
import com.checkin.app.widget.CheckInWidgetProvider
import com.checkin.app.widget.WidgetFace
import com.checkin.app.widget.WidgetViews

/** The home-screen widgets placed on this device: what they show, pushed from the app's side. */
interface HomeWidgets {
    /** Whether the system's animation scale is on, which decides whether the names cycle. */
    fun animationsOn(): Boolean

    /** Draws [face] on every placed widget; nothing when none is placed. */
    fun show(face: WidgetFace)
}

class AndroidHomeWidgets(private val context: Context) : HomeWidgets {

    override fun animationsOn(): Boolean = context.animationScaleOn()

    override fun show(face: WidgetFace) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CheckInWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, WidgetViews.build(context, face))
    }
}
