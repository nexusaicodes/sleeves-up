package com.checkin.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.checkin.app.CheckInApplication
import kotlinx.coroutines.launch

/**
 * The home-screen widget: one button that checks in — or, while a session is open, checks out —
 * through the presence gate, for whichever habit the tap resolves to. See [WidgetFace] for what it
 * shows and [WidgetViews] for its single, state-free tap intent.
 *
 * The system calls [onUpdate] when a widget is placed, after a reboot or an update, and when the
 * launcher restarts; every change made while the app runs reaches the widget through
 * `AppContainer.launchWidgetSync` instead, so no write path has to remember it.
 *
 * **This class's path is frozen once shipped.** A placed widget names this component, so moving or
 * renaming it takes the widget off every home screen it is on.
 */
class CheckInWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val container = (context.applicationContext as CheckInApplication).container
        // The faces come off the database, so the render runs off the main thread and holds the
        // broadcast open until it lands.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.refreshWidgets()
            } finally {
                pending.finish()
            }
        }
    }
}
