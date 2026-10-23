package app.musix.ui

import app.musix.data.RecentSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** One search box for names and sounds (issue #373). */
class OneSearchTest {
    @Test fun soundSearchNeedsThreeLettersAndAudioMuse() {
        assertFalse(soundSearchWanted("ja", aiDown = false))
        assertTrue(soundSearchWanted("jazz", aiDown = false))
        assertFalse(soundSearchWanted("jazz", aiDown = true))
        assertFalse(soundSearchWanted("  ab ", aiDown = false))
    }

    @Test fun recentSearchesAreOneListEachWordingOnce() {
        val history = listOf(RecentSearch("Rainy Jazz", sound = true), RecentSearch("rainy jazz"), RecentSearch("Radiohead"))
        assertEquals(listOf("Rainy Jazz", "Radiohead"), oneSearchList(history).map { it.query })
    }

    @Test fun theSoundTabIsGone() {
        val ui = listOf(File("src/main/java/app/musix/ui"), File("app/src/main/java/app/musix/ui")).first { it.isDirectory }
        val code = File(ui, "SearchScreen.kt").readText()
        assertFalse(code.contains("FilterChip"))
        assertFalse(code.contains("soundMode"))
    }
}
