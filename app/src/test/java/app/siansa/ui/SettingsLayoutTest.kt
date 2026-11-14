package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings, regrouped (the owner, 13 Nov; issue #780 before): you and your services first, then twelve pages in three
 * groups of four, each setting where you'd look for it.
 */
class SettingsLayoutTest {
    private val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }
    private fun source(name: String) = File(ui, name).readText()

    @Test fun theStartPageHasThreeGroupsOfFourTiles() {
        val hub = source("SettingsHub.kt").substringAfter("fun SettingsHub(").substringBefore("private fun SearchBox(")
        val groups = Regex("""HubGroupLabel\("([^"]+)"""").findAll(hub).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Playing", "Home & look", "Listening & app"), groups)
        val tiles = Regex("""HubTile\([^,]+, "([^"]+)", "([^"]+)"\)""").findAll(hub).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(
            listOf(
                "Sound" to "settings/sound", "Radios & mixes" to "settings/radios", "Streaming & storage" to "settings/streaming", "Audiobooks & podcasts" to "settings/books",
                "Home page" to "settings/home", "Theme" to "settings/themes", "Now playing" to "settings/now-playing", "Bottom bar" to "settings/bottom-bar",
                "Scrobbling & history" to "settings/scrobbling", "Backup & restore" to "settings/backup", "Help & feedback" to "settings/help", "About" to "settings/about",
            ),
            tiles,
        )
        // Above the groups: the search, you and your server, and the services with Manage.
        for (part in listOf("SearchBox(nav)", "YouCard(", "ServicesCard(")) assertTrue("The start page has no $part", hub.contains(part))
        assertTrue(source("SettingsHub.kt").contains("nav.navigate(\"settings/services\")"))
        assertTrue(source("SettingsHub.kt").contains("nav.navigate(\"settings/find\")"))
    }

    @Test fun pagesAreCalledWhatTheirTilesAre() {
        assertTrue(source("CrossfadeScreen.kt").contains("Screen(title = \"Sound\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Radios & mixes\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Streaming & storage\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Audiobooks & podcasts\""))
        assertTrue(source("SettingsPages.kt").contains("Screen(title = \"Home page\""))
        assertTrue(source("ThemesScreen.kt").contains("Screen(title = \"Theme\""))
        assertTrue(source("StyleScreens.kt").contains("Screen(title = \"Now playing\""))
        assertTrue(source("StyleScreens.kt").contains("title = \"Bottom bar\""))
        assertTrue(source("ScrobblingScreen.kt").contains("Screen(title = \"Scrobbling & history\""))
        assertTrue(source("BackupScreen.kt").contains("Screen(title = \"Backup & restore\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Help & feedback\""))
        assertTrue(source("ServicesScreen.kt").contains("Screen(title = \"Services\""))
        assertTrue(source("SettingsSearch.kt").contains("Screen(title = \"Find a setting\""))
        // No page or hint still sends people to a name that's gone.
        for (file in ui.listFiles()!!.filter { it.name.endsWith(".kt") }) {
            val text = file.readText()
            for (old in listOf("Settings › Servers and services", "Settings › Streaming and data", "Settings › Mixes and vibes", "Settings › Notices and Cards", "Settings › Auto Generated Mixes", "Settings › Style ›", "Settings › Scrobbling and history")) {
                assertFalse("${file.name} still says $old", text.contains(old))
            }
        }
    }

    @Test fun settingsLiveWhereTheirEffectIs() {
        // Sound: the equaliser's page, levelling and crossfade (the owner, 13 Nov).
        val sound = source("CrossfadeScreen.kt").substringAfter("fun CrossfadeScreen(").substringBefore("private fun levellingHint(")
        assertTrue("Sound doesn't open the equaliser", sound.contains("nav.navigate(\"settings/equaliser\")"))
        assertTrue(sound.contains("FoldSection(\"Levelling\"") && sound.contains("FoldSection(\"Crossfade\""))
        // Radios & mixes: the radio, the queue's end, Wind down and the daily mixes.
        val radios = source("SettingsHub.kt").substringAfter("fun RadiosScreen(").substringBefore("fun BooksSettingsScreen(")
        for (part in listOf("FoldSection(\"Radio wandering\"", "FoldSection(\"When the queue ends\"", "FoldSection(\"Wind down\"", "nav.navigate(\"vibes\")")) assertTrue("Radios & mixes misses $part", radios.contains(part))
        // Streaming & storage: the quality (as drop-downs), loading ahead, the caches and the downloads.
        val streaming = source("SettingsHub.kt").substringAfter("fun StreamingDataScreen(").substringBefore("fun restartApp(")
        for (part in listOf("FoldSection(\"Streaming quality\"", "ChoicePicker(", "\"Load songs ahead\"", "\"Song cache\"", "DownloadSettings(nav)")) assertTrue("Streaming & storage misses $part", streaming.contains(part))
        val downloads = source("DownloadsScreens.kt").substringAfter("fun DownloadSettings(").substringBefore("\n}\n")
        for (part in listOf("\"Download on Wi-Fi only\"", "ChoicePicker(\"Download quality\"", "\"Storage limit\"", "\"Remove all downloads\"", "CoverDownloadSection()", "ImageCacheSection()", "nav.navigate(\"downloads\")")) {
            assertTrue("The downloads miss $part", downloads.contains(part))
        }
        assertTrue("The image cache isn't a slider", source("DownloadsScreens.kt").contains("ImageCacheSlider(settings.imageCacheMb)"))
        // Audiobooks & podcasts: the skips alone; Audiobookshelf is under Services.
        val books = source("SettingsHub.kt").substringAfter("fun BooksSettingsScreen(").substringBefore("\n}\n")
        assertTrue(books.contains("SkipChips(settings.bookSkipBackSec") && books.contains("SkipChips(settings.bookSkipForwardSec"))
        assertFalse("Audiobooks & podcasts still scans or sets up Audiobookshelf", books.contains("Scan") || books.contains("settings/audiobookshelf"))
        // Home page: the top of Home, the menu's order and the cards.
        val home = source("SettingsPages.kt").substringAfter("fun HomeSettingsScreen(").substringBefore("fun NoticeKinds(")
        for (part in listOf("FoldSection(\"Top of Home\"", "ShortcutCountRow(", "SwitchRow(\"Notices\"", "FoldSection(\"Home menu\"", "nav.navigate(\"settings/cards\")")) assertTrue("Home page misses $part", home.contains(part))
        // Theme: light or dark, the colours, dynamic colours, the status bar and animated covers.
        val theme = source("ThemesScreen.kt")
        for (part in listOf("FoldSection(\"Dynamic colours\"", "FoldSection(\"Status bar\"", "FoldSection(\"Animated covers\"")) assertTrue("Theme misses $part", theme.contains(part))
        // Now playing: the buttons, the play button, the DJ mix bar and the mini player.
        val np = source("StyleScreens.kt").substringAfter("fun NowPlayingStyleScreen(").substringBefore("fun PlaySlidesSettings(")
        for (part in listOf("FoldSection(\"Buttons\"", "FoldSection(\"Play button\"", "FoldSection(\"DJ mix bar\"", "FoldSection(\"Mini player\"")) assertTrue("Now playing misses $part", np.contains(part))
        assertTrue(source("ScrobblingScreen.kt").contains("\"Show songs not in your library\""))
        assertTrue(source("AboutScreen.kt").contains("\"What's new\""))
        assertFalse("Beat-matched blends are still marked experimental", source("CrossfadeScreen.kt").contains("(experimental)"))
        // Style is gone: nothing opens it.
        assertFalse(source("StyleScreens.kt").contains("fun StyleScreen("))
    }

    @Test fun foldedSectionsAreRemembered() {
        assertEquals(setOf("song-radio"), FoldMemory.foldedAfter(emptySet(), "song-radio", fold = true))
        assertEquals(emptySet<String>(), FoldMemory.foldedAfter(setOf("song-radio"), "song-radio", fold = false))
        assertEquals(setOf("a", "b"), FoldMemory.foldedAfter(setOf("a", "b"), "b", fold = true))
    }

    /** The search finds a setting by its name, its page or the words people use for it, and knows its section. */
    @Test fun theSearchFindsSettingsByNamePageAndWords() {
        assertEquals("Pure black", SettingsIndex.find("battery").first().name)
        assertTrue(SettingsIndex.find("loud").take(3).any { it.name == "Levelling" })
        assertTrue(SettingsIndex.find("crossfade").first().let { it.route == "settings/sound" && it.section == "crossfade" })
        assertTrue(SettingsIndex.find("Theme").all { it.page == "Theme" })
        assertTrue(SettingsIndex.find("").isEmpty())
        assertTrue(SettingsIndex.find("zzzz").isEmpty())
        // Every entry's section is a FoldSection tag that exists, spelt as FoldSection spells it.
        val sources = ui.listFiles()!!.filter { it.name.endsWith(".kt") }.joinToString("\n") { it.readText() }
        val tags = Regex("""FoldSection\("([^"]+)"""").findAll(sources).map { it.groupValues[1].lowercase().replace(Regex("[^a-z0-9]+"), "-") }.toSet() +
            Regex("""tag = "([^"]+)"""").findAll(sources).map { it.groupValues[1] }.toSet()
        for (entry in SettingsIndex.entries) {
            val section = entry.section ?: continue
            assertTrue("${entry.name} points at a section that isn't there: $section", section in tags || section.startsWith("service-"))
        }
        // A section asked for is taken once, by its own tag.
        SettingsFocus.ask("crossfade")
        assertFalse(SettingsFocus.take("levelling"))
        assertTrue(SettingsFocus.take("crossfade"))
        assertFalse(SettingsFocus.take("crossfade"))
    }
}
