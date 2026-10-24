package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #434: with the lyrics button on, now playing has no lyrics card below the player. */
class LyricsCardTest {
    @Test fun theLyricsCardOnlyShowsWithoutTheButton() {
        assertEquals(false, showsLyricsCard(lyricsButton = true))
        assertEquals(true, showsLyricsCard(lyricsButton = false))
    }
}
