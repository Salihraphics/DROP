package app.drop.templates

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.NoteFormat
import app.drop.richtext.DropMarkdown
import app.drop.ui.templates.TemplateEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.Executors

/** Templates make the same notes as in 0.10.0, never crash on bad input and keep the user's own templates safe. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TemplatesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DropDatabase
    private val main = Executors.newSingleThreadExecutor()
    private val now = ZonedDateTime.of(2026, 10, 24, 14, 5, 0, 0, ZoneId.of("Europe/Istanbul"))

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

    private fun onMain(block: () -> Unit) = main.submit(block).get()

    @Test
    fun placeholdersExpandExactlyAs010() {
        val cases = javaClass.classLoader!!.getResourceAsStream("templates-golden-0.10.0.jsonl")!!.bufferedReader(Charsets.UTF_8)
            .readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
        assertTrue(cases.size >= 400)
        cases.forEachIndexed { i, c ->
            val t = Template("x", c.getString("name"), c.getString("title"), c.getString("body"), c.getInt("format"), isTask = false)
            val made = Placeholders.tryInstantiate(t, now, Locale.forLanguageTag(c.getString("locale")))
            if (c.isNull("out")) {
                assertEquals("case $i", null, made)
            } else {
                val out = c.getJSONObject("out")
                assertNotNull("case $i", made)
                assertEquals("case $i title", out.getString("title"), made!!.first)
                assertEquals("case $i body", out.getString("body"), made.second)
                assertEquals("case $i format", out.getInt("format"), made.third)
            }
        }
    }

    @Test
    fun everyBuiltInTemplateWorksInEnglishAndTurkish() {
        for (tag in listOf("en", "tr")) {
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val res = context.createConfigurationContext(config).resources
            val all = BuiltInTemplates.all(res)
            assertEquals(6, all.size)
            assertEquals(6, all.map { it.key }.toSet().size)
            for (t in all) {
                assertTrue(t.isBuiltIn)
                val made = Placeholders.tryInstantiate(t, now, Locale.forLanguageTag(tag))
                assertNotNull("$tag ${t.key}", made)
                val (title, body, format) = made!!
                assertFalse("$tag ${t.key} title still has a placeholder: $title", title.contains("{{"))
                assertFalse("$tag ${t.key} body still has a placeholder", body.contains("{{"))
                assertEquals(NoteFormat.MARKDOWN, format)
                assertTrue(DropMarkdown.plainOf(body, format).isNotBlank())
            }
        }
    }

    @Test
    fun unknownPlaceholdersAndBracesAreLeftAlone() {
        val values = Placeholders.values(now, Locale.ENGLISH)
        assertEquals("{{weather}} {x} }} {{", Placeholders.expandTitle("{{weather}} {x} }} {{", values))
        assertEquals("a b", Placeholders.expandTitle("a\nb", values))
        assertEquals("Oct 24, 2026 " + values["time"], Placeholders.expandTitle("{{ DATE }} {{time}}", values))
        assertTrue(values["time"]!!.startsWith("2:05"))
    }

    @Test
    fun usingATemplateCreatesOneNoteWithTheFilledInText() = runBlocking {
        val store = TemplateStore(db) { 42L }
        val t = Template("custom:1", "Weekly", "Week of {{date}}", "**{{title}}**\n- [ ] plan", NoteFormat.MARKDOWN, isTask = true, customId = 1)
        val note = store.createNote(t, now, Locale.ENGLISH)
        val stored = db.noteDao().getById(note.id)!!
        assertEquals("Week of Oct 24, 2026", stored.title)
        assertEquals(DropMarkdown.store(DropMarkdown.docOf("**Week of Oct 24, 2026**\n- [ ] plan", NoteFormat.MARKDOWN)).first, stored.body)
        assertTrue(stored.isTask)
        assertEquals(42L, stored.createdAt)
        assertEquals(1, db.noteDao().allNotes().size)
    }

    @Test
    fun ownTemplatesAreSavedUpdatedDuplicatedAndDeleted() = runBlocking {
        var clock = 10L
        val store = TemplateStore(db) { clock }
        val id = store.save(null, "  Standup\n", "Standup {{date}}\n", DropMarkdown.docOf("- yesterday", NoteFormat.MARKDOWN), isTask = false)
        var saved = store.get(id)!!
        assertEquals("Standup", saved.name)
        assertEquals("Standup {{date}}", saved.title)
        assertEquals(10L, saved.createdAt)

        clock = 20L
        assertEquals(id, store.save(id, "N".repeat(80), "T", DropMarkdown.docOf("x", NoteFormat.PLAIN), isTask = true))
        saved = store.get(id)!!
        assertEquals(TemplateStore.MAX_NAME, saved.name.length)
        assertTrue(saved.isTask)
        assertEquals(10L, saved.createdAt)
        assertEquals(20L, saved.updatedAt)

        val builtIn = BuiltInTemplates.all(context.resources).first()
        val copy = store.duplicate(builtIn, "Copy of ${builtIn.name}")
        assertEquals(builtIn.body, store.get(copy)!!.body)
        assertEquals(2, store.observeCustom().first().size)

        store.delete(id)
        assertEquals(listOf(copy), store.observeCustom().first().map { it.id })
    }

    @Test
    fun aDamagedTemplateDoesNotCrash() {
        val broken = Template("custom:9", "Broken", "{{date", "\u0000[[" + "*".repeat(5000), 7, isTask = false, customId = 9)
        // Either it works or it is reported as unreadable: it never throws.
        Placeholders.tryInstantiate(broken, now, Locale.ENGLISH)
    }

    @Test
    fun theEditorNeedsANameAndKeepsADraftAcrossProcessDeath() {
        val store = TemplateStore(db)
        val handle = SavedStateHandle(mapOf(TemplateEditorViewModel.ARG_ID to -1L))
        lateinit var vm: TemplateEditorViewModel
        onMain { vm = TemplateEditorViewModel(handle, store, db.noteDao()) }
        waitFor("load") { vm.loaded }
        assertTrue(vm.isNew)
        onMain {
            vm.onTitleChange("Retro")
            vm.save()
        }
        assertTrue(vm.nameError)
        assertFalse(vm.saved)

        onMain { vm.onNameChange("Retro\nnotes") }
        assertFalse(vm.nameError)
        assertEquals("Retro notes", vm.name)

        // The system ends the process: a new screen gets the draft back from the saved state.
        lateinit var again: TemplateEditorViewModel
        onMain { again = TemplateEditorViewModel(handle, store, db.noteDao()) }
        waitFor("reload") { again.loaded }
        assertEquals("Retro notes", again.name)
        assertEquals("Retro", again.title)
        assertTrue(again.dirty)
        onMain { again.save() }
        waitFor("save") { again.saved }
        assertEquals(listOf("Retro notes"), runBlocking { store.observeCustom().first().map { it.name } })
    }

    @Test
    fun aTemplateMadeFromANoteStartsWithItsText() {
        val noteId = runBlocking {
            db.noteDao().insert(Note(title = "Sprint review", body = "## Demo\n- item", format = NoteFormat.MARKDOWN, isTask = true, createdAt = 1, updatedAt = 1))
        }
        val handle = SavedStateHandle(mapOf(TemplateEditorViewModel.ARG_ID to -1L, TemplateEditorViewModel.ARG_FROM_NOTE to noteId))
        lateinit var vm: TemplateEditorViewModel
        onMain { vm = TemplateEditorViewModel(handle, TemplateStore(db), db.noteDao()) }
        waitFor("load") { vm.loaded }
        assertEquals("Sprint review", vm.name)
        assertEquals("Sprint review", vm.title)
        assertTrue(vm.isTask)
        assertTrue(vm.dirty)
        assertEquals("## Demo\n- item", DropMarkdown.store(vm.editor.doc).first)
    }
}
