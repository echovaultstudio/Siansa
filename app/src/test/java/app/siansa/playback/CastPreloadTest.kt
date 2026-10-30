package app.siansa.playback

import org.junit.Assert.assertTrue
import org.junit.Test

/** How early a Cast device loads the next song (issue #689). */
class CastPreloadTest {
    @Test fun theNextSongLoadsWellBeforeTheCurrentOneEnds() {
        // Counted back from where the next song starts: long enough to fetch it over Wi-Fi, short enough not to
        // load songs that may yet be skipped or reordered.
        assertTrue(CAST_PRELOAD_SECONDS in 10.0..30.0)
    }
}
