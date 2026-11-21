package app.drop.ui.spaces

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.CardColor
import app.drop.data.CardShape
import app.drop.data.DropDatabase
import app.drop.data.IdeaCanvas
import app.drop.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import kotlin.math.abs

/** Idea Canvas: the viewport maths match 0.10.0 exactly, cards never land on each other, notes are never deleted. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CanvasTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DropDatabase
    private val main = Executors.newSingleThreadExecutor()

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

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun f(a: JSONArray, i: Int) = a.getDouble(i).toFloat()

    @Test
    fun viewportMathsMatch010() {
        val cases = javaClass.classLoader!!.getResourceAsStream("canvas-viewport-golden-0.10.0.jsonl")!!.bufferedReader().readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
        assertEquals(2000, cases.size)
        cases.forEachIndexed { i, c ->
            val v = c.getJSONArray("v").let { Viewport(f(it, 0), f(it, 1), f(it, 2)) }
            val d = c.getDouble("d").toFloat()
            val zf = c.getJSONArray("zf")
            val z = v.zoomBy(f(zf, 0), f(zf, 1), f(zf, 2))
            c.getJSONArray("z").let { assertEquals("case $i zoom", Viewport(f(it, 0), f(it, 1), f(it, 2)), z) }
            val w = c.getJSONArray("w")
            assertEquals("case $i visible", c.getBoolean("vis"), v.isVisible(f(w, 0), f(w, 1), f(w, 2), f(w, 3), d, 48 * d))
            assertEquals("case $i grid", c.getDouble("grid").toFloat(), v.gridStep(d))
            assertEquals("case $i grid start", c.getDouble("gstart").toFloat(), v.gridStart(v.panX, v.gridStep(d)))
            assertEquals("case $i toScreen", c.getDouble("sx").toFloat(), v.toScreenX(f(w, 0), d))
            assertEquals("case $i toWorld", c.getDouble("wxb").toFloat(), v.toWorldX(f(zf, 1), d))
            val pos = c.getJSONArray("pos").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> f(p, 0) to f(p, 1) } } }
            val rect = WorldRect.ofCards(pos)
            if (c.isNull("fit")) {
                assertEquals(null, rect)
            } else {
                val fit = Viewport.fit(rect!!, f(w, 2), f(w, 3), d, 32 * d)
                c.getJSONArray("fit").let { assertEquals("case $i fit", Viewport(f(it, 0), f(it, 1), f(it, 2)), fit) }
            }
        }
    }

    @Test
    fun addedCardsNeverOverlapAndEveryNoteIsPlaced() = runBlocking {
        val canvasId = db.canvasDao().insert(IdeaCanvas(name = "Plan", createdAt = 1, updatedAt = 1))
        val ids = (1..10).map { db.noteDao().insert(Note(title = "N$it", body = "", createdAt = 1, updatedAt = 1)) }
        lateinit var vm: CanvasViewModel
        main.submit { vm = CanvasViewModel(SavedStateHandle(mapOf(CanvasViewModel.ARG_ID to canvasId)), db.canvasDao(), db.noteDao()) }.get()
        main.submit { vm.add(ids.take(6)) }.get()
        waitFor("first cards") { runBlocking { db.canvasDao().cardsOf(canvasId).size } == 6 }
        main.submit { vm.add(ids.drop(6)) }.get()
        waitFor("more cards") { runBlocking { db.canvasDao().cardsOf(canvasId).size } == 10 }
        val cards = db.canvasDao().cardsOf(canvasId)
        assertEquals(ids.toSet(), cards.map { it.noteId }.toSet())
        for (a in cards) for (b in cards) if (a.id != b.id) {
            assertTrue("cards overlap", abs(a.x - b.x) >= CanvasGeometry.CARD_W || abs(a.y - b.y) >= CanvasGeometry.CARD_H)
        }
    }

    @Test
    fun connectionsStyleTextBlocksAndRemovalKeepNotes() = runBlocking {
        val canvasId = db.canvasDao().insert(IdeaCanvas(name = "Plan", createdAt = 1, updatedAt = 1))
        val a = db.noteDao().insert(Note(title = "A", body = "", createdAt = 1, updatedAt = 1))
        val b = db.noteDao().insert(Note(title = "B", body = "", createdAt = 1, updatedAt = 1))
        lateinit var vm: CanvasViewModel
        main.submit { vm = CanvasViewModel(SavedStateHandle(mapOf(CanvasViewModel.ARG_ID to canvasId)), db.canvasDao(), db.noteDao(), clock = { 99 }) }.get()
        main.submit { vm.add(listOf(a, b)) }.get()
        waitFor("cards") { runBlocking { db.canvasDao().cardsOf(canvasId).size } == 2 }
        val (ca, cb) = db.canvasDao().cardsOf(canvasId).sortedBy { it.noteId }
        main.submit { vm.connect(cb.id, ca.id) }.get()
        waitFor("edge") { runBlocking { db.canvasDao().observeEdges(canvasId).first() }.isNotEmpty() }
        val edge = db.canvasDao().observeEdges(canvasId).first().single()
        assertEquals(minOf(ca.id, cb.id), edge.fromCard)

        main.submit { vm.style(ca.id, CardShape.Pill, CardColor.Rose) }.get()
        waitFor("style") { runBlocking { db.canvasDao().cardsOf(canvasId) }.first { it.id == ca.id }.shape == CardShape.Pill.id }
        assertEquals(CardColor.Rose.id, db.canvasDao().cardsOf(canvasId).first { it.id == ca.id }.color)

        main.submit { vm.addTextBlock("  Big idea\nwith details  ", 10f, 20f) }.get()
        waitFor("text block") { runBlocking { db.canvasDao().cardsOf(canvasId).size } == 3 }
        val note = db.noteDao().allNotes().first { it.title == "Big idea" }
        assertEquals("with details", note.body)
        val sticky = db.canvasDao().cardsOf(canvasId).first { it.noteId == note.id }
        assertEquals(CardShape.Sticky.id, sticky.shape)
        assertEquals(CardColor.Amber.id, sticky.color)

        main.submit { vm.disconnect(ca.id) }.get()
        waitFor("disconnect") { runBlocking { db.canvasDao().observeEdges(canvasId).first() }.isEmpty() }
        main.submit { vm.remove(ca.id) }.get()
        waitFor("remove") { runBlocking { db.canvasDao().cardsOf(canvasId).size } == 2 }
        assertNotNull(db.noteDao().getById(a))

        var deleted = false
        main.submit { vm.delete { deleted = true } }.get()
        waitFor("delete") { deleted }
        assertTrue(db.canvasDao().all().isEmpty())
        assertEquals(3, db.noteDao().allNotes().size)
        Unit
    }

    @Test
    fun theLastViewIsRememberedPerCanvasWhateverTheScreenDensity() {
        val store = CanvasViewportStore(context)
        val v = Viewport(-300f, 150f, 1.5f)
        store.save(7, v, density = 2f)
        val back = store.load(7, density = 2f)!!
        assertEquals(v.scale, back.scale)
        assertEquals(v.toWorldX(0f, 2f), back.toWorldX(0f, 2f), 0.001f)
        // On a denser screen the same world point is at the top-left corner.
        val dense = store.load(7, density = 3f)!!
        assertEquals(v.toWorldX(0f, 2f), dense.toWorldX(0f, 3f), 0.001f)
        store.forget(7)
        assertEquals(null, store.load(7, 2f))
        assertEquals(null, store.load(8, 2f))
    }
}
