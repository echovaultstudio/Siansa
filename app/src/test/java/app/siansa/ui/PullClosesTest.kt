package app.siansa.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a panel over the player (the queue, the lyrics) closes as a pull down is let go. */
class PullClosesTest {
    @Test fun aFifthOfTheWayOrAQuickFlickCloses() {
        assertTrue(pullCloses(pulledPx = 500f, heightPx = 2000f, speed = 0f))
        assertFalse(pullCloses(pulledPx = 300f, heightPx = 2000f, speed = 0f))
        assertTrue(pullCloses(pulledPx = 60f, heightPx = 2000f, speed = 3000f))
        // A touch that hardly moved isn't a flick, however fast; nor is a slow short pull.
        assertFalse(pullCloses(pulledPx = 10f, heightPx = 2000f, speed = 3000f))
        assertFalse(pullCloses(pulledPx = 60f, heightPx = 2000f, speed = 500f))
    }
}
