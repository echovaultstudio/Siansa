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
        for (name in listOf("SettingsHub.kt", "StyleScreens.kt", "ConnectionsHub.kt", "CarouselEditor.kt", "DownloadsScreens.kt")) {
            assertFalse("$name still draws an arrow beside a setting", File(ui, name).readText().contains("KeyboardArrowRight"))
        }
    }

    /**
     * Servers and services are one entry, Connections, called what its page is called (#565, #665): no separate
     * Users entry, and the page has Servers and Services.
     */
    @Test fun serversAndServicesAreOneConnectionsEntry() {
        val hub = File(ui, "SettingsHub.kt").readText()
        assertTrue("Settings has no \"Connections\" entry", hub.contains("HubRow(Icons.Rounded.Dns, \"Connections\")"))
        assertFalse("Users is still its own entry", hub.contains("\"Users\")"))
        val page = File(ui, "ConnectionsHub.kt").readText()
        assertTrue("The page isn't called Connections", page.contains("title = \"Connections\""))
        assertTrue("No Servers section", page.contains("SectionHeading(\"Servers\")"))
        assertTrue("No Services section", page.contains("SectionHeading(\"Services\")"))
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

    /** #576: About sits just above Help, with the info icon. */
    @Test fun aboutIsJustAboveHelp() {
        val hub = File(ui, "SettingsHub.kt").readText()
        val rows = Regex("""HubRow\(Icons\.Rounded\.(\w+), "([^"]+)"\)""").findAll(hub).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue("About isn't just above Help: ${rows.takeLast(2)}", rows.takeLast(2) == listOf("Info" to "About", "BugReport" to "Help"))
    }
}
