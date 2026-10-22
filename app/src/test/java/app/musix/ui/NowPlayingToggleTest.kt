package app.musix.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** The song playing now in the queue and on album pages: no "Now playing" label, its cover pauses and plays (issue #345). */
class NowPlayingToggleTest {
    @Test fun theToggleSaysWhatATapDoes() {
        assertEquals("Pause", playPauseLabel(isPlaying = true))
        assertEquals("Play", playPauseLabel(isPlaying = false))
    }

    @Test fun noNowPlayingLabelInLists() {
        val ui = listOf(File("src/main/java/app/musix/ui"), File("app/src/main/java/app/musix/ui")).first { it.isDirectory }
        for (name in listOf("PlayerScreen.kt", "CollectionPage.kt")) {
            val code = File(ui, name).readText()
            assertFalse("$name still writes \"Now playing\" under the song playing now", code.contains("listOfNotNull(\"Now playing\""))
        }
    }
}
