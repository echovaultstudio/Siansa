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
        // Renamed for v1.0 (the owner): Home and mixes is just Home.
        assertEquals(listOf("Sources", "Listening", "Home", "Look", "Your listening", "App"), groups)
        val rows = Regex("""HubRow\([^,]+, "([^"]+)"""").findAll(hub).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "Connections", "Playback", "Data", "Notices and Cards", "Auto Generated Mixes",
                "Style", "Scrobbling and history", "Backup and restore", "Help", "About",
            ),
            rows,
        )
        // Data keeps the icon Streaming and data had.
        assertTrue(hub.contains("HubRow(Icons.Rounded.SwapVert, \"Data\")"))
    }

    @Test fun pagesAreCalledWhatTheirEntriesAre() {
        assertTrue(source("ConnectionsHub.kt").contains("title = \"Connections\""))
        assertTrue(source("SettingsScreens.kt").contains("else \"Connections\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Data\""))
        assertTrue(source("SettingsPages.kt").contains("Screen(title = \"Notices and Cards\""))
        assertTrue(source("VibesScreens.kt").contains("title = \"Auto Generated Mixes\""))
        // No page or hint still sends people to a name that's gone.
        for (file in ui.listFiles()!!.filter { it.name.endsWith(".kt") }) {
            val text = file.readText()
            for (old in listOf("Settings › Servers and services", "Settings › Streaming and data", "Settings › Mixes and vibes", "Settings › Audiobooks and podcasts", "Settings › Home ›")) {
                assertFalse("${file.name} still says $old", text.contains(old))
            }
        }
    }

    @Test fun settingsLiveWhereTheirEffectIs() {
        val playback = source("SettingsHub.kt").substringAfter("fun PlaybackSettingsScreen(").substringBefore("fun SkipChips(")
        // v1.0 (the owner): streaming quality is what you hear, and the book player's skips are playback too.
        assertTrue("Streaming quality isn't under Playback", playback.contains("FoldSection(\"Streaming quality\""))
        assertTrue("The books' skips aren't under Playback", playback.contains("SkipChips(settings.bookSkipBackSec") && playback.contains("SkipChips(settings.bookSkipForwardSec"))
        assertFalse("Playback still links to Audiobookshelf (it's under Connections)", playback.contains("settings/audiobookshelf"))
        assertFalse("Audiobooks and podcasts is still its own page", source("SettingsPages.kt").contains("fun BooksSettingsScreen("))
        val data = source("SettingsHub.kt").substringAfter("fun StreamingDataScreen(").substringBefore("fun restartApp(")
        assertFalse("Streaming quality is still under Data", data.contains("\"Streaming quality\""))
        assertTrue(data.contains("\"Load songs ahead\"") && data.contains("\"Song cache\""))
        // Everything Downloads had in Settings is under Data.
        assertTrue("Downloads aren't under Data", data.contains("DownloadSettings(nav)"))
        val downloads = source("DownloadsScreens.kt").substringAfter("fun DownloadSettings(").substringBefore("\n}\n")
        for (part in listOf("\"Download on Wi-Fi only\"", "\"Download quality\"", "\"Storage limit\"", "\"Remove all downloads\"", "CoverDownloadSection()", "ImageCacheSection()", "nav.navigate(\"downloads\")")) {
            assertTrue("Data's downloads miss $part", downloads.contains(part))
        }
        assertTrue(source("DownloadsScreens.kt").contains("Heading(\"Image cache\")"))
        assertFalse("Downloads is still its own Settings entry", source("SettingsHub.kt").contains("\"Downloads\") { nav.navigate"))
        assertTrue(source("StyleScreens.kt").contains("SwitchRow(\"Animated covers\""))
        assertTrue(source("ScrobblingScreen.kt").contains("\"Show songs not in your library\""))
        assertTrue(source("AboutScreen.kt").contains("\"What's new\""))
        assertFalse("Beat-matched blends are still marked experimental", source("CrossfadeScreen.kt").contains("(experimental)"))
    }

    @Test fun foldedSectionsAreRemembered() {
        assertEquals(setOf("song-radio"), FoldMemory.foldedAfter(emptySet(), "song-radio", fold = true))
        assertEquals(emptySet<String>(), FoldMemory.foldedAfter(setOf("song-radio"), "song-radio", fold = false))
        assertEquals(setOf("a", "b"), FoldMemory.foldedAfter(setOf("a", "b"), "b", fold = true))
    }
}
