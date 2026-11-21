package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import app.drop.data.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Projects: create, fill, rename, delete (notes stay), membership from a note and from a selection. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ProjectsUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication
    private val projects get() = app.database.projectDao()

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun s(id: Int) = rule.activity.getString(id)

    private fun note(title: String) = runBlocking { app.database.noteDao().insert(Note(title = title, body = "", createdAt = 1, updatedAt = 1)) }

    private fun items(projectId: Long) = runBlocking { projects.observeItems(projectId).first() }.map { it.title }.sorted()

    @Test
    fun aProjectIsCreatedFilledRenamedAndDeletedWithoutLosingNotes() {
        note("Moodboard")
        note("Budget sheet")
        note("Unrelated")
        settle()
        waitForTag("nav-library")
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithTag("spaces").performClick()
        settle()
        rule.onNodeWithTag("space-Projects").performClick()
        settle()
        rule.onNodeWithText(s(R.string.projects_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.projects_new), useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("name-field").performTextInput("  Wedding  ")
        rule.onNodeWithTag("description-field").performTextInput("June")
        rule.onNodeWithText(s(R.string.spaces_save)).performClick()
        settle()
        val project = runBlocking { projects.all() }.single()
        assertEquals("Wedding", project.name)
        assertEquals("June", project.description)
        rule.onNodeWithTag("project").assertIsDisplayed()

        rule.onNodeWithTag("project-add").performClick()
        settle()
        rule.onNodeWithText("Moodboard").performClick()
        rule.onNodeWithText("Budget sheet").performClick()
        rule.onNodeWithText(s(R.string.spaces_add)).performClick()
        settle()
        assertEquals(listOf("Budget sheet", "Moodboard"), items(project.id))

        rule.onNodeWithTag("project-menu").performClick()
        settle()
        rule.onNodeWithText(s(R.string.spaces_rename)).performClick()
        settle()
        rule.onNodeWithTag("name-field").performTextReplacement("Wedding 2027")
        rule.onNodeWithText(s(R.string.spaces_save)).performClick()
        settle()
        assertEquals("Wedding 2027", runBlocking { projects.all() }.single().name)

        rule.onNodeWithTag("project-menu").performClick()
        settle()
        rule.onNodeWithText(s(R.string.projects_delete)).performClick()
        settle()
        rule.onNodeWithText(s(R.string.projects_delete_title)).assertIsDisplayed()
        // Confirm in the dialog.
        rule.onNodeWithTag("confirm-delete-project").performClick()
        settle()
        assertTrue(runBlocking { projects.all() }.isEmpty())
        assertEquals(3, runBlocking { app.database.noteDao().allNotes() }.size)
    }

    @Test
    fun aNoteJoinsAndLeavesProjectsFromItsMenu() {
        val id = note("Venue ideas")
        val projectId = runBlocking { projects.insert(Project(name = "Wedding", createdAt = 1, updatedAt = 1)) }
        settle()
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Venue ideas").performClick()
        settle()
        rule.onNodeWithContentDescription(s(R.string.note_more)).performClick()
        settle()
        rule.onNodeWithTag("note-projects").performClick()
        settle()
        rule.onNodeWithTag("membership-$projectId").performClick()
        settle()
        assertEquals(listOf("Venue ideas"), items(projectId))
        rule.onNodeWithTag("membership-$projectId").performClick()
        settle()
        assertTrue(items(projectId).isEmpty())

        // A new project made from here already holds the note.
        rule.onNodeWithText(s(R.string.projects_new)).performClick()
        settle()
        rule.onNodeWithTag("name-field").performTextInput("Honeymoon")
        rule.onNodeWithText(s(R.string.spaces_save)).performClick()
        settle()
        val honeymoon = runBlocking { projects.all() }.single { it.name == "Honeymoon" }
        assertEquals(listOf(id), runBlocking { projects.observeItems(honeymoon.id).first() }.map { it.id })
    }

    @Test
    fun selectedNotesAreAddedToANewProjectAndShownOnHome() {
        note("Flights")
        note("Hotels")
        settle()
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Flights").performTouchInput { longClick() }
        settle()
        rule.onNodeWithText("Hotels").performClick()
        settle()
        rule.onNodeWithTag("bulk-more").performClick()
        settle()
        rule.onNodeWithTag("bulk-project").performClick()
        settle()
        rule.onNodeWithText(s(R.string.bulk_project_new)).performTextInput("Trip")
        rule.onNodeWithText(s(R.string.bulk_project_create)).performClick()
        settle()
        val trip = runBlocking { projects.all() }.single()
        assertEquals(listOf("Flights", "Hotels"), items(trip.id))

        rule.onNodeWithTag("nav-home").performClick()
        settle()
        waitForTag("dashboard-list")
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("dashboard-project-${trip.id}"))
        rule.onNodeWithTag("dashboard-project-${trip.id}").performScrollTo().performClick()
        settle()
        rule.onNodeWithTag("project").assertIsDisplayed()
    }
}
