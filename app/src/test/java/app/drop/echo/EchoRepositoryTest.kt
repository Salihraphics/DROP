package app.drop.echo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.NoteFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ECHO reads only open notes, follows changes, stops when switched off and remembers what to hold back. */
@RunWith(RobolectricTestRunner::class)
class EchoRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DropDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
        context.getSharedPreferences("echo_memory", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun add(title: String, body: String, updated: Long = 1, deleted: Long? = null, format: Int = NoteFormat.PLAIN) =
        runBlocking { db.noteDao().insert(Note(title = title, body = body, createdAt = 1, updatedAt = updated, deletedAt = deleted, format = format)) }

    @Test
    fun theIndexFollowsTheLibraryAndLeavesOutTrash(): Unit = runBlocking {
        val a = add("Garden plan", "**tomatoes** and basil seedlings", format = NoteFormat.MARKDOWN)
        val b = add("Garden diary", "basil seedlings sprouted, tomatoes next")
        val trashed = add("Garden trash", "basil seedlings tomatoes", deleted = 5)
        val enabled = MutableStateFlow(true)
        val repo = EchoRepository(db.echoDao(), enabled, scope, debounceMs = 10)
        val index = withTimeout(5_000) { repo.index.filterNotNull().first() }
        assertEquals(2, index.size)
        assertNull(index.doc(trashed))
        // Markdown is read as plain text.
        assertEquals("tomatoes and basil seedlings", index.doc(a)!!.body)
        assertEquals(listOf(b), index.related(a).map { it.noteId })

        enabled.value = false
        withTimeout(5_000) { repo.index.first { it == null } }
        Unit
    }

    @Test
    fun aDismissedSuggestionStaysHiddenAndSurvivesARestart() {
        var now = 1_000L
        val memory = EchoMemory(context) { now }
        memory.dismiss(7)
        memory.opened(8)
        assertEquals(setOf(7L, 8L), memory.suppressed())
        assertEquals(1_000L + EchoMemory.DISMISS_MS, memory.suppressedUntil.value[7])

        // After 14 days an opened note may come back; a dismissed one only after 90.
        now += EchoMemory.OPENED_MS + 1
        assertEquals(setOf(7L), memory.suppressed())
        assertEquals(setOf(7L), EchoMemory(context) { now }.suppressed())

        memory.clear()
        assertTrue(EchoMemory(context) { now }.suppressed().isEmpty())
    }

    @Test
    fun memoryKeepsAtMostTheNewest300() {
        var now = 0L
        val memory = EchoMemory(context) { now }
        for (i in 1..350L) {
            now = i
            memory.dismiss(i)
        }
        val kept = EchoMemory(context) { now }.suppressedUntil.value
        assertEquals(EchoMemory.MAX_ENTRIES, kept.size)
        assertTrue(350L in kept && 51L in kept && 50L !in kept)
    }
}
