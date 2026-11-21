package app.drop.palette

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** The command palette finds and orders commands exactly as 0.10.0 did (1,200 recorded queries, English and Turkish). */
@RunWith(RobolectricTestRunner::class)
class PaletteGoldenTest {
    private val labelsEn = listOf("New note", "New task", "Voice Dump", "Paper Capture", "Search", "All notes", "Tasks", "Links", "Pinned", "Resurface", "Projects", "Daily Log", "Idea Canvas", "Trash", "Settings", "Vault", "New from template", "Templates", "Smart Collections", "Knowledge Health", "Import & Export")
    private val labelsTr = listOf("Yeni not", "Yeni görev", "Sesli Not", "Kâğıt Tarama", "Ara", "Tüm notlar", "Görevler", "Bağlantılar", "Sabitlenenler", "Hatırlatmalar", "Projeler", "Günlük", "Fikir Tuvali", "Çöp Kutusu", "Ayarlar", "Kasa", "Şablondan yeni", "Şablonlar", "Akıllı Koleksiyonlar", "Bilgi Sağlığı", "İçe ve Dışa Aktar")

    private fun commands(lang: Int): List<Command> {
        val labels = if (lang == 0) labelsEn else labelsTr
        return CommandId.entries.mapIndexed { i, id ->
            val kw = ENGLISH_KEYWORDS.getValue(id).toMutableList()
            if (lang == 1) kw.add(labels[i].lowercase(Locale.forLanguageTag("tr")))
            Command(id, labels[i], kw)
        }
    }

    @Test
    fun rankingMatches010() {
        val cases = javaClass.classLoader!!.getResourceAsStream("palette-golden-0.10.0.jsonl")!!.bufferedReader(Charsets.UTF_8)
            .readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
        assertEquals(1200, cases.size)
        var found = 0
        cases.forEachIndexed { i, c ->
            val expected = c.getJSONArray("out").let { a -> (0 until a.length()).map { a.getString(it) } }
            val actual = filterCommands(commands(c.getInt("lang")), c.getString("q")).map { it.id.name }
            assertEquals("case $i \"${c.getString("q")}\"", expected, actual)
            found += expected.size
        }
        assertTrue(found > 400)
        assertEquals(CommandId.entries.size, filterCommands(commands(0), "   ").size)
    }
}
