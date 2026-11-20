package app.drop.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun turkishDottedAndDotlessIMatchEachOther() {
        val q = SearchQuery("istanbul ışık")
        assertTrue(q.matches(FoldedText("İSTANBUL'da IŞIK")))
        assertTrue(q.matches(FoldedText("Istanbul"), FoldedText("Işık")))
        assertFalse(q.matches(FoldedText("Ankara")))
        assertEquals("cagri gul sule omer unal", FoldedText.fold("Çağrı Gül Şule Ömer Ünal"))
    }

    @Test
    fun highlightsPointAtTheOriginalCharacters() {
        val text = "Bugün İzmir'deyim"
        val ranges = SearchQuery("izmir").highlights(FoldedText(text))
        assertEquals(listOf(6..10), ranges)
        assertEquals("İzmir", text.substring(6, 11))
    }

    @Test
    fun snippetsStartNearADeepMatch() {
        val text = "a ".repeat(100) + "needle here"
        val s = snippet(text, SearchQuery("needle"))
        assertTrue(s.text.startsWith("…"))
        val r = s.highlights.single()
        assertEquals("needle", s.text.substring(r.first, r.last + 1))
        assertEquals(Snippet("short needle", listOf(6..11)), snippet("short needle", SearchQuery("needle")))
    }
}
