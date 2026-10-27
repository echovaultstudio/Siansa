package app.siansa.playback

import app.siansa.ui.SPEEDS
import app.siansa.ui.nextSpeed
import app.siansa.ui.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** A book's files play as one: a position in the whole book maps to a file and a place in it (issue #521). */
class BookSessionTest {
    // Three files: 0–600, 600–1500, 1500–2000 s.
    private val tracks = listOf(0.0 to 600.0, 600.0 to 900.0, 1500.0 to 500.0)

    @Test fun aPositionFindsItsFile() {
        assertEquals(0 to 0.0, BookSession.locate(tracks, 0.0))
        assertEquals(0 to 599.0, BookSession.locate(tracks, 599.0))
        assertEquals(1 to 0.0, BookSession.locate(tracks, 600.0))
        assertEquals(1 to 300.0, BookSession.locate(tracks, 900.0))
        assertEquals(2 to 100.0, BookSession.locate(tracks, 1600.0))
    }

    @Test fun pastTheEndsStaysInside() {
        assertEquals(2 to 500.0, BookSession.locate(tracks, 9999.0))
        assertEquals(0 to 0.0, BookSession.locate(tracks, -5.0))
        assertEquals(0 to 0.0, BookSession.locate(emptyList(), 10.0))
    }

    @Test fun speedsStepAroundAndReadWell() {
        assertEquals(1.25f, nextSpeed(1f), 0.001f)
        assertEquals(0.5f, nextSpeed(3f), 0.001f)
        assertEquals(0.75f, nextSpeed(0.5f), 0.001f)
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("0.75×", speedLabel(0.75f))
        assertEquals(SPEEDS.size, SPEEDS.distinct().size)
    }

    /** ▶ on a card or page (#553): the loaded book plays or pauses, any other starts. */
    @Test fun aTapOnTheLoadedBookTogglesAndOnAnotherStarts() {
        assertEquals(BookTap.TOGGLE, BookPlayer.bookTap("b1", "b1"))
        assertEquals(BookTap.START, BookPlayer.bookTap("b1", "b2"))
        assertEquals(BookTap.START, BookPlayer.bookTap(null, "b2"))
    }
}
