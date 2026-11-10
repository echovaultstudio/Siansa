package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's shortcut to a radio shows the radio's own cover, in its colours, not one of its songs' (the owner, 10 Nov). */
class ShortcutCoverTest {
    @Test fun aGenreRadioHasItsPagesColours() {
        assertEquals(genreColors("Halftime"), generatedCoverColors("radio/genre/Halftime"))
        assertEquals(genreColors("Liquid Funk"), generatedCoverColors("radio/genre/Liquid%20Funk"))
    }

    @Test fun aMoodRadioHasItsMoodsColours() {
        val mood = moodRadios.first()
        assertEquals(Color(mood.colors.first) to Color(mood.colors.second), generatedCoverColors("radio/mood/${mood.id}"))
    }

    @Test fun otherPagesKeepTheirCovers() {
        assertNull(generatedCoverColors("album/123"))
        assertNull(generatedCoverColors("playlist/abc"))
        assertNull(generatedCoverColors("liked"))
    }
}
