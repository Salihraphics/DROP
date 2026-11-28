package app.drop.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.RemoteViews
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.capture.QuickCaptureActivity
import app.drop.data.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Context.app: DropApplication get() = applicationContext as DropApplication

/** The most rows any list widget shows. */
const val MAX_WIDGET_ROWS = 6

/** Redraws every DROP widget on the home screen. */
object DropWidgets {
    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        fun ids(cls: Class<*>) = manager.getAppWidgetIds(ComponentName(context, cls))
        QuickCaptureWidget.render(context, manager, ids(QuickCaptureWidget::class.java))
        RecentNotesWidget.render(context, manager, ids(RecentNotesWidget::class.java))
        TasksWidget.render(context, manager, ids(TasksWidget::class.java))
    }
}

/** What tapping parts of a widget does. */
object WidgetIntents {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, MainActivity.launchIntent(context), FLAGS)

    /** The data URI keeps one PendingIntent per note, so rows don't open each other's note. */
    fun openItem(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(context, 0, MainActivity.openNoteIntent(context, id).setData(Uri.parse("drop://item/$id")), FLAGS)

    fun showTasks(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, MainActivity.showTasksIntent(context), FLAGS)

    fun capture(context: Context, task: Boolean): PendingIntent =
        PendingIntent.getActivity(context, if (task) 1 else 0, QuickCaptureActivity.intent(context, task), FLAGS)

    fun completeTask(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TaskCompleteReceiver::class.java)
            .setAction(TasksWidget.ACTION_COMPLETE)
            .setData(Uri.parse("drop://task/$id"))
            .putExtra(TasksWidget.EXTRA_TASK_ID, id),
        FLAGS,
    )
}

/** Widget colours when DROP's theme is set to light or dark; null follows the phone (the XML default). */
class WidgetPalette(val background: Int, val field: Int, val text: Int, val textSecondary: Int, val accentContent: Int) {
    companion object {
        fun forMode(context: Context, mode: ThemeMode): WidgetPalette? = when (mode) {
            ThemeMode.System -> null
            ThemeMode.Light -> WidgetPalette(
                R.drawable.widget_bg_light,
                R.drawable.widget_field_light,
                context.getColor(R.color.drop_light_text),
                context.getColor(R.color.drop_light_text_secondary),
                context.getColor(R.color.drop_light_text),
            )
            ThemeMode.Dark -> WidgetPalette(
                R.drawable.widget_bg_dark,
                R.drawable.widget_field_dark,
                context.getColor(R.color.drop_dark_text),
                context.getColor(R.color.drop_dark_text_secondary),
                context.getColor(R.color.drop_accent),
            )
        }
    }
}

/** "Mar 4" this year, "Mar 4, 2025" before. */
class WidgetDateFormatter(locale: Locale, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val monthDay = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    private val full = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMd"), locale)

    fun format(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val thisYear = Instant.ofEpochMilli(now).atZone(zone).year
        return (if (date.year == thisYear) monthDay else full).format(date)
    }
}

/** How many rows fit in a widget of the height the launcher gave it. */
fun rowsThatFit(options: Bundle?, fontScale: Float, chromeDp: Int, rowDp: Int): Int {
    val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
    if (height <= 0) return 3
    val row = rowDp * fontScale.coerceAtLeast(1f)
    return ((height - chromeDp) / row).toInt().coerceIn(1, MAX_WIDGET_ROWS)
}

internal fun RemoteViews.setBackground(viewId: Int, drawable: Int) = setInt(viewId, "setBackgroundResource", drawable)

internal fun RemoteViews.setTint(viewId: Int, color: Int) = setInt(viewId, "setColorFilter", color)
