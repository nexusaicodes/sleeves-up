package com.checkin.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.checkin.app.MainActivity
import com.checkin.app.R
import com.checkin.app.notify.LaunchExtras

/**
 * Builds the widget's views for a [WidgetFace]. Everything the launcher can draw on its own — the
 * colours for light and dark, the fade between names — is in the layout, resolved by the launcher in
 * its own configuration; only the words and which views show are set here.
 */
object WidgetViews {

    /**
     * The widget's one tap target, in the `PendingIntent` request-code namespace `NotificationIds`
     * tables. **Frozen**, as every code there is: a placed widget holds this intent across an update.
     */
    const val TAP_REQUEST_CODE = 40_000

    fun build(context: Context, face: WidgetFace): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_check_in).apply {
            setOnClickPendingIntent(android.R.id.background, tapIntent(context))
            // A stable description rather than the cycling text, which a screen reader cannot follow.
            setContentDescription(android.R.id.background, label(context, face))
            val running = face is WidgetFace.Running
            setViewVisibility(R.id.widget_mark_running, if (running) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_mark_rest, if (running) View.GONE else View.VISIBLE)

            // Emptied either way: a host may re-apply these views over its previous ones, children kept.
            removeAllViews(R.id.widget_flipper)
            if (face is WidgetFace.Cycling) {
                face.names.forEach { name ->
                    val label = RemoteViews(context.packageName, R.layout.widget_label)
                    label.setTextViewText(R.id.widget_label_text, checkInLabel(context, name))
                    addView(R.id.widget_flipper, label)
                }
                setViewVisibility(R.id.widget_flipper, View.VISIBLE)
                setViewVisibility(R.id.widget_label, View.GONE)
            } else {
                setTextViewText(R.id.widget_label, label(context, face))
                setViewVisibility(R.id.widget_flipper, View.GONE)
                setViewVisibility(R.id.widget_label, View.VISIBLE)
            }
        }

    /**
     * The face in one line: the static label, and the description of every face. A cycling face's
     * line is the plain "Check In", which a screen reader reads in place of the names going by.
     */
    private fun label(context: Context, face: WidgetFace): String = when (face) {
        is WidgetFace.Running -> checkOutLabel(context, face.name)
        is WidgetFace.Single -> checkInLabel(context, face.name)
        is WidgetFace.Cycling, WidgetFace.Plain -> context.getString(R.string.check_in)
    }

    private fun checkInLabel(context: Context, name: String?): String =
        name?.let { context.getString(R.string.check_in_to, it) } ?: context.getString(R.string.check_in)

    private fun checkOutLabel(context: Context, name: String?): String =
        name?.let { context.getString(R.string.check_out_of, it) } ?: context.getString(R.string.check_out)

    /**
     * One intent whatever the face: "the widget was tapped", and nothing about what it showed. What
     * the tap opens is decided in `MainActivity` from the rows at that moment, so a face that has not
     * caught up with a change still routes correctly. Explicit, not a deep link, so nothing else on
     * the device can open the gate through it.
     */
    private fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LaunchExtras.WIDGET_TAP, true)
        }
        return PendingIntent.getActivity(
            context,
            TAP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
