package app.drop.links

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.NoteAlias
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [[Links]] resolve only when the target is clear, follow Trash, and never point at the wrong note. */
@RunWith(RobolectricTestRunner::class)
class LinkResolverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DropDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun add(title: String, body: String = "") = runBlocking { db.noteDao().insert(Note(title = title, body = body, createdAt = 1, updatedAt = 1)) }

    @Test
    fun linksResolveOnlyWhenOneNoteMatches() = runBlocking {
        val alpha = add("Alpha")
        add("Gamma")
        add("gamma")
        val sourceId = add("Source", "See [[ALPHA]], [[Gamma]] and [[Nope]].")
        val resolver = LinkResolver(db.noteDao(), db.linkDao())
        val source = db.noteDao().getById(sourceId)!!
        resolver.sync(source)
        assertEquals(listOf(alpha), db.linkDao().forSource(sourceId).map { it.targetId })

        val states = resolver.states(source)
        assertEquals(3, states.size)
        assertEquals(LinkState.Linked("ALPHA", alpha, "Alpha"), states[0])
        assertTrue(states[1] is LinkState.Ambiguous && (states[1] as LinkState.Ambiguous).candidates.size == 2)
        assertEquals(LinkState.Missing("Nope"), states[2])

        // The person picks one of the two Gammas; it sticks.
        val gamma = (states[1] as LinkState.Ambiguous).candidates.first().id
        resolver.choose(sourceId, "Gamma", gamma)
        assertEquals(LinkState.Linked("Gamma", gamma, db.noteDao().getById(gamma)!!.title), resolver.states(source)[1])

        db.noteDao().setDeletedAt(alpha, 5)
        assertEquals(LinkState.InTrash("ALPHA", alpha, "Alpha"), resolver.states(source)[0])
    }

    @Test
    fun removingTheTextRemovesTheLinkAndAliasesCount() = runBlocking {
        val target = add("Quarterly plan")
        val sourceId = add("Source", "[[Q3]]")
        val resolver = LinkResolver(db.noteDao(), db.linkDao(), aliases = { listOf(NoteAlias(target, "Q3", WikiLinks.key("Q3"))) })
        resolver.sync(db.noteDao().getById(sourceId)!!)
        assertEquals(listOf(target), db.linkDao().forSource(sourceId).map { it.targetId })

        db.noteDao().update(db.noteDao().getById(sourceId)!!.copy(body = "no links now"))
        resolver.sync(db.noteDao().getById(sourceId)!!)
        assertTrue(db.linkDao().forSource(sourceId).isEmpty())
    }

    @Test
    fun aNoteNeverLinksToItself() = runBlocking {
        val id = add("Self", "[[Self]]")
        LinkResolver(db.noteDao(), db.linkDao()).sync(db.noteDao().getById(id)!!)
        assertTrue(db.linkDao().forSource(id).isEmpty())
    }
}
