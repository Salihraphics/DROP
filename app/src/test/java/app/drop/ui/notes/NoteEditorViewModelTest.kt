package app.drop.ui.notes

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.NoteFormat
import app.drop.richtext.InlineStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

/** The editor never loses text, never creates duplicates and never stores empty notes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NoteEditorViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DropDatabase
    private val main = Executors.newSingleThreadExecutor()
    private var now = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(main.asCoroutineDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        main.shutdownNow()
    }

    private fun onMain(block: () -> Unit) = main.submit(block).get()

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle(), trashed: MutableList<Long> = mutableListOf(), history: MutableList<Pair<Note, Note>> = mutableListOf()) =
        NoteEditorViewModel(
            handle, db.noteDao(), clock = { now }, autosaveDelayMs = 50,
            onTrashed = { trashed += it }, beforeWrite = { a, b -> history += a to b },
        ).also { v -> waitFor("load") { v.isLoaded } }

    private fun notes() = runBlocking { db.noteDao().allNotes() }

    @Test
    fun aNewNoteIsStoredOnceAndKeepsItsId() {
        val handle = SavedStateHandle()
        val v = vm(handle)
        onMain { v.onTitleChange("Alışveriş") }
        waitFor("first save") { notes().size == 1 }
        onMain { v.onBodyChange("süt, ekmek") }
        waitFor("second save") { notes().single().body == "süt, ekmek" }
        assertEquals(1, notes().size)
        val id = notes().single().id
        assertEquals(id, v.noteId)
        // After the process is killed the editor reopens the same note, it never makes a copy.
        val restored = vm(handle)
        assertEquals("Alışveriş", restored.title)
        onMain { restored.onBodyChange("süt, ekmek, peynir") }
        waitFor("save after restore") { notes().single().body == "süt, ekmek, peynir" }
        assertEquals(1, notes().size)
    }

    @Test
    fun anEmptyNewNoteIsNeverStored() {
        val v = vm()
        onMain { v.onTitleChange("   ") }
        Thread.sleep(200)
        onMain { v.saveAndClose() }
        waitFor("close") { v.result != null }
        assertTrue(notes().isEmpty())
        assertNull(v.result!!.trashed)
    }

    @Test
    fun formattingIsStoredAsMarkdown() {
        val v = vm()
        onMain {
            v.onBodyChange("bold")
            v.editor.selectRange(0, 4)
            v.editor.toggleInline(InlineStyle.Bold)
        }
        waitFor("save") { notes().singleOrNull()?.format == NoteFormat.MARKDOWN }
        assertEquals("**bold**", notes().single().body)
    }

    @Test
    fun pinningAndCompletingKeepTheDateButEditingChangesIt() {
        val id = runBlocking { db.noteDao().insert(Note(title = "Task", body = "", createdAt = 10, updatedAt = 10, isTask = true)) }
        val history = mutableListOf<Pair<Note, Note>>()
        val v = vm(SavedStateHandle(mapOf(NoteEditorViewModel.ARG_NOTE_ID to id)), history = history)
        now = 5_000
        onMain { v.onPinnedChange(true) }
        waitFor("pin") { notes().single().pinned }
        onMain { v.onCompletedChange(true) }
        waitFor("complete") { notes().single().completedAt == 5_000L }
        assertEquals(10L, notes().single().updatedAt)
        assertTrue(history.isEmpty())
        now = 9_000
        onMain { v.onTitleChange("Task done") }
        waitFor("edit") { notes().single().title == "Task done" }
        assertEquals(9_000L, notes().single().updatedAt)
        // Time Machine got the previous version before it was replaced.
        assertEquals("Task", history.single().first.title)
    }

    @Test
    fun closingAnEmptiedNoteMovesItToTrashInsteadOfDeletingIt() {
        val id = runBlocking { db.noteDao().insert(Note(title = "Old", body = "text", createdAt = 1, updatedAt = 1)) }
        val trashed = mutableListOf<Long>()
        val v = vm(SavedStateHandle(mapOf(NoteEditorViewModel.ARG_NOTE_ID to id)), trashed = trashed)
        onMain {
            v.onTitleChange("")
            v.onBodyChange("")
            v.saveAndClose()
        }
        waitFor("close") { v.result != null }
        assertEquals(TrashedNote(id, wasEmpty = true), v.result!!.trashed)
        val note = notes().single()
        assertNotNull(note.deletedAt)
        // The text is still there to undo: only Trash, never deleted.
        assertEquals("Old", note.title)
        assertEquals(listOf(id), trashed)
    }

    @Test
    fun closingRightAfterTypingStillSaves() {
        val v = vm()
        onMain {
            v.onTitleChange("Quick thought")
            v.saveAndClose()
        }
        waitFor("close") { v.result != null }
        assertEquals("Quick thought", notes().single().title)
    }

    @Test
    fun aNewTaskStartsAsATask() {
        val v = vm(SavedStateHandle(mapOf(NoteEditorViewModel.ARG_TASK to true)))
        assertTrue(v.isTask)
        onMain { v.onTitleChange("Call") }
        waitFor("save") { notes().isNotEmpty() }
        assertTrue(notes().single().isTask)
    }
}
