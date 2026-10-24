package app.siansa.ui

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Now playing's buttons beside play: repeat on the left, add to playlist on the right (issue #370). */
class PlayerButtonsTest {
    @Test fun oneTapMovesRepeatOn() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeat(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeat(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeat(Player.REPEAT_MODE_ONE))
    }

    @Test fun eachRepeatStateSaysWhatItIs() {
        assertEquals("Repeat off", repeatLabel(Player.REPEAT_MODE_OFF))
        assertEquals("Repeat all", repeatLabel(Player.REPEAT_MODE_ALL))
        assertEquals("Repeat this song", repeatLabel(Player.REPEAT_MODE_ONE))
    }

    @Test fun shuffleIsNoLongerBesideTheControls() {
        val ui = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui")).first { it.isDirectory }
        assertFalse(File(ui, "PlayerScreen.kt").readText().contains("Icons.Rounded.Shuffle"))
    }
}
