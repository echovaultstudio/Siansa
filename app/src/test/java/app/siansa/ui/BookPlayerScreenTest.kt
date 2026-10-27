package app.siansa.ui

import app.siansa.data.Book
import app.siansa.data.Chapter
import app.siansa.playback.BookPlayerState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The book player's seek bar (#542): what it spans. */
class BookPlayerScreenTest {
    private val book = Book(id = "b", title = "A book", durationSec = 3600, chapters = listOf(Chapter(0, "One", 0.0, 1000.0), Chapter(1, "Two", 1000.0, 3600.0)))

    @Test fun theBarSpansTheChapter() {
        val (start, length, into) = chapterWindow(BookPlayerState(book = book, positionSec = 1300.0))
        assertEquals(1000.0, start, 0.0)
        assertEquals(2600.0, length, 0.0)
        assertEquals(300.0, into, 0.0)
    }

    @Test fun withoutChaptersItSpansTheBook() {
        val (start, length, into) = chapterWindow(BookPlayerState(book = book.copy(chapters = emptyList()), positionSec = 42.0))
        assertEquals(0.0, start, 0.0)
        assertEquals(3600.0, length, 0.0)
        assertEquals(42.0, into, 0.0)
    }

    @Test fun nothingLoadedIsAtLeastASecondWide() {
        val (_, length, into) = chapterWindow(BookPlayerState(positionSec = 5.0))
        assertEquals(1.0, length, 0.0)
        assertEquals(1.0, into, 0.0)
    }

    @Test fun theSpeedStepsRoundAndReadsShort() {
        assertEquals(1.25f, nextSpeed(1f), 0.001f)
        assertEquals(0.5f, nextSpeed(3f), 0.001f)
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("1.5×", speedLabel(1.5f))
    }
}
