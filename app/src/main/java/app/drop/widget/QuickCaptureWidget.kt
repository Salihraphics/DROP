package app.drop.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import app.drop.R

/** "Drop a thought…": one tap to a new note or task. */
class QuickCaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { render(context, manager, ids) }
    }

    companion object {
        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, build(context))
        }

        internal fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_capture)
            views.setOnClickPendingIntent(R.id.capture_field, WidgetIntents.capture(context, task = false))
            views.setOnClickPendingIntent(R.id.capture_note, WidgetIntents.capture(context, task = false))
            views.setOnClickPendingIntent(R.id.capture_task, WidgetIntents.capture(context, task = true))
            WidgetPalette.forMode(context, context.app.settings.themeMode.value)?.let { p ->
                views.setBackground(android.R.id.background, p.background)
                views.setBackground(R.id.capture_field, p.field)
                views.setTextColor(R.id.capture_field, p.textSecondary)
                views.setTint(R.id.capture_task, p.text)
            }
            return views
        }
    }
}
