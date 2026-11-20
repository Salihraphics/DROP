package app.drop.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Libraries from every earlier version open with all their data. Each test builds a database
 * exactly as an older DROP left it, then lets Room migrate it and check it against schema 7:
 * Room refuses to open a migrated database whose tables differ from the current entities.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    private var opened: DropDatabase? = null

    @After
    fun tearDown() {
        opened?.close()
        context.deleteDatabase(name)
    }

    /** The table DROP 0.1 created. */
    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
            "`body` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
    )

    /** Creates a database at [version] by running version 1's SQL and the migrations up to it. */
    private fun createAt(version: Int, fill: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v1.forEach(db::execSQL)
                    DropDatabase.MIGRATIONS.filter { it.endVersion <= version }.forEach { it.migrate(db) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.use { fill(it) }
    }

    private fun open(): DropDatabase =
        Room.databaseBuilder(context, DropDatabase::class.java, name).addMigrations(*DropDatabase.MIGRATIONS)
            .allowMainThreadQueries().build().also { opened = it }

    private fun SupportSQLiteDatabase.insertNote(values: Map<String, Any?>): Long =
        insert("notes", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            values.forEach { (k, v) ->
                when (v) {
                    null -> putNull(k)
                    is String -> put(k, v)
                    is Long -> put(k, v)
                    is Int -> put(k, v)
                    is Boolean -> put(k, if (v) 1 else 0)
                    else -> error("unsupported $v")
                }
            }
        })

    @Test
    fun aVersion1LibraryOpensWithEveryNoteAndNewFieldsFilledIn() = runBlocking {
        createAt(1) { db ->
            db.insertNote(mapOf("title" to "Çağrı'nın listesi", "body" to "İstanbul ışıkları — see https://example.com/a.", "created_at" to 10L, "updated_at" to 20L))
            db.insertNote(mapOf("title" to "", "body" to "second", "created_at" to 30L, "updated_at" to 40L))
        }
        val notes = open().noteDao().allNotes().sortedBy { it.createdAt }
        assertEquals(2, notes.size)
        val first = notes[0]
        assertEquals("Çağrı'nın listesi", first.title)
        assertEquals("İstanbul ışıkları — see https://example.com/a.", first.body)
        assertEquals(10L, first.createdAt)
        assertEquals(20L, first.updatedAt)
        // 1→2 gives every note a uuid; 2→3 finds links already in the text.
        assertTrue(notes.all { Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(it.uuid) })
        assertEquals(2, notes.map { it.uuid }.toSet().size)
        assertEquals("https://example.com/a", first.url)
        assertEquals(false, first.pinned)
        assertEquals(false, first.isTask)
        assertEquals(NoteFormat.PLAIN, first.format)
    }

    @Test
    fun aVersion4LibraryKeepsAttachmentsAndReminders() = runBlocking {
        createAt(4) { db ->
            val id = db.insertNote(mapOf("uuid" to "u-1", "title" to "Scan", "body" to "", "created_at" to 1L, "updated_at" to 1L, "is_task" to 1, "completed_at" to null))
            db.execSQL("INSERT INTO attachments (uuid, note_id, kind, file_name, display_name, mime_type, size_bytes, created_at, ocr_status, ocr_text) VALUES ('a-1', $id, 'image', 'f.jpg', 'Photo', 'image/jpeg', 12, 5, 'done', 'Türkçe metin')")
            db.execSQL("INSERT INTO reminders (note_id, local_time, created_at) VALUES ($id, '2026-11-21T09:00', 3)")
        }
        val database = open()
        val note = database.noteDao().getByUuid("u-1")!!
        val attachment = database.attachmentDao().forNotes(listOf(note.id)).single()
        assertEquals("Türkçe metin", attachment.ocrText)
        assertEquals(OcrStatus.Done, attachment.status)
        assertEquals("2026-11-21T09:00", database.reminderDao().get(note.id)!!.localTime)
    }

    @Test
    fun aVersion6LibraryKeepsCanvasesAndGetsDefaultCardStyles() = runBlocking {
        createAt(6) { db ->
            val a = db.insertNote(mapOf("uuid" to "a", "title" to "A", "body" to "", "created_at" to 1L, "updated_at" to 1L))
            val b = db.insertNote(mapOf("uuid" to "b", "title" to "B", "body" to "**rich**", "created_at" to 1L, "updated_at" to 1L, "format" to 1))
            db.execSQL("INSERT INTO canvases (uuid, name, created_at, updated_at) VALUES ('c', 'Board', 1, 1)")
            db.execSQL("INSERT INTO canvas_cards (canvas_id, note_id, x, y) VALUES (1, $a, 10.5, -3)")
            db.execSQL("INSERT INTO canvas_cards (canvas_id, note_id, x, y) VALUES (1, $b, 0, 0)")
            db.execSQL("INSERT INTO canvas_edges (from_card, to_card) VALUES (1, 2)")
            db.execSQL("INSERT INTO templates (uuid, name, title, body, created_at, updated_at) VALUES ('t', 'Mine', 'T', 'B', 1, 1)")
            db.execSQL("INSERT INTO note_aliases (note_id, alias, `key`) VALUES ($a, 'Alpha', 'alpha')")
        }
        val database = open()
        val cards = database.canvasDao().allCards().sortedBy { it.id }
        assertEquals(listOf(10.5f, 0f), cards.map { it.x })
        assertTrue(cards.all { it.shape == 0 && it.color == 0 })
        assertEquals(listOf(CanvasEdge(1, 2)), database.canvasDao().allEdges())
        assertEquals(NoteFormat.MARKDOWN, database.noteDao().getByUuid("b")!!.format)
        assertEquals("Mine", database.templateDao().all().single().name)
        assertEquals("Alpha", database.metaDao().allAliases().single().alias)
    }

    @Test
    fun theSchemaIsExactlyTheOneDrop0100Shipped() {
        // Room's identity hash covers every table, column, default, index and foreign key.
        val schema = File("schemas/app.drop.data.DropDatabase/7.json").readText()
        assertTrue(schema.contains("\"identityHash\": \"a7697d0792b88e13bc8fb2ea7c5539f4\""))
        assertNotNull(open().openHelper.writableDatabase)
    }
}
