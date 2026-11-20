package app.drop.ui.notes

import app.drop.data.AttachmentSummary
import app.drop.data.Note
import app.drop.data.NoteFormat
import app.drop.data.OcrEntry
import app.drop.search.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {
    private fun note(id: Long, title: String, body: String = "", task: Boolean = false, done: Long? = null, pinned: Boolean = false, format: Int = NoteFormat.PLAIN) =
        Note(id = id, title = title, body = body, createdAt = id, updatedAt = id, isTask = task, completedAt = done, pinned = pinned, format = format).withDerivedFields()

    private val notes = listOf(
        note(1, "İstanbul gezisi", pinned = true),
        note(2, "Market", task = true),
        note(3, "Eski görev", task = true, done = 50),
        note(4, "Okuma", body = "https://example.com/makale"),
        note(5, "Biçimli", body = "**kalın** yazı", format = NoteFormat.MARKDOWN),
    )

    private fun build(filter: HomeFilter = HomeFilter.All, query: String = "", ocr: List<OcrEntry> = emptyList(), aliases: Map<Long, String> = emptyMap()) =
        buildState(notes, filter, SearchQuery(query), SearchIndex(), ocr, listOf(AttachmentSummary(4, "image", null, null)), aliases)

    @Test
    fun allHidesCompletedTasksButTasksAndSearchShowThem() {
        val all = build()
        assertEquals(listOf(1L), all.pinned.map { it.id })
        assertEquals(listOf(2L, 4L, 5L), all.others.map { it.id })
        assertTrue(all.completed.isEmpty())
        assertEquals(5, all.totalCount)
        assertEquals(4, all.filterCount)

        val tasks = build(HomeFilter.Tasks)
        assertEquals(listOf(2L), tasks.others.map { it.id })
        assertEquals(listOf(3L), tasks.completed.map { it.id })

        assertEquals(listOf(3L), build(query = "eski").completed.map { it.id })
    }

    @Test
    fun filtersForLinksAndPinned() {
        assertEquals(listOf(4L), build(HomeFilter.Links).others.map { it.id })
        assertEquals(listOf(1L), build(HomeFilter.Pinned).pinned.map { it.id })
        assertEquals(1, build().attachments.getValue(4).size)
    }

    @Test
    fun searchIgnoresCaseAccentsAndFormattingAndUsesAliases() {
        assertEquals(listOf(1L), build(query = "istanbul").pinned.map { it.id })
        assertEquals(listOf(5L), build(query = "kalin").others.map { it.id })
        assertTrue(build(query = "**").isEmpty)
        assertEquals(listOf(2L), build(query = "bakkal", aliases = mapOf(2L to "Bakkal")).others.map { it.id })
    }

    @Test
    fun aNoteFoundOnlyInItsScanSaysWhere() {
        val state = build(query = "fatura", ocr = listOf(OcrEntry(attachmentId = 9, noteId = 4, text = "Elektrik  FATURASI\n2026")))
        assertEquals(listOf(4L), state.others.map { it.id })
        val hit = state.ocrHits.getValue(4)
        assertEquals(9L, hit.attachmentId)
        val r = hit.snippet.highlights.single()
        assertEquals("FATURA", hit.snippet.text.substring(r.first, r.last + 1))
    }
}
