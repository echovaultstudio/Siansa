package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings, regrouped (the owner, 13 Nov; issue #780 before; by what they do, each setting in one place, the owner,
 * 17 Nov): you and your services first, then twelve pages in four groups, each setting where you'd look for it.
 */
class SettingsLayoutTest {
    private val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }
    private fun source(name: String) = File(ui, name).readText()

    @Test fun theStartPageHasFourGroupsOfTwelveTiles() {
        val hub = source("SettingsHub.kt").substringAfter("fun SettingsHub(").substringBefore("private fun SearchBox(")
        val groups = Regex("""HubGroupLabel\("([^"]+)"""").findAll(hub).map { it.groupValues[1] }.toList()
        // One word each, no "&" (the owner, 20 Nov).
        assertEquals(listOf("Music", "Phone", "Look", "Support"), groups)
        val tiles = Regex("""HubTile\([^,]+, "([^"]+)", "([^"]+)"\)""").findAll(hub).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(
            listOf(
                "Playback" to "settings/sound", "Mixes" to "settings/radios", "Discovery" to "settings/discovery", "Scrobbling" to "settings/scrobbling",
                "Storage" to "settings/streaming", "Backup" to "settings/backup",
                "Home" to "settings/home", "Theme" to "settings/themes", "Player" to "settings/now-playing", "Navigation" to "settings/bottom-bar",
                "Help" to "settings/help", "About" to "settings/about",
            ),
            tiles,
        )
        // Above the groups: the search, you and your server, and the services with Manage.
        for (part in listOf("SearchBox(nav)", "YouCard(", "ServicesCard(")) assertTrue("The start page has no $part", hub.contains(part))
        assertTrue(source("SettingsHub.kt").contains("nav.navigate(\"settings/services\")"))
        assertTrue(source("SettingsHub.kt").contains("nav.navigate(\"settings/find\")"))
    }

    @Test fun pagesAreCalledWhatTheirTilesAre() {
        assertTrue(source("CrossfadeScreen.kt").contains("Screen(title = \"Playback\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Mixes\""))
        assertTrue(source("DiscoveryScreen.kt").contains("Screen(title = \"Discovery\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Storage\""))
        assertFalse("Audiobooks & podcasts is gone: its skips are in Playback", source("SettingsHub.kt").contains("Screen(title = \"Audiobooks & podcasts\""))
        assertTrue(source("SettingsPages.kt").contains("Screen(title = \"Home\""))
        assertTrue(source("ThemesScreen.kt").contains("Screen(title = \"Theme\""))
        assertTrue(source("StyleScreens.kt").contains("Screen(title = \"Player\""))
        assertTrue(source("StyleScreens.kt").contains("title = \"Navigation\""))
        assertTrue(source("ScrobblingScreen.kt").contains("Screen(title = \"Scrobbling\""))
        assertTrue(source("BackupScreen.kt").contains("Screen(title = \"Backup\""))
        assertTrue(source("SettingsHub.kt").contains("Screen(title = \"Help\""))
        assertTrue(source("ServicesScreen.kt").contains("Screen(title = \"Services\""))
        assertTrue(source("SettingsSearch.kt").contains("Screen(title = \"Find a setting\""))
        // No page or hint still sends people to a name that's gone.
        for (file in ui.listFiles()!!.filter { it.name.endsWith(".kt") }) {
            val text = file.readText()
            for (old in listOf("Settings › Servers and services", "Settings › Streaming and data", "Settings › Mixes and vibes", "Settings › Notices and Cards", "Settings › Auto Generated Mixes", "Settings › Style ›", "Settings › Scrobbling and history",
                "Settings › Connections", "Settings › Streaming & storage", "Settings › Scrobbling & history", "Settings › Radios & mixes",
                "Settings › Sound & playback", "Settings › Mixes & radios", "Settings › Storage & downloads", "Settings › Backup & restore",
                "Settings › Help & feedback", "Settings › Home page", "Settings › Now playing", "Settings › Bottom bar", "Settings › Listening")) {
                assertFalse("${file.name} still says $old", text.contains(old))
            }
        }
    }

    @Test fun settingsLiveWhereTheirEffectIs() {
        // Sound: the equaliser's page, levelling and crossfade (the owner, 13 Nov).
        val sound = source("CrossfadeScreen.kt").substringAfter("fun CrossfadeScreen(").substringBefore("private fun levellingHint(")
        assertTrue("Sound doesn't open the equaliser", sound.contains("nav.navigate(\"settings/equaliser\")"))
        assertTrue(sound.contains("FoldSection(\"Levelling\"") && sound.contains("FoldSection(\"Crossfade & DJ blends\""))
        // DJ blends and the book skips are here too (the owner, 17 Nov).
        assertTrue(sound.contains("BeatsUseSwitch(graph, app.siansa.data.BeatsUse.DJ)"))
        assertTrue(sound.contains("SkipChips(settings.bookSkipBackSec") && sound.contains("SkipChips(settings.bookSkipForwardSec"))
        // Mixes & radios: who makes Home's mixes and their settings, the radio, the queue's end and Wind down.
        val radios = source("SettingsHub.kt").substringAfter("fun RadiosScreen(").substringBefore("/** 10 to 90 seconds")
        for (part in listOf("FoldSection(\"Home's mixes\"", "nav.navigate(\"settings/beats/mixes\")", "FoldSection(\"Radio wandering\"", "FoldSection(\"When the queue ends\"", "FoldSection(\"Wind down\"", "nav.navigate(\"vibes\")")) assertTrue("Mixes & radios misses $part", radios.contains(part))
        // Discovery: genres, similar music and moods, each Siansa Beats' switch.
        val discovery = source("DiscoveryScreen.kt")
        for (use in listOf("GENRES", "SOUND", "MOODS")) assertTrue("Discovery misses $use", discovery.contains("BeatsUseSwitch(graph, BeatsUse.$use)"))
        // Streaming & storage: the quality (as drop-downs), loading ahead, the caches and the downloads.
        val streaming = source("SettingsHub.kt").substringAfter("fun StreamingDataScreen(").substringBefore("fun restartApp(")
        for (part in listOf("FoldSection(\"Streaming quality\"", "ChoicePicker(", "\"Load songs ahead\"", "\"Song cache\"", "DownloadSettings(nav)")) assertTrue("Streaming & storage misses $part", streaming.contains(part))
        val downloads = source("DownloadsScreens.kt").substringAfter("fun DownloadSettings(").substringBefore("\n}\n")
        for (part in listOf("\"Download on Wi-Fi only\"", "ChoicePicker(\"Download quality\"", "\"Storage limit\"", "\"Remove all downloads\"", "CoverDownloadSection()", "ImageCacheSection()", "nav.navigate(\"downloads\")")) {
            assertTrue("The downloads miss $part", downloads.contains(part))
        }
        assertTrue("The image cache isn't a slider", source("DownloadsScreens.kt").contains("ImageCacheSlider(settings.imageCacheMb)"))
        // Home page: the top of Home, the menu's order and the cards.
        val home = source("SettingsPages.kt").substringAfter("fun HomeSettingsScreen(").substringBefore("fun NoticeKinds(")
        for (part in listOf("FoldSection(\"Top of Home\"", "ShortcutCountRow(", "SwitchRow(\"Notices\"", "YourOwnNotices(nav)", "FoldSection(\"Home menu\"", "nav.navigate(\"settings/home/arrange\")", "nav.navigate(\"settings/cards\")")) assertTrue("Home page misses $part", home.contains(part))
        // Theme: light or dark, the colours, dynamic colours, the status bar and animated covers.
        val theme = source("ThemesScreen.kt")
        for (part in listOf("FoldSection(\"Dynamic colours\"", "FoldSection(\"Status bar\"", "FoldSection(\"Animated covers\"")) assertTrue("Theme misses $part", theme.contains(part))
        // Now playing: the buttons, the play button, the DJ mix bar and the mini player.
        val np = source("StyleScreens.kt").substringAfter("fun NowPlayingStyleScreen(").substringBefore("fun PlaySlidesSettings(")
        for (part in listOf("FoldSection(\"Buttons\"", "FoldSection(\"Star ratings\"", "FoldSection(\"About this song\"", "FoldSection(\"Play button\"", "FoldSection(\"DJ mix bar\"", "FoldSection(\"Mini player\"")) assertTrue("Now playing misses $part", np.contains(part))
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
        assertTrue(SettingsIndex.find("crossfade").first().let { it.route == "settings/sound" && it.section == "crossfade-dj-blends" })
        assertTrue(SettingsIndex.find("Theme").all { it.page == "Theme" })
        // The pages' old names still find them (one word each since the owner, 20 Nov).
        assertTrue(SettingsIndex.find("feedback").any { it.page == "Help" })
        assertTrue(SettingsIndex.find("radios").any { it.page == "Mixes" })
        assertTrue(SettingsIndex.find("bottom bar").any { it.page == "Navigation" })
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

    @Test fun eachSettingLivesInOnePlace() {
        // The owner, 17 Nov: no switch shown in two places. The Siansa Beats page only connects and links.
        val hub = source("ConnectionsHub.kt")
        assertFalse("The Beats card still has its own switches", hub.contains("BeatsUses(graph"))
        assertFalse("Use Siansa Beats for is still on its page", hub.contains("\"Use Siansa Beats for\""))
        assertTrue(hub.contains("Heading(\"Used for\")"))
        val help = source("SettingsHub.kt").substringAfter("fun HelpScreen(").substringBefore("/** \"Syncing")
        assertEquals("Debug logs is one switch, in Help", 1, Regex("SwitchRow\\(\"Debug logs\"").findAll(source("SettingsHub.kt")).count())
        assertTrue(help.contains("Heading(\"Debugging\")") && help.contains("\"Bad blend button\""))
        assertFalse("The old Notices page is back", source("SettingsPages.kt").contains("fun NoticesSettingsScreen("))
        val downloads = source("DownloadsScreens.kt")
        assertEquals("Download on Wi-Fi only shows twice", 1, Regex("SwitchRow\\(\"Download on Wi-Fi only\"").findAll(downloads).count())
    }
}
