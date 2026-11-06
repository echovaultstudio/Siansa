package app.siansa.ui

import app.siansa.data.Song
import app.siansa.mix.ContinuousMode
import app.siansa.playback.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Song radio asks Steady or DJ, and the queue marks what it added by itself (issue #337). */
class RadioChooserTest {
    @Test fun eachChoiceHasItsOwnIcon() {
        // Issue #916: a wave for Steady, a turntable for DJ, in place of the descriptions.
        assertNotEquals(radioModeIcon(ContinuousMode.STEADY).name, radioModeIcon(ContinuousMode.DJ).name)
        assertEquals("Siansa.Turntable", radioModeIcon(ContinuousMode.DJ).name)
    }

    @Test fun theMixingDocsLinkGoesToTheCrossfadeDocs() {
        assertTrue(AboutLinks.MIXING.startsWith(AboutLinks.DOCS.replace("/tree/", "/blob/")))
        assertTrue(AboutLinks.MIXING.endsWith("playback.md#crossfade"))
        // The section it points at is there.
        val docs = listOf(File("../docs/playback.md"), File("docs/playback.md")).first { it.isFile }
        assertTrue(docs.readLines().any { it == "## Crossfade" })
    }

    @Test fun askingHoldsTheSongUntilAChoice() {
        val song = Song("s1", "Song")
        RadioChooser.ask(song)
        assertEquals(song, RadioChooser.song.value)
        RadioChooser.song.value = null
        assertNull(RadioChooser.song.value)
    }

    @Test fun nothingIsMarkedAutoByDefault() {
        assertTrue(PlayerState().auto.isEmpty())
    }

    @Test fun everyStartSongRadioGoesThroughTheChooser() {
        val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }
        val direct = ui.walkTopDown().filter { it.extension == "kt" && it.name != "RadioChooser.kt" }
            .filter { it.readText().contains("songRadio.start(") }
            .map { it.name }
            .toList()
        assertFalse("Start song radio without asking Steady or DJ in: $direct", direct.isNotEmpty())
    }
}
