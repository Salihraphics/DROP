package app.drop.share

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.DropDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Share to DROP: what becomes the title and body, and that one share makes one note. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: DropDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun parsingMatches010OnRecordedShares() {
        val lines = javaClass.classLoader!!.getResourceAsStream("share-golden-0.10.0.jsonl")!!.bufferedReader().readLines()
        assertTrue(lines.size > 250)
        lines.forEach { line ->
            val o = JSONObject(line)
            val text = if (o.isNull("text")) null else o.getString("text")
            val subject = if (o.isNull("subject")) null else o.getString("subject")
            val got = SharedContentParser.parse(text, subject)
            if (o.isNull("result")) {
                assertNull("($text, $subject)", got)
            } else {
                val r = o.getJSONObject("result")
                assertEquals(SharedContent(r.getString("title"), r.getString("body"), r.getBoolean("truncated")), got)
            }
        }
    }

    private fun share(text: String?, subject: String? = null, handle: SavedStateHandle = SavedStateHandle(mapOf(Intent.EXTRA_TEXT to text, Intent.EXTRA_SUBJECT to subject)), now: Long = 1_000_000, supported: Boolean = true) =
        ShareViewModel(handle, db.noteDao(), supported, clock = { now })

    private fun ShareViewModel.awaitSaved(): ShareState.Saved {
        val end = System.currentTimeMillis() + 10_000
        while (state !is ShareState.Saved) {
            check(state != ShareState.Failed) { "save failed" }
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(20)
        }
        return state as ShareState.Saved
    }

    @Test
    fun aShareIsSavedOnceEvenWhenRepeatedOrRecreated() {
        val first = share("Read this later https://example.com/a", subject = "Example article").awaitSaved()
        assertEquals("Example article", first.note.title)
        assertEquals(false, first.alreadySaved)
        // Sharing the same thing again a moment later keeps one note and says so.
        val again = share("Read this later https://example.com/a", subject = "Example article", now = 1_030_000).awaitSaved()
        assertTrue(again.alreadySaved)
        assertEquals(first.note.id, again.note.id)
        // After the window, it is a new note.
        val later = share("Read this later https://example.com/a", subject = "Example article", now = 1_100_000).awaitSaved()
        assertEquals(false, later.alreadySaved)
        // A recreated screen (rotation) finds its own note instead of saving again.
        val handle = SavedStateHandle(mapOf(Intent.EXTRA_TEXT to "Rotate me"))
        val a = share(null, handle = handle).awaitSaved()
        val b = share(null, handle = handle, now = 5_000_000).awaitSaved()
        assertEquals(a.note.id, b.note.id)
        assertEquals(false, b.alreadySaved)
        assertEquals(3, runBlocking { db.noteDao().allNotes() }.size)
    }

    @Test
    fun sharesWithoutTextSaveNothing() {
        assertEquals(ShareState.NothingToSave, share("   ").state)
        assertEquals(ShareState.NothingToSave, share("Some text", supported = false).state)
        assertTrue(runBlocking { db.noteDao().allNotes() }.isEmpty())
    }
}
