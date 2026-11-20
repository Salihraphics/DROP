package app.drop.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Backups hold everything outside Trash, restore it exactly, and never damage a library. */
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var source: DropDatabase
    private lateinit var target: DropDatabase

    private fun memory() = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        source = memory()
        target = memory()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private val turkish = "İstanbul'da ışık; Çağrı, Gül, Şule, Ömer, Ünal — ĞÜŞİÖÇ ğüşıöç ✓ 🎉"

    private fun export(db: DropDatabase): String {
        val out = ByteArrayOutputStream()
        runBlocking { BackupManager(db).export(out, exportedAt = 1_700_000_000_000, appVersion = "test") }
        return out.toString("UTF-8")
    }

    private fun restore(db: DropDatabase, json: String): ImportResult = runBlocking { BackupManager(db).import(json.byteInputStream(Charsets.UTF_8)) }

    /** A library using every kind of thing a backup holds. */
    private fun fill(db: DropDatabase) = runBlocking {
        val notes = db.noteDao()
        val a = notes.insert(Note(uuid = "note-a", title = turkish, body = "Body with [[Second]] and https://drop.app/x", createdAt = 100, updatedAt = 200, pinned = true).withDerivedFields())
        val b = notes.insert(Note(uuid = "note-b", title = "Second", body = "## Heading\n**bold** text", createdAt = 110, updatedAt = 210, format = NoteFormat.MARKDOWN))
        notes.insert(Note(uuid = "task-done", title = "Pay rent", body = "", createdAt = 120, updatedAt = 220, isTask = true, completedAt = 300))
        notes.insert(Note(uuid = "task-open", title = "Call Ayşe", body = "", createdAt = 130, updatedAt = 230, isTask = true))
        notes.insert(Note(uuid = "in-trash", title = "Gone", body = "", createdAt = 140, updatedAt = 240, deletedAt = 500))
        val p = db.projectDao().insert(Project(uuid = "proj", name = "Ev taşıma", description = "Taşınma planı", createdAt = 1, updatedAt = 2))
        db.projectDao().addItems(listOf(ProjectItem(p, a, 5), ProjectItem(p, b, 6)))
        db.linkDao().put(NoteLink(a, "second", b))
        db.dailyDao().put(DailyEntry("2026-11-20", "Güzel bir gün", 7))
        val c = db.canvasDao().insert(IdeaCanvas(uuid = "canvas", name = "Fikirler", createdAt = 1, updatedAt = 2))
        val k1 = db.canvasDao().addCard(CanvasCard(canvasId = c, noteId = a, x = 12.5f, y = -40f, shape = CardShape.Sticky.id, color = CardColor.Amber.id))
        val k2 = db.canvasDao().addCard(CanvasCard(canvasId = c, noteId = b, x = 300f, y = 80f))
        db.canvasDao().addEdge(CanvasEdge(k1, k2))
        db.metaDao().putAlias(NoteAlias(a, "Şehir", "sehir"))
        db.metaDao().putProperty(NoteProperty(b, "Durum", "durum", "text", "Devam ediyor"))
        db.templateDao().insert(NoteTemplate(uuid = "tpl", name = "Toplantı", title = "Toplantı {{date}}", body = "- Katılanlar", format = NoteFormat.MARKDOWN, createdAt = 1, updatedAt = 2))
        db.collectionDao().insert(SmartCollection(uuid = "col", name = "Açık görevler", rules = "{\"match\":\"all\",\"rules\":[]}", createdAt = 1, updatedAt = 2))
    }

    @Test
    fun aPopulatedLibraryExportsEverythingOutsideTrash() {
        fill(source)
        val json = export(source)
        assertTrue(json.length > 500)
        val root = JSONObject(json)
        assertEquals("drop-backup", root.getString("format"))
        assertEquals(3, root.getInt("version"))
        val items = root.getJSONArray("items")
        val uuids = (0 until items.length()).map { items.getJSONObject(it).getString("uuid") }.toSet()
        assertEquals(setOf("note-a", "note-b", "task-done", "task-open"), uuids)
        assertFalse(json.contains("in-trash"))
        assertTrue(json.contains(turkish))
    }

    @Test
    fun aRestoreIntoAnEmptyLibraryRecreatesEverything() = runBlocking {
        fill(source)
        val result = restore(target, export(source))
        assertEquals(ImportResult(added = 4, skipped = 0, spaces = 5), result) // project, day, canvas, template, collection

        val notes = target.noteDao().allNotes().associateBy { it.uuid }
        val a = notes.getValue("note-a")
        assertEquals(turkish, a.title)
        assertTrue(a.pinned)
        assertEquals("https://drop.app/x", a.url)
        assertEquals(NoteFormat.MARKDOWN, notes.getValue("note-b").format)
        assertEquals(300L, notes.getValue("task-done").completedAt)
        assertNull(notes.getValue("task-open").completedAt)
        assertTrue(notes.getValue("task-open").isTask)

        val project = target.projectDao().all().single()
        assertEquals("Ev taşıma", project.name)
        assertEquals(2, target.projectDao().allItems().size)
        assertEquals(listOf(NoteLink(a.id, "second", notes.getValue("note-b").id)), target.linkDao().all())
        assertEquals("Güzel bir gün", target.dailyDao().get("2026-11-20")!!.text)
        val cards = target.canvasDao().allCards().associateBy { it.noteId }
        assertEquals(12.5f, cards.getValue(a.id).x)
        assertEquals(CardShape.Sticky.id, cards.getValue(a.id).shape)
        assertEquals(CardColor.Amber.id, cards.getValue(a.id).color)
        assertEquals(1, target.canvasDao().allEdges().size)
        assertEquals("Şehir", target.metaDao().allAliases().single().alias)
        assertEquals("Devam ediyor", target.metaDao().allProperties().single().value)
        assertEquals("Toplantı {{date}}", target.templateDao().all().single().title)
        assertEquals("Açık görevler", target.collectionDao().all().single().name)
    }

    @Test
    fun aRestoreIsRepeatableAndNeverDuplicates() = runBlocking {
        fill(source)
        val json = export(source)
        restore(target, json)
        val again = restore(target, json)
        assertEquals(ImportResult(added = 0, skipped = 4, spaces = 0), again)
        assertEquals(4, target.noteDao().allNotes().size)
        assertEquals(1, target.projectDao().all().size)
        assertEquals(1, target.canvasDao().all().size)
        assertEquals(2, target.canvasDao().allCards().size)
        assertEquals(1, target.metaDao().allAliases().size)
    }

    @Test
    fun aRestoreLeavesNotesAlreadyInTheLibraryExactlyAsTheyAre() = runBlocking {
        fill(source)
        val json = export(source)
        // The person edited note-a on the new phone before restoring the old backup.
        target.noteDao().insert(Note(uuid = "note-a", title = "Edited here", body = "newer text", createdAt = 100, updatedAt = 999))
        val result = restore(target, json)
        assertEquals(3, result.added)
        assertEquals(1, result.skipped)
        val kept = target.noteDao().getByUuid("note-a")!!
        assertEquals("Edited here", kept.title)
        assertEquals("newer text", kept.body)
        // The backup's alias belonged to that note: it isn't forced onto the newer version.
        assertTrue(target.metaDao().allAliases().isEmpty())
    }

    @Test
    fun anEmptyLibraryExportsAValidBackupThatRestoresToNothing() = runBlocking {
        val json = export(source)
        val root = JSONObject(json)
        assertEquals(0, root.getJSONArray("items").length())
        assertEquals(3, root.getInt("version"))
        assertEquals(ImportResult(0, 0, 0), restore(target, json))
    }

    @Test
    fun aVersion1BackupRestores() = runBlocking {
        val json = """{"format":"drop-backup","version":1,"exportedAt":1,"items":[
            {"uuid":"v1-a","title":"Eski not","body":"çok eski","createdAt":1,"updatedAt":2,"pinned":true},
            {"uuid":"v1-b","title":"","body":"www.example.org","createdAt":3,"updatedAt":4}]}"""
        assertEquals(ImportResult(2, 0, 0), restore(target, json))
        val a = target.noteDao().getByUuid("v1-a")!!
        assertEquals("çok eski", a.body)
        assertTrue(a.pinned)
        assertEquals("www.example.org", target.noteDao().getByUuid("v1-b")!!.url)
    }

    @Test
    fun aVersion2BackupRestoresWithItsSpaces() = runBlocking {
        val json = """{"format":"drop-backup","version":2,"exportedAt":1,"app":{"name":"DROP","version":"0.5.0"},
            "items":[{"uuid":"x","title":"X","body":"","createdAt":1,"updatedAt":1,"task":true,"completedAt":9},
                     {"uuid":"y","title":"Y","body":"","createdAt":1,"updatedAt":1,"task":false,"completedAt":9}],
            "projects":[{"uuid":"p","name":"P","description":"","createdAt":1,"updatedAt":1,"items":["x","y","missing"]}],
            "links":[{"source":"x","label":"Y","target":"y"}],
            "dailyEntries":[{"date":"2026-01-02","text":"day","updatedAt":1},{"date":"2026-01-03","text":"  ","updatedAt":1}],
            "canvases":[{"uuid":"c","name":"C","createdAt":1,"updatedAt":1,"cards":[{"note":"x","x":1,"y":2},{"note":"y","x":3,"y":4}],
                         "edges":[{"from":"x","to":"y"}]}],
            "aliases":[{"note":"x","alias":"ignored before v3"}]}"""
        val result = restore(target, json)
        assertEquals(2, result.added)
        assertEquals(9L, target.noteDao().getByUuid("x")!!.completedAt)
        // Only tasks can be completed.
        assertNull(target.noteDao().getByUuid("y")!!.completedAt)
        assertEquals(2, target.projectDao().allItems().size)
        assertEquals("y", target.linkDao().all().single().label)
        assertEquals(listOf("2026-01-02"), target.dailyDao().all().map { it.date })
        assertTrue(target.canvasDao().allCards().all { it.shape == 0 && it.color == 0 })
        assertEquals(1, target.canvasDao().allEdges().size)
        assertTrue(target.metaDao().allAliases().isEmpty())
        assertEquals(NoteFormat.PLAIN, target.noteDao().getByUuid("x")!!.format)
    }

    @Test
    fun aVersion3BackupFrom090WithoutCardStylesRestores() = runBlocking {
        val json = """{"format":"drop-backup","version":3,"exportedAt":1,"items":[
            {"uuid":"n","title":"N","body":"**b**","createdAt":1,"updatedAt":1,"format":1}],
            "canvases":[{"uuid":"c","name":"C","createdAt":1,"updatedAt":1,"cards":[{"note":"n","x":0,"y":0,"shape":99,"color":-1}],"edges":[]}],
            "properties":[{"note":"n","name":"Bitiş","type":"date","value":"2026-12-01"},{"note":"n","name":"Bad","type":"number","value":"abc"}]}"""
        restore(target, json)
        val card = target.canvasDao().allCards().single()
        assertEquals(0, card.shape) // unknown values fall back to the default
        assertEquals(0, card.color)
        assertEquals(listOf("Bitiş"), target.metaDao().allProperties().map { it.name })
    }

    private fun assertRefused(json: String, expected: Class<out BackupError>) {
        if (runBlocking { target.noteDao().allNotes().isEmpty() }) fill(target)
        val before = runBlocking { target.noteDao().allNotes() }
        try {
            restore(target, json)
            fail("restore should have been refused")
        } catch (e: BackupError) {
            assertTrue("$e", expected.isInstance(e))
        }
        assertEquals(before, runBlocking { target.noteDao().allNotes() })
    }

    @Test
    fun filesThatAreNotDropBackupsAreRefusedWithoutChangingAnything() {
        assertRefused("", BackupError.NotADropBackup::class.java)
        assertRefused("not json at all", BackupError.NotADropBackup::class.java)
        assertRefused("[1,2]", BackupError.NotADropBackup::class.java)
        assertRefused("""{"format":"something-else","version":3,"items":[]}""", BackupError.NotADropBackup::class.java)
    }

    @Test
    fun brokenOrNewerBackupsAreRefusedWithoutChangingAnything() {
        assertRefused("""{"format":"drop-backup","version":4,"items":[]}""", BackupError.UnsupportedVersion::class.java)
        assertRefused("""{"format":"drop-backup","items":[]}""", BackupError.Malformed::class.java)
        assertRefused("""{"format":"drop-backup","version":3}""", BackupError.Malformed::class.java)
        // One bad item anywhere refuses the whole file: nothing is half-restored.
        assertRefused(
            """{"format":"drop-backup","version":3,"items":[{"uuid":"ok","title":"","body":"","createdAt":1,"updatedAt":1},
               {"title":"no uuid","body":"","createdAt":1,"updatedAt":1}]}""",
            BackupError.Malformed::class.java,
        )
        assertRefused(
            """{"format":"drop-backup","version":3,"items":[{"uuid":"ok","title":"","body":"","createdAt":-5,"updatedAt":1}]}""",
            BackupError.Malformed::class.java,
        )
        assertRefused(
            """{"format":"drop-backup","version":3,"items":[],"dailyEntries":[{"date":"2026-13-45","text":"x","updatedAt":1}]}""",
            BackupError.Malformed::class.java,
        )
    }

    @Test
    fun aFileOver32MegabytesIsRefusedBeforeItIsRead() {
        val huge = object : InputStream() {
            var left = BackupFormat.MAX_FILE_BYTES + 1
            override fun read(): Int = if (left-- > 0) ' '.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                b.fill(' '.code.toByte(), off, off + n)
                left -= n
                return n
            }
        }
        try {
            runBlocking { BackupManager(target).import(huge) }
            fail("too large")
        } catch (e: BackupError.TooLarge) {
            // expected
        }
    }

    @Test
    fun exportWritesUtf8AndDoesNotCloseTheCallersStream() = runBlocking {
        fill(source)
        var closed = false
        val out = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
            }
        }
        val count = BackupManager(source).export(out, 1, "test")
        assertEquals(4, count)
        assertFalse(closed)
        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())
        assertTrue(String(bytes, Charsets.UTF_8).contains(turkish))
        assertEquals(4, BackupFormat.decode(String(bytes, Charsets.UTF_8)).size)
        assertTrue(ByteArrayInputStream(bytes).read() == '{'.code)
    }
}
