package app.drop.richtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Notes written by DROP 0.10.0 must read, edit and save exactly as they did there. The cases in
 * richtext-golden-0.10.0.jsonl were recorded by running 0.10.0's own rich-text code on thousands
 * of random documents; the rebuilt code has to reproduce every one.
 */
@RunWith(RobolectricTestRunner::class)
class RichTextGoldenTest {
    private val cases: List<JSONObject> by lazy {
        javaClass.classLoader!!.getResourceAsStream("richtext-golden-0.10.0.jsonl")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { JSONObject(it) }
    }

    private fun doc(o: JSONObject): RichDoc {
        val spans = o.getJSONArray("spans").let { a ->
            (0 until a.length()).map { i ->
                val s = a.getJSONArray(i)
                Span(s.getInt(0), s.getInt(1), InlineStyle.valueOf(s.getString(2)), if (s.isNull(3)) null else s.getString(3))
            }
        }
        val blocks = o.getJSONArray("blocks").let { a ->
            (0 until a.length()).map { i -> a.getJSONArray(i).let { Block(BlockType.valueOf(it.getString(0)), it.getBoolean(1)) } }
        }
        return RichDoc(o.getString("text"), spans, blocks)
    }

    private fun check(expected: JSONObject, actual: RichDoc, what: String) {
        val e = doc(expected)
        assertEquals("$what text", e.text, actual.text)
        assertEquals("$what spans", e.spans, actual.spans)
        assertEquals("$what blocks", e.blocks, actual.blocks)
    }

    @Test
    fun serializingMatches010() {
        val serialize = cases.filter { it.getString("op") == "serialize" }
        assertTrue(serialize.size >= 1000)
        serialize.forEachIndexed { i, c ->
            val input = doc(c.getJSONObject("in"))
            check(c.getJSONObject("normalized"), input.normalized(), "case $i normalized")
            assertEquals("case $i markdown", c.getString("out"), DropMarkdown.serialize(input))
            check(c.getJSONObject("back"), DropMarkdown.parse(c.getString("out")), "case $i read back")
        }
    }

    @Test
    fun parsingMatches010() {
        val parse = cases.filter { it.getString("op") == "parse" }
        assertTrue(parse.size >= 1000)
        parse.forEachIndexed { i, c ->
            check(c.getJSONObject("out"), DropMarkdown.parse(c.getString("md"), c.getBoolean("lenient")), "case $i")
        }
    }

    @Test
    fun editingMatches010() {
        val edits = cases.filter { it.getString("op") == "edit" }
        assertTrue(edits.size >= 1000)
        edits.forEachIndexed { i, c ->
            val d = doc(c.getJSONObject("in"))
            val out = c.getJSONObject("out")
            val what = "edit $i kind ${c.getInt("kind")}"
            when (c.getInt("kind")) {
                0 -> {
                    val styles = if (c.isNull("styles")) null else c.getJSONArray("styles").let { a -> (0 until a.length()).map { InlineStyle.valueOf(a.getString(it)) }.toSet() }
                    val r = RichEdit.apply(d, c.getString("newText"), c.getInt("sel"), c.getInt("sel"), styles)
                    check(out, r.doc, what)
                    assertEquals(what, c.getInt("selStart"), r.selectionStart)
                    assertEquals(what, c.getInt("selEnd"), r.selectionEnd)
                }
                1 -> check(out, RichEdit.toggleInline(d, c.getInt("start"), c.getInt("end"), InlineStyle.valueOf(c.getString("style"))), what)
                2 -> {
                    val r = RichEdit.applyBlock(d, c.getInt("start"), c.getInt("end"), BlockType.valueOf(c.getString("block")))
                    check(out, r.doc, what)
                    assertEquals(what, c.getInt("selStart"), r.selectionStart)
                    assertEquals(what, c.getInt("selEnd"), r.selectionEnd)
                }
                3 -> check(out, RichEdit.clearFormatting(d, c.getInt("start"), c.getInt("end")), what)
                4 -> check(out, RichEdit.setLink(d, c.getInt("start"), c.getInt("end"), c.getString("url")), what)
                5 -> check(out, RichEdit.toggleChecked(d, c.getInt("line")), what)
                else -> check(out, RichEdit.appendParagraphs(d, c.getString("plain"), "\n"), what)
            }
        }
    }
}
