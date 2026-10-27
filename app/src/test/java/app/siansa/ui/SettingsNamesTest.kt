package app.siansa.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Settings show just each entry's name: no summary under it and no arrow beside it (issue #375). */
class SettingsNamesTest {
    private val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }

    @Test fun hubRowsTakeNoSummary() {
        val hub = File(ui, "SettingsHub.kt").readText()
        assertFalse("HubRow still takes a summary", Regex("""fun HubRow\([^)]*subtitle""").containsMatchIn(hub))
    }

    @Test fun noArrowsOnSettingsPages() {
        for (name in listOf("SettingsHub.kt", "StyleScreens.kt", "UsersScreen.kt", "CarouselEditor.kt", "DownloadsScreens.kt")) {
            assertFalse("$name still draws an arrow beside a setting", File(ui, name).readText().contains("KeyboardArrowRight"))
        }
    }

    /** The entry for users is called what its page is called (#565): "Users", not "Users and library sync". */
    @Test fun theUsersEntryIsCalledUsers() {
        val hub = File(ui, "SettingsHub.kt").readText()
        assertTrue("Settings has no \"Users\" entry", hub.contains("HubRow(Icons.Rounded.People, \"Users\")"))
        assertFalse("The old name is still there", hub.contains("Users and library sync"))
        assertTrue("The page isn't called Users", File(ui, "UsersScreen.kt").readText().contains("Screen(title = \"Users\""))
    }

    /** #567: Report a problem and Logs are one entry, Help, with the bug icon, at the foot of Settings. */
    @Test fun helpIsOneEntryAtTheBottom() {
        val hub = File(ui, "SettingsHub.kt").readText()
        val rows = Regex("""HubRow\(Icons\.Rounded\.(\w+), "([^"]+)"\)""").findAll(hub).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue("No rows found", rows.isNotEmpty())
        assertTrue("Help isn't last with the bug icon: ${rows.last()}", rows.last() == ("BugReport" to "Help"))
        assertFalse("Report a problem is still its own entry", rows.any { it.second == "Report a problem" })
        assertFalse("Logs is still its own entry", rows.any { it.second == "Logs" })
    }
}
