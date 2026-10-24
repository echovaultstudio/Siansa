package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Made for you tiles show only the title and the track count (issue #338). */
class MadeForYouCaptionTest {
    @Test fun aMadeMixShowsOnlyItsTrackCount() {
        assertEquals("25 tracks", madeForYouCaption(25, aiDown = false, prompt = "Tap to make it"))
        assertEquals("1 track", madeForYouCaption(1, aiDown = false, prompt = "Tap to make it"))
    }

    @Test fun aMadeMixStillShowsItsCountWhileAudioMuseIsDown() {
        assertEquals("30 tracks", madeForYouCaption(30, aiDown = true, prompt = "Tap to make it"))
    }

    @Test fun beforeItsMadeTheTileSaysWhatATapDoesOrWhyItCant() {
        assertEquals("Tap to make it", madeForYouCaption(null, aiDown = false, prompt = "Tap to make it"))
        assertEquals("AI backend not available :(", madeForYouCaption(null, aiDown = true, prompt = "Tap to make it"))
    }
}
