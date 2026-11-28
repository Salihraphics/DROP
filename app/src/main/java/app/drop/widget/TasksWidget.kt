package app.drop.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.drop.R
import app.drop.data.Note
import app.drop.ui.notes.cardTitle

/** Open tasks, ticked off right from the home screen. */
class TasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { render(context, manager, ids) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        runAsync { render(context, manager, intArrayOf(id)) }
    }

    companion object {
        const val ACTION_COMPLETE = "app.drop.widget.action.COMPLETE_TASK"
        const val EXTRA_TASK_ID = "app.drop.widget.extra.TASK_ID"

        private val rowIds = intArrayOf(R.id.task_row_0, R.id.task_row_1, R.id.task_row_2, R.id.task_row_3, R.id.task_row_4, R.id.task_row_5)
        private val checkIds = intArrayOf(R.id.task_check_0, R.id.task_check_1, R.id.task_check_2, R.id.task_check_3, R.id.task_check_4, R.id.task_check_5)
        private val titleIds = intArrayOf(R.id.task_title_0, R.id.task_title_1, R.id.task_title_2, R.id.task_title_3, R.id.task_title_4, R.id.task_title_5)

        suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val dao = context.app.database.noteDao()
            val tasks = dao.openTasks(MAX_WIDGET_ROWS)
            val total = dao.openTaskCount()
            val fontScale = context.resources.configuration.fontScale
            for (id in ids) {
                val rows = rowsThatFit(manager.getAppWidgetOptions(id), fontScale, chromeDp = 44, rowDp = 36)
                manager.updateAppWidget(id, build(context, tasks.take(rows), total))
            }
        }

        internal fun build(context: Context, tasks: List<Note>, total: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            val palette = WidgetPalette.forMode(context, context.app.settings.themeMode.value)
            views.setTextViewText(R.id.tasks_title, if (total > 0) context.getString(R.string.widget_tasks_title_count, total) else context.getString(R.string.widget_tasks_label))
            views.setOnClickPendingIntent(R.id.tasks_title, WidgetIntents.showTasks(context))
            views.setOnClickPendingIntent(R.id.tasks_view_all, WidgetIntents.showTasks(context))
            views.setOnClickPendingIntent(R.id.tasks_add, WidgetIntents.capture(context, task = true))
            views.setViewVisibility(R.id.tasks_empty, if (tasks.isEmpty()) View.VISIBLE else View.GONE)
            for (i in rowIds.indices) {
                val task = tasks.getOrNull(i)
                views.setViewVisibility(rowIds[i], if (task == null) View.GONE else View.VISIBLE)
                if (task == null) continue
                val title = cardTitle(task).ifEmpty { context.getString(R.string.note_empty) }
                views.setTextViewText(titleIds[i], title)
                views.setContentDescription(checkIds[i], context.getString(R.string.widget_complete_task, title))
                views.setOnClickPendingIntent(checkIds[i], WidgetIntents.completeTask(context, task.id))
                views.setOnClickPendingIntent(titleIds[i], WidgetIntents.openItem(context, task.id))
                if (palette != null) {
                    views.setTextColor(titleIds[i], palette.text)
                    views.setTint(checkIds[i], palette.textSecondary)
                }
            }
            if (palette != null) {
                views.setBackground(android.R.id.background, palette.background)
                views.setTextColor(R.id.tasks_title, palette.text)
                views.setTextColor(R.id.tasks_empty, palette.textSecondary)
                views.setTextColor(R.id.tasks_view_all, palette.accentContent)
            }
            return views
        }
    }
}

/** The tick on a task row: completes it (only if still open) and redraws the widgets. */
class TaskCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TasksWidget.ACTION_COMPLETE) return
        val id = intent.getLongExtra(TasksWidget.EXTRA_TASK_ID, -1L)
        if (id <= 0) return
        runAsync {
            context.app.database.noteDao().completeIfOpen(id, System.currentTimeMillis())
            DropWidgets.updateAll(context)
        }
    }
}
