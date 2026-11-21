package app.drop.experience

import app.drop.R
import app.drop.data.CaptureWhere
import app.drop.data.ExperienceStyle
import app.drop.data.Familiarity
import app.drop.data.Personalization
import app.drop.data.UseCase

/** The quick ways to capture on Home, in the order a person's answers make most useful. */
enum class Capture { Note, Task, Link, Voice, Scan }

/** The sections of Home below the tasks, which personalisation can reorder (never hide). */
enum class DashboardSection { Echo, Daily, Recent, Projects }

/** A short hint on Home; dismissed tips never come back. */
data class Tip(val id: String, val text: Int)

data class Highlight(val title: Int, val body: Int)

data class ReleaseNotes(val version: String, val headline: Int, val highlights: List<Highlight>)

/** What to show when the app starts: the introduction, What's new, or nothing. */
sealed interface StartupScreen {
    data object Onboarding : StartupScreen
    data class WhatsNew(val notes: ReleaseNotes) : StartupScreen
    data object None : StartupScreen
}

object ReleaseNotesCatalog {
    val all: List<ReleaseNotes> = listOf(
        ReleaseNotes(
            "0.10.0", R.string.whatsnew_0100_headline,
            listOf(
                Highlight(R.string.whatsnew_0100_fixes_title, R.string.whatsnew_0100_fixes_body),
                Highlight(R.string.whatsnew_0100_tasks_title, R.string.whatsnew_0100_tasks_body),
                Highlight(R.string.whatsnew_0100_echo_title, R.string.whatsnew_0100_echo_body),
                Highlight(R.string.whatsnew_0100_canvas_title, R.string.whatsnew_0100_canvas_body),
                Highlight(R.string.whatsnew_0100_personal_title, R.string.whatsnew_0100_personal_body),
            ),
        ),
        ReleaseNotes(
            "0.9.0", R.string.whatsnew_090_headline,
            listOf(
                Highlight(R.string.whatsnew_090_write_title, R.string.whatsnew_090_write_body),
                Highlight(R.string.whatsnew_090_history_title, R.string.whatsnew_090_history_body),
                Highlight(R.string.whatsnew_090_templates_title, R.string.whatsnew_090_templates_body),
                Highlight(R.string.whatsnew_090_organize_title, R.string.whatsnew_090_organize_body),
                Highlight(R.string.whatsnew_090_portable_title, R.string.whatsnew_090_portable_body),
            ),
        ),
        ReleaseNotes(
            "0.8.0", R.string.whatsnew_080_headline,
            listOf(
                Highlight(R.string.whatsnew_080_echo_title, R.string.whatsnew_080_echo_body),
                Highlight(R.string.whatsnew_080_revisit_title, R.string.whatsnew_080_revisit_body),
                Highlight(R.string.whatsnew_080_personal_title, R.string.whatsnew_080_personal_body),
                Highlight(R.string.whatsnew_080_privacy_title, R.string.whatsnew_080_privacy_body),
            ),
        ),
    )

    fun forVersion(version: String): ReleaseNotes? = all.firstOrNull { compareVersions(it.version, version) == 0 }
}

private val TIPS: List<Pair<UseCase, Tip>> = listOf(
    UseCase.Tasks to Tip("tasks-resurface", R.string.tip_tasks),
    UseCase.Research to Tip("links-share", R.string.tip_links),
    UseCase.Study to Tip("study-scan", R.string.tip_study),
    UseCase.Projects to Tip("projects-menu", R.string.tip_projects),
    UseCase.Notes to Tip("notes-wikilinks", R.string.tip_notes),
)

/** Compares "1.2.3" style versions, ignoring anything after "-"; null when either is not a version. */
fun compareVersions(a: String, b: String): Int? {
    fun parse(v: String): List<Int>? = v.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: return null }
    val x = parse(a) ?: return null
    val y = parse(b) ?: return null
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

fun startupScreen(
    onboardingDone: Boolean,
    releaseNotesSeen: String?,
    currentVersion: String,
    catalog: (String) -> ReleaseNotes? = ReleaseNotesCatalog::forVersion,
): StartupScreen {
    if (!onboardingDone) return StartupScreen.Onboarding
    val notes = catalog(currentVersion) ?: return StartupScreen.None
    val seenAlready = releaseNotesSeen != null && (compareVersions(releaseNotesSeen, currentVersion) ?: -1) >= 0
    return if (seenAlready) StartupScreen.None else StartupScreen.WhatsNew(notes)
}

fun captureOrder(p: Personalization): List<Capture> {
    val first = if (p.useCases == setOf(UseCase.Tasks)) Capture.Task else Capture.Note
    val wanted = buildList {
        if (UseCase.Tasks in p.useCases) add(Capture.Task)
        if (p.dictation == true || p.where == CaptureWhere.OnTheGo) add(Capture.Voice)
        if (p.savesLinks == true) add(Capture.Link)
        if (p.paper == true) add(Capture.Scan)
        if (UseCase.Research in p.useCases) add(Capture.Link)
        if (UseCase.Study in p.useCases) add(Capture.Scan)
        if (UseCase.Notes in p.useCases) add(Capture.Voice)
    }
    return (listOf(first) + wanted + Capture.entries).distinct()
}

fun dashboardSections(p: Personalization): List<DashboardSection> {
    val list = mutableListOf(DashboardSection.Daily, DashboardSection.Echo, DashboardSection.Recent, DashboardSection.Projects)
    if (UseCase.Projects in p.useCases || p.style == ExperienceStyle.Organized) {
        list.remove(DashboardSection.Projects)
        list.add(list.indexOf(DashboardSection.Recent), DashboardSection.Projects)
    }
    if (p.style == ExperienceStyle.Explore || UseCase.Research in p.useCases) {
        list.remove(DashboardSection.Echo)
        list.add(0, DashboardSection.Echo)
    }
    if (p.journal == true) {
        list.remove(DashboardSection.Daily)
        list.add(0, DashboardSection.Daily)
    }
    return list
}

fun showsTips(p: Personalization): Boolean = when {
    p.familiarity == Familiarity.Experienced -> false
    p.familiarity == Familiarity.New -> true
    p.style == ExperienceStyle.Simple -> false
    else -> !(p.familiarity != Familiarity.Regular && p.useCases.isEmpty() && p.style == null)
}

fun nextTip(p: Personalization, dismissed: Set<String>): Tip? {
    if (!showsTips(p)) return null
    val relevant = TIPS.filter { it.first in p.useCases }.map { it.second }
    val general = if (p.style == ExperienceStyle.Explore) listOf(TIPS.last().second) else emptyList()
    return (relevant + general + TIPS.map { it.second }).distinct().firstOrNull { it.id !in dismissed }
}
