package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** How early a Cast device loads the next song (issue #689). */
class CastPreloadTest {
    @Test fun aNormalSongLoadsTheNextOneTwentySecondsBeforeItsEnd() {
        assertEquals(20.0, castPreloadSeconds(240), 0.0)
        assertEquals(20.0, castPreloadSeconds(41), 0.0)
    }

    @Test fun aShortSongLoadsItHalfwayButNotTooLate() {
        assertEquals(15.0, castPreloadSeconds(30), 0.0)
        assertEquals(5.0, castPreloadSeconds(6), 0.0)
    }

    @Test fun anUnknownLengthUsesTheUsualTime() {
        assertEquals(20.0, castPreloadSeconds(0), 0.0)
    }
}
