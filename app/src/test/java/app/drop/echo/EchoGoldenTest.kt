package app.drop.echo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * ECHO must suggest exactly what 0.10.0 suggested. The cases were recorded by running 0.10.0's
 * own ECHO engine on 150 random libraries (keywords, related notes with scores and reasons,
 * and rediscoveries).
 */
@RunWith(RobolectricTestRunner::class)
class EchoGoldenTest {
    private val cases: List<JSONObject> by lazy {
        javaClass.classLoader!!.getResourceAsStream("echo-golden-0.10.0.jsonl")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { JSONObject(it) }
    }
    private lateinit var locale: Locale

    @Before
    fun setUp() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() = Locale.setDefault(locale)

    private fun kw(a: JSONArray): Map<String, String> = (0 until a.length()).associate { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }

    private fun longs(a: JSONArray) = (0 until a.length()).map { a.getLong(it) }

    private fun reason(o: JSONObject): EchoReason = when (o.getString("t")) {
        "words" -> EchoReason.SharedWords((0 until o.getJSONArray("w").length()).map { o.getJSONArray("w").getString(it) }, o.getBoolean("in"))
        "project" -> EchoReason.SameProject(o.getString("v"))
        "canvas" -> EchoReason.CanvasConnection(o.getString("v"))
        else -> EchoReason.WikiLink
    }

    private fun suggestion(o: JSONObject) = EchoSuggestion(
        o.getLong("id"),
        o.getDouble("score"),
        (0 until o.getJSONArray("reasons").length()).map { reason(o.getJSONArray("reasons").getJSONObject(it)) },
        (0 until o.getJSONArray("points").length()).map { o.getJSONArray("points").getDouble(it) },
    )

    private fun setOf(text: String): Set<Long> = text.trim('[', ']').split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    @Test
    fun echoSuggestsExactlyWhat010Suggested() {
        assertEquals(150, cases.size)
        var compared = 0
        cases.forEachIndexed { c, case ->
            val docsJson = case.getJSONArray("docs")
            val docs = (0 until docsJson.length()).map { i ->
                val d = docsJson.getJSONObject(i)
                val doc = EchoDoc(d.getLong("id"), d.getString("title"), d.getString("body"), d.getLong("updated"), d.getBoolean("task"))
                assertEquals("case $c doc $i title keywords", kw(d.getJSONArray("kt")), EchoText.keywords(doc.title))
                assertEquals("case $c doc $i body keywords", kw(d.getJSONArray("kb")), EchoText.keywords(doc.body))
                doc
            }
            val projects = LinkedHashMap<Long, LinkedHashMap<Long, String>>()
            case.getJSONArray("projects").let { a ->
                for (i in 0 until a.length()) a.getJSONArray(i).let { projects.getOrPut(it.getLong(0)) { LinkedHashMap() }[it.getLong(1)] = it.getString(2) }
            }
            val canvas = LinkedHashMap<Long, LinkedHashMap<Long, String>>()
            case.getJSONArray("canvas").let { a ->
                for (i in 0 until a.length()) a.getJSONArray(i).let {
                    val (x, y, name) = Triple(it.getLong(0), it.getLong(1), it.getString(2))
                    canvas.getOrPut(x) { LinkedHashMap() }[y] = name
                    canvas.getOrPut(y) { LinkedHashMap() }[x] = name
                }
            }
            val links = LinkedHashMap<Long, HashSet<Long>>()
            case.getJSONArray("links").let { a ->
                for (i in 0 until a.length()) longs(a.getJSONArray(i)).let { (x, y) ->
                    links.getOrPut(x) { HashSet() }.add(y)
                    links.getOrPut(y) { HashSet() }.add(x)
                }
            }
            val index = EchoIndex(docs, EchoRelations(projects, canvas, links), EchoWeights())
            val related = case.getJSONArray("related")
            for (i in 0 until related.length()) {
                val r = related.getJSONObject(i)
                val expected = r.getJSONArray("out").let { a -> (0 until a.length()).map { suggestion(a.getJSONObject(it)) } }
                assertEquals("case $c related to ${r.getLong("src")}", expected, index.related(r.getLong("src"), 5, setOf(r.get("ex").toString())))
                compared += expected.size
            }
            val expectedRd = case.getJSONArray("rediscover").let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { o -> Rediscovery(o.getLong("seed"), suggestion(o.getJSONObject("s"))) } }
            }
            assertEquals("case $c rediscover", expectedRd, index.rediscover(case.getLong("now"), case.getLong("stale"), 3, setOf(case.get("sup").toString()), 5))
            case.getJSONArray("pct").let {
                assertEquals(it.getInt(0), EchoStrength.percent(5.5, EchoWeights()))
                assertEquals(it.getInt(1), EchoStrength.percent(25.0, EchoWeights()))
            }
        }
        assertTrue(compared > 2000)
    }
}
