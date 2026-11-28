package app.drop.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.drop.R
import app.drop.data.Links
import app.drop.data.Note
import app.drop.ui.notes.cardTitle

/** The notes edited most recently. */
class RecentNotesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { render(context, manager, ids) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        runAsync { render(context, manager, intArrayOf(id)) }
    }

    companion object {
        private val rowIds = intArrayOf(R.id.note_row_0, R.id.note_row_1, R.id.note_row_2, R.id.note_row_3, R.id.note_row_4, R.id.note_row_5)
        private val titleIds = intArrayOf(R.id.note_title_0, R.id.note_title_1, R.id.note_title_2, R.id.note_title_3, R.id.note_title_4, R.id.note_title_5)
        private val metaIds = intArrayOf(R.id.note_meta_0, R.id.note_meta_1, R.id.note_meta_2, R.id.note_meta_3, R.id.note_meta_4, R.id.note_meta_5)

        suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val notes = context.app.database.noteDao().recentNotes(MAX_WIDGET_ROWS)
            val fontScale = context.resources.configuration.fontScale
            for (id in ids) {
                val rows = rowsThatFit(manager.getAppWidgetOptions(id), fontScale, chromeDp = 64, rowDp = 52)
                manager.updateAppWidget(id, build(context, notes.take(rows)))
            }
        }

        internal fun build(context: Context, notes: List<Note>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_recent_notes)
            val dates = WidgetDateFormatter(context.resources.configuration.locales[0])
            val palette = WidgetPalette.forMode(context, context.app.settings.themeMode.value)
            views.setOnClickPendingIntent(R.id.recent_title, WidgetIntents.openApp(context))
            views.setOnClickPendingIntent(R.id.recent_add, WidgetIntents.capture(context, task = false))
            views.setViewVisibility(R.id.recent_empty, if (notes.isEmpty()) View.VISIBLE else View.GONE)
            for (i in rowIds.indices) {
                val note = notes.getOrNull(i)
                views.setViewVisibility(rowIds[i], if (note == null) View.GONE else View.VISIBLE)
                if (note == null) continue
                val title = cardTitle(note).ifEmpty { context.getString(R.string.note_empty) }
                val host = note.url?.let(Links::host)
                val date = dates.format(note.updatedAt)
                views.setTextViewText(titleIds[i], title)
                views.setTextViewText(metaIds[i], if (host != null) "$date · $host" else date)
                views.setOnClickPendingIntent(rowIds[i], WidgetIntents.openItem(context, note.id))
                if (palette != null) {
                    views.setTextColor(titleIds[i], palette.text)
                    views.setTextColor(metaIds[i], palette.textSecondary)
                }
            }
            if (palette != null) {
                views.setBackground(android.R.id.background, palette.background)
                views.setTextColor(R.id.recent_title, palette.text)
                views.setTextColor(R.id.recent_empty, palette.textSecondary)
            }
            return views
        }
    }
}
