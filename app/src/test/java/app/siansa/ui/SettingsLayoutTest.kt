package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Settings, grouped and reordered so each setting is where you'd look for it (issue #780). */
class SettingsLayoutTest {
    private val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }
    private fun source(name: String) = File(ui, name).readText()

    @Test fun theStartPageHasSixGroupsInOrder() {
        val hub = source("SettingsHub.kt").substringAfter("fun SettingsHub(").substringBefore("fun HubGroup(")
        val groups = Regex("""HubGroup\("([^"]+)"""").findAll(hub).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Sources", "Listening", "Home and mixes", "Look", "Your listening", "App"), groups)
        val rows = Regex("""HubRow\([^,]+, "([^"]+)"""").findAll(hub).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "Servers and services", "Playback", "Streaming and data", "Downloads", "Audiobooks and podcasts", "Home",
                "Mixes and vibes", "Style", "Scrobbling and history", "Backup and restore", "Help", "About",
            ),
            rows,
        )
    }

    @Test fun settingsLiveWhereTheirEffectIs() {
        val playback = source("SettingsHub.kt").substringAfter("fun PlaybackSettingsScreen(").substringBefore("fun SkipChips(")
        assertFalse("Streaming quality moved to Streaming and data", playback.contains("\"Streaming quality\""))
        assertFalse("Load songs ahead moved to Streaming and data", playback.contains("\"Load songs ahead\""))
        assertFalse("The books' skips moved to Audiobooks and podcasts", playback.contains("SkipChips("))
        val streaming = source("SettingsHub.kt").substringAfter("fun StreamingDataScreen(").substringBefore("fun restartApp(")
        assertTrue(streaming.contains("\"Streaming quality\"") && streaming.contains("\"Load songs ahead\"") && streaming.contains("\"Song cache\""))
        assertFalse("The image cache moved to Downloads", streaming.contains("\"Image cache\""))
        assertFalse("Animated covers moved to Style", streaming.contains("\"Animated covers\""))
        assertTrue(source("DownloadsScreens.kt").contains("Heading(\"Image cache\")"))
        assertTrue(source("StyleScreens.kt").contains("SwitchRow(\"Animated covers\""))
        assertTrue(source("ScrobblingScreen.kt").contains("\"Show songs not in your library\""))
        assertTrue(source("AboutScreen.kt").contains("\"What's new\""))
    }

    @Test fun foldedSectionsAreRemembered() {
        assertEquals(setOf("song-radio"), FoldMemory.foldedAfter(emptySet(), "song-radio", fold = true))
        assertEquals(emptySet<String>(), FoldMemory.foldedAfter(setOf("song-radio"), "song-radio", fold = false))
        assertEquals(setOf("a", "b"), FoldMemory.foldedAfter(setOf("a", "b"), "b", fold = true))
    }

    @Test fun homeRowsSayWhatsSet() {
        assertEquals("Your music", homeSourcesLine(1, downloadedOnly = false))
        assertEquals("Your music and 1 other user", homeSourcesLine(2, downloadedOnly = false))
        assertEquals("Your music and 3 other users · only downloaded music", homeSourcesLine(4, downloadedOnly = true))
        assertEquals("Every section shown · Normal spacing", homeArrangeLine(0, "Normal"))
        assertEquals("2 sections hidden · Compact spacing", homeArrangeLine(2, "Compact"))
    }
}
