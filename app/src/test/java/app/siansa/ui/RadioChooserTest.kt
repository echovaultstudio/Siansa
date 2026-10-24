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
    @Test fun eachChoiceSaysWhatItDoes() {
        assertNotEquals(radioModeHint(ContinuousMode.STEADY), radioModeHint(ContinuousMode.DJ))
        assertTrue(radioModeHint(ContinuousMode.DJ).contains("tempo"))
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
