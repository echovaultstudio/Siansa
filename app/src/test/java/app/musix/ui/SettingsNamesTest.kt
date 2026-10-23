package app.musix.ui

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Settings show just each entry's name: no summary under it and no arrow beside it (issue #375). */
class SettingsNamesTest {
    private val ui = listOf(File("src/main/java/app/musix/ui"), File("app/src/main/java/app/musix/ui")).first { it.isDirectory }

    @Test fun hubRowsTakeNoSummary() {
        val hub = File(ui, "SettingsHub.kt").readText()
        assertFalse("HubRow still takes a summary", Regex("""fun HubRow\([^)]*subtitle""").containsMatchIn(hub))
    }

    @Test fun noArrowsOnSettingsPages() {
        for (name in listOf("SettingsHub.kt", "StyleScreens.kt", "UsersScreen.kt", "CarouselEditor.kt", "DownloadsScreens.kt")) {
            assertFalse("$name still draws an arrow beside a setting", File(ui, name).readText().contains("KeyboardArrowRight"))
        }
    }
}
