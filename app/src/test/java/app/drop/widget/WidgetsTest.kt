package app.drop.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.drop.DropApplication
import app.drop.R
import app.drop.capture.QuickCaptureViewModel
import app.drop.data.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** The home-screen widgets and quick capture: what they show, and what their buttons change. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class WidgetsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = context as DropApplication
    private val dao get() = app.database.noteDao()

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun View.visible(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    private fun inflate(views: android.widget.RemoteViews): View = views.apply(context, FrameLayout(context))

    @Test
    fun recentNotesShowTheirTitlesDatesAndLinks() {
        val now = System.currentTimeMillis()
        val notes = listOf(
            Note(id = 1, title = "Trip plan", body = "", createdAt = now, updatedAt = now),
            Note(id = 2, title = "", body = "Read later https://example.com/article", createdAt = now, updatedAt = now, url = "https://example.com/article"),
        )
        val view = inflate(RecentNotesWidget.build(context, notes))
        assertTrue(view.visible(R.id.note_row_0))
        assertTrue(view.visible(R.id.note_row_1))
        assertTrue(!view.visible(R.id.note_row_2))
        assertTrue(!view.visible(R.id.recent_empty))
        assertEquals("Trip plan", view.text(R.id.note_title_0))
        assertTrue(view.text(R.id.note_meta_1).endsWith(" · example.com"))

        val empty = inflate(RecentNotesWidget.build(context, emptyList()))
        assertTrue(empty.visible(R.id.recent_empty))
        assertTrue(!empty.visible(R.id.note_row_0))
    }

    @Test
    fun theTasksWidgetCountsOpenTasksAndLabelsEachTick() {
        val tasks = (1L..3L).map { Note(id = it, title = "Task $it", body = "", createdAt = 1, updatedAt = 1, isTask = true) }
        val view = inflate(TasksWidget.build(context, tasks, total = 9))
        assertEquals(context.getString(R.string.widget_tasks_title_count, 9), view.text(R.id.tasks_title))
        assertEquals("Task 2", view.text(R.id.task_title_1))
        assertEquals(context.getString(R.string.widget_complete_task, "Task 2"), view.findViewById<View>(R.id.task_check_1).contentDescription)
        assertTrue(!view.visible(R.id.task_row_3))
        val none = inflate(TasksWidget.build(context, emptyList(), total = 0))
        assertEquals(context.getString(R.string.widget_tasks_label), none.text(R.id.tasks_title))
        assertTrue(none.visible(R.id.tasks_empty))
    }

    @Test
    fun tickingATaskOnTheWidgetCompletesIt() {
        val id = runBlocking { dao.insert(Note(title = "Water the plants", body = "", createdAt = 1, updatedAt = 1, isTask = true)) }
        TaskCompleteReceiver().onReceive(
            context,
            Intent(context, TaskCompleteReceiver::class.java).setAction(TasksWidget.ACTION_COMPLETE).putExtra(TasksWidget.EXTRA_TASK_ID, id),
        )
        val end = System.currentTimeMillis() + 10_000
        while (runBlocking { dao.getById(id) }!!.completedAt == null) {
            check(System.currentTimeMillis() < end) { "task was not completed" }
            Thread.sleep(20)
        }
        assertNotNull(runBlocking { dao.getById(id) }!!.completedAt)
    }

    @Test
    fun rowsFollowTheWidgetHeight() {
        assertEquals(3, rowsThatFit(null, 1f, 64, 52))
        val tall = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 64 + 52 * 4 + 10) }
        assertEquals(4, rowsThatFit(tall, 1f, 64, 52))
        // Bigger text means fewer rows, never fewer than one or more than six.
        assertEquals(2, rowsThatFit(tall, 1.6f, 64, 52))
        assertEquals(MAX_WIDGET_ROWS, rowsThatFit(Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 5000) }, 1f, 64, 52))
        assertEquals(1, rowsThatFit(Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 70) }, 1f, 64, 52))
    }

    @Test
    fun datesLeaveOutThisYear() {
        val f = WidgetDateFormatter(Locale.US, ZoneOffset.UTC)
        val now = LocalDate.of(2026, 11, 28).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val thisYear = LocalDate.of(2026, 3, 4).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val lastYear = LocalDate.of(2025, 3, 4).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("Mar 4", f.format(thisYear, now))
        assertEquals("Mar 4, 2025", f.format(lastYear, now))
    }

    @Test
    fun quickCaptureSplitsATaskAndNeverSavesTwice() {
        val vm = QuickCaptureViewModel(SavedStateHandle(), dao, clock = { 7 })
        val task = runBlocking { vm.persist("  Call the plumber\nAsk about the boiler  ", asTask = true) }
        assertEquals("Call the plumber", task.title)
        assertEquals("Ask about the boiler", task.body)
        assertTrue(task.isTask)
        // A retry after a failure finds the note it already made.
        val again = runBlocking { vm.persist("Call the plumber\nAsk about the boiler", asTask = true) }
        assertEquals(task.id, again.id)
        assertEquals(1, runBlocking { dao.allNotes() }.size)

        val note = runBlocking { QuickCaptureViewModel(SavedStateHandle(), dao).persist("Idea: a\nsecond line", asTask = false) }
        assertEquals("", note.title)
        assertEquals("Idea: a\nsecond line", note.body)
    }
}
