package app.drop.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.DropDatabase
import app.drop.data.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Time Machine: which versions are kept, how many, and that restoring can always be undone. */
@RunWith(RobolectricTestRunner::class)
class HistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: DropDatabase
    private var now = 1_000_000_000L
    private lateinit var history: NoteHistory

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
        history = NoteHistory(db) { now }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun diffsMatch010OnRecordedCases() {
        val lines = javaClass.classLoader!!.getResourceAsStream("diff-golden-0.10.0.jsonl")!!.bufferedReader().readLines()
        assertTrue(lines.size > 1500)
        for (line in lines) {
            val o = JSONObject(line)
            val got = LineDiff.of(o.getString("old"), o.getString("new"))
            if (o.isNull("diff")) {
                assertNull(got)
            } else {
                val want = o.getJSONArray("diff").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> DiffLine(DiffLine.Kind.valueOf(p.getString(0)), p.getString(1)) } } }
                assertEquals(want, got)
            }
        }
    }

    private fun note(title: String, body: String) = runBlocking {
        val n = Note(title = title, body = body, createdAt = now, updatedAt = now)
        n.copy(id = db.noteDao().insert(n))
    }

    private fun edit(note: Note, body: String): Note = runBlocking {
        val next = note.copy(body = body, updatedAt = now)
        history.beforeWrite(note, next)
        db.noteDao().update(next)
        next
    }

    private fun count(id: Long) = runBlocking { db.revisionDao().countFor(id) }

    @Test
    fun aPauseInWritingLeavesAVersionBehind() {
        var n = note("Plan", "one")
        n = edit(n, "two")
        assertEquals(1, count(n.id))
        // Typing on within ten minutes doesn't pile up versions.
        now += 60_000
        n = edit(n, "three")
        now += 5 * 60_000
        n = edit(n, "four")
        assertEquals(1, count(n.id))
        now += NoteHistory.INTERVAL_MS
        n = edit(n, "five")
        assertEquals(2, count(n.id))
        val latest = runBlocking { db.revisionDao().latest(n.id) }!!
        assertEquals("four", latest.body)
        // Saving without a change, or an empty note, keeps nothing.
        now += NoteHistory.INTERVAL_MS
        runBlocking { history.beforeWrite(n, n.copy(updatedAt = now)) }
        val empty = note("", "")
        edit(empty, "now some text")
        assertEquals(2, count(n.id))
        assertEquals(0, count(empty.id))
    }

    @Test
    fun versionsAreCappedPerNote() {
        var n = note("Long-lived", "v0")
        repeat(NoteHistory.KEEP_PER_NOTE + 10) { i ->
            now += NoteHistory.INTERVAL_MS
            n = edit(n, "v${i + 1}")
        }
        assertEquals(NoteHistory.KEEP_PER_NOTE, count(n.id))
        // The oldest go first.
        val bodies = runBlocking { history.observe(n.id).first() }.map { runBlocking { history.get(n.id, it.id) }!!.body }
        assertEquals("v${NoteHistory.KEEP_PER_NOTE + 9}", bodies.first())
        assertEquals("v10", bodies.last())
    }

    @Test
    fun restoringKeepsTheCurrentTextAsAVersionFirst() {
        var n = note("Recipe", "flour, sugar")
        n = edit(n, "flour, sugar, lemons")
        val old = runBlocking { history.observe(n.id).first() }.single()
        now += 1_000
        val restored = runBlocking { history.restore(n.id, old.id) }!!
        assertEquals("flour, sugar", restored.body)
        assertEquals("flour, sugar", runBlocking { db.noteDao().getById(n.id) }!!.body)
        // The text it replaced is in History, even though less than ten minutes passed.
        assertEquals("flour, sugar, lemons", runBlocking { db.revisionDao().latest(n.id) }!!.body)

        val other = note("Other", "x")
        assertNull(runBlocking { history.restore(other.id, old.id) })
        runBlocking { db.noteDao().setDeletedAt(n.id, now) }
        assertNull(runBlocking { history.restore(n.id, old.id) })
    }
}
