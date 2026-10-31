package app.siansa

import app.siansa.ui.FULL_FADE
import app.siansa.ui.roomBelow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The cover's fade on album, playlist and mix pages (issues #682, #723). */
class CollectionFadeTest {
    private val cover = 600f
    private val full = cover * FULL_FADE

    @Test fun aShortPageGetsRoomToScrollItsCoverAway() {
        // A single: its rows let it scroll 200 px; it needs FULL_FADE covers.
        assertEquals(full - 200f, roomBelow(200f, cover), 0.01f)
        // Rows that don't fill the screen at all.
        assertEquals(full, roomBelow(0f, cover), 0.01f)
    }

    @Test fun aLongPageGetsNoRoom() {
        assertEquals(0f, roomBelow(full, cover), 0.01f)
        assertEquals(0f, roomBelow(5000f, cover), 0.01f)
    }

    @Test fun theLengthIsEstimatedBeforeTheEndShows() {
        // At the end: nothing unseen, what's left below the screen.
        assertEquals(300f, app.siansa.ui.estimatedMaxScroll(100f, 1900f, 1800f, 100f, 0, 60f), 0.01f)
        // Five more rows of 60 px not laid out yet: counted in, so nothing jumps when they appear.
        assertEquals(600f, app.siansa.ui.estimatedMaxScroll(100f, 1900f, 1800f, 100f, 5, 60f), 0.01f)
        // Everything fits: it can't scroll beyond where it is.
        assertEquals(0f, app.siansa.ui.estimatedMaxScroll(0f, 900f, 1800f, 100f, 0, 60f), 0.01f)
    }
}
