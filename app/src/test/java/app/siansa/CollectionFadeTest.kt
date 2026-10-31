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
        // Rows that just fill the screen.
        assertEquals(full, roomBelow(0f, cover), 0.01f)
        // Rows that end 800 px above the screen's end (a single on a tall phone, issue #738): that much more, or it
        // still can't scroll at all.
        assertEquals(full + 800f, roomBelow(-800f, cover), 0.01f)
    }

    @Test fun aLongPageGetsNoRoom() {
        assertEquals(0f, roomBelow(full, cover), 0.01f)
        assertEquals(0f, roomBelow(5000f, cover), 0.01f)
    }

    @Test fun howFarTheRowsReachPastTheScreen() {
        // Scrolled 100 px, the last row ends 100 px below the screen, and 100 px of padding: 300 px.
        assertEquals(300f, app.siansa.ui.rowsPastScreen(100f, 1900f, 1800f, 100f), 0.01f)
        // Rows and padding end 800 px before the screen does: below zero, so the room fills that first.
        assertEquals(-800f, app.siansa.ui.rowsPastScreen(0f, 900f, 1800f, 100f), 0.01f)
    }

    @Test fun aSingleOnATallPhoneCanScrollItsCoverAway() {
        // With the room, the page scrolls exactly FULL_FADE covers, however short its rows.
        val past = app.siansa.ui.rowsPastScreen(0f, 900f, 1800f, 100f)
        val room = roomBelow(past, cover)
        assertEquals(full, past + room, 0.01f)
    }
}
