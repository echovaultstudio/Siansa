package app.siansa.ui

import app.siansa.data.Book
import app.siansa.data.BookProgress
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's books carousel: started books first with Continue, then the series' next, then new (issue #521). */
class BooksCarouselTest {
    private fun book(id: String, podcast: Boolean = false) = Book(id, "B$id", podcast = podcast, durationSec = 1000)
    private fun progress(id: String, sec: Double, finished: Boolean = false) = BookProgress(id, sec, 1000.0, sec / 1000, finished)

    @Test fun startedBooksComeFirstWithContinue() {
        val shelved = listOf("recently-added" to book("1"), "continue-listening" to book("2"), "continue-series" to book("3"), "recently-added" to book("4", podcast = true))
        val progress = mapOf("2" to progress("2", 400.0), "4" to progress("4", 100.0))
        val cards = booksForCarousel(shelved, progress)
        assertEquals(listOf("Continue" to "2", "Pick up" to "4", "Up next" to "3", "New" to "1"), cards.map { it.first to it.second.id })
    }

    @Test fun finishedBooksAndRepeatsStayOut() {
        val shelved = listOf("continue-listening" to book("1"), "recently-added" to book("1"), "recently-added" to book("2"))
        val cards = booksForCarousel(shelved, mapOf("2" to progress("2", 1000.0, finished = true)))
        assertEquals(listOf("New" to "1"), cards.map { it.first to it.second.id })
    }

    @Test fun atMostFiveCards() {
        val shelved = (1..9).map { "recently-added" to book("$it") }
        val cards = booksForCarousel(shelved, emptyMap())
        assertEquals(listOf("1", "2", "3", "4", "5"), cards.map { it.second.id })
    }

    @Test fun theTitleBarTurnsSolidAsTheCoverScrollsAway() {
        assertEquals(0f, titleBarAlpha(0f, 200f))
        assertEquals(0f, titleBarAlpha(160f, 200f))
        assertEquals(0.5f, titleBarAlpha(190f, 200f), 0.001f)
        assertEquals(1f, titleBarAlpha(400f, 200f))
    }

    @Test fun descriptionsLoseTheirHtml() {
        assertEquals("A lighthouse & a lamp.\nLine two", stripHtml("<p>A lighthouse &amp; a lamp.<br/>Line two</p>"))
    }
}
