package app.drop.experience

import app.drop.data.CaptureWhere
import app.drop.data.ExperienceStyle
import app.drop.data.Familiarity
import app.drop.data.Personalization
import app.drop.data.UseCase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Home's order, capture buttons, tips and the start screen follow 0.10.0's rules exactly (3,000 recorded cases). */
@RunWith(RobolectricTestRunner::class)
class ExperienceGoldenTest {
    @Test
    fun personalisationMatches010() {
        val cases = javaClass.classLoader!!.getResourceAsStream("experience-golden-0.10.0.jsonl")!!.bufferedReader(Charsets.UTF_8)
            .readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
        assertEquals(3000, cases.size)
        cases.forEachIndexed { i, c ->
            fun str(k: String) = if (c.isNull(k)) null else c.getString(k)
            val flags = c.getJSONArray("flags").let { a -> (0 until 8).map { if (a.isNull(it)) null else a.getBoolean(it) } }
            val p = Personalization(
                useCases = c.getJSONArray("uc").let { a -> (0 until a.length()).mapTo(LinkedHashSet()) { UseCase.valueOf(a.getString(it)) } },
                style = str("style")?.let(ExperienceStyle::valueOf),
                familiarity = str("fam")?.let(Familiarity::valueOf),
                where = str("where")?.let(CaptureWhere::valueOf),
                savesLinks = flags[0], paper = flags[1], dictation = flags[2], journal = flags[3],
                longWriting = flags[4], repeatNotes = flags[5], reminders = flags[6], privateNotes = flags[7],
            )
            val dismissed = c.getJSONArray("dismissed").let { a -> (0 until a.length()).mapTo(HashSet()) { a.getString(it) } }
            assertEquals("case $i sections", c.getString("sections"), dashboardSections(p).toString())
            assertEquals("case $i capture", c.getString("capture"), captureOrder(p).toString())
            assertEquals("case $i tips", c.getBoolean("tips"), showsTips(p))
            assertEquals("case $i tip", str("tip"), nextTip(p, dismissed)?.id)
            val va = c.getString("va")
            val vb = str("vb")
            assertEquals("case $i compare", if (c.isNull("cmp")) null else c.getInt("cmp"), compareVersions(va, vb ?: "0.10.0"))
            val screen = when (val s = startupScreen(c.getBoolean("onb"), vb, va)) {
                is StartupScreen.WhatsNew -> "whatsnew:" + s.notes.version
                StartupScreen.Onboarding -> "onboarding"
                StartupScreen.None -> "none"
            }
            assertEquals("case $i screen", c.getString("screen"), screen)
        }
    }
}
