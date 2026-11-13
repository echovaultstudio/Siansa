package app.siansa.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Settings' start page: nothing on its top panel has an arrow, and the tiles are just their names (issue #375; the owner, 13 Nov). */
class SettingsNamesTest {
    private val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }

    @Test fun hubTilesTakeNoSummary() {
        val hub = File(ui, "SettingsHub.kt").readText()
        assertFalse("HubTile still takes a summary", Regex("""data class HubTile\([^)]*subtitle""").containsMatchIn(hub))
    }

    /** Settings › Home page has the top of Home, the menu and Cards; the source filter and Arrange Home are only behind Home's own button. */
    @Test fun homeSettingsLeaveTheFilterToHome() {
        val pages = File(ui, "SettingsPages.kt").readText()
        val home = pages.substringAfter("fun HomeSettingsScreen(").substringBefore("fun NoticeKinds(")
        assertTrue(home.contains("PageLink(\"Cards\""))
        assertFalse("Settings › Home page still links the source filter", home.contains("\"Source filter\""))
        assertFalse("Settings › Home page still links Arrange Home", home.contains("\"Arrange Home\""))
        assertFalse("Settings › Home page still opens Home's panel", home.contains("HomeSettingsSheet"))
    }

    @Test fun noArrowsOnSettingsPages() {
        for (name in listOf("SettingsHub.kt", "StyleScreens.kt", "ConnectionsHub.kt", "CarouselEditor.kt", "DownloadsScreens.kt", "ServicesScreen.kt")) {
            assertFalse("$name still draws an arrow beside a setting", File(ui, name).readText().contains("KeyboardArrowRight"))
        }
    }

    /** Servers and services are set up on Connections, with Servers and Services sections; Services is the page that switches them. */
    @Test fun connectionsKeepsServersAndServices() {
        val page = File(ui, "ConnectionsHub.kt").readText()
        assertTrue("The page isn't called Connections", page.contains("title = \"Connections\""))
        assertTrue("No Servers section", page.contains("SectionHeading(\"Servers\")"))
        assertTrue("No Services section", page.contains("SectionHeading(\"Services\")"))
        val services = File(ui, "ServicesScreen.kt").readText()
        assertTrue("Services has no switch per service", services.contains("serviceSwitch-"))
        assertTrue("Services has no For the best Siansa", services.contains("FOR THE BEST SIANSA"))
    }

    /** Help & feedback and About are the last two tiles, in that order. */
    @Test fun helpAndAboutAreLast() {
        val hub = File(ui, "SettingsHub.kt").readText()
        val tiles = Regex("""HubTile\(Icons\.Rounded\.(\w+), "([^"]+)"""").findAll(hub).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue("No tiles found", tiles.isNotEmpty())
        assertTrue("Help & feedback and About aren't last: ${tiles.takeLast(2)}", tiles.takeLast(2) == listOf("BugReport" to "Help & feedback", "Info" to "About"))
    }
}
