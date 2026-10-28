package app.siansa.ui

import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.net.Episode
import app.siansa.playback.BookPlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A book's or podcast's page (issue #626): where you are, measured against the right length, and the details line. */
class BookPageTest {
    private val podcast = Book("p", "The Dozen", authors = listOf("The Dozen Media"), podcast = true, genres = listOf("Society"))
    private val book = Book("b", "Leviathan Wakes", authors = listOf("James S. A. Corey"), narrators = listOf("Jefferson Mays"), series = "The Expanse", sequence = "1", year = 2011, durationSec = 72_000)
    private val ep = Episode("e1", "p", "Naomi Campbell Was My WORST Client", null, 0L, 5_163, null, null)
    private val idle = BookPlayerState()

    @Test fun aPodcastIsMeasuredAgainstItsEpisodeNotZero() {
        // The page showed "47 min of 0 min · 0 % · 0 min left" with a full bar: a podcast has no length of its own.
        val saved = BookProgress("p", currentSec = 2_823.0, durationSec = 5_163.0, fraction = 0.55, episodeId = "e1")
        val p = bookPosition(podcast, idle, saved, listOf(ep))!!
        assertEquals("Naomi Campbell Was My WORST Client", p.episodeTitle)
        assertEquals(5_163.0, p.totalSec, 0.1)
        assertEquals(0.547f, p.fraction, 0.01f)
        assertEquals("47 min of 1 h 26 min · 54 % · 39 min left", p.line)
    }

    @Test fun whilePlayingTheBarFollowsThePlayer() {
        val asPlayed = podcast.copy(title = ep.title, subtitle = podcast.title, durationSec = ep.durationSec)
        val now = BookPlayerState(book = asPlayed, positionSec = 3_000.0, playing = true)
        val later = now.copy(positionSec = 3_600.0)
        val a = bookPosition(podcast, now, null, listOf(ep))!!
        val b = bookPosition(podcast, later, null, listOf(ep))!!
        assertEquals("e1", a.episodeId)
        assertTrue("the bar moves as it plays", b.fraction > a.fraction)
    }

    @Test fun aBookUsesItsOwnLength() {
        val p = bookPosition(book, idle, BookProgress("b", currentSec = 36_000.0, durationSec = 72_000.0, fraction = 0.5), emptyList())!!
        assertEquals(0.5f, p.fraction, 0.001f)
        assertNull(p.episodeTitle)
    }

    @Test fun nothingToShowBeforeStartingOrWithoutALength() {
        assertNull(bookPosition(book, idle, null, emptyList()))
        assertNull(bookPosition(podcast, idle, BookProgress("p", currentSec = 100.0), emptyList()))  // no length known
    }

    @Test fun aFinishedBookShowsAsFinished() {
        val p = bookPosition(book, idle, BookProgress("b", currentSec = 72_000.0, durationSec = 72_000.0, fraction = 1.0, finished = true), emptyList())!!
        assertTrue(p.finished)
    }

    @Test fun theDetailsReadLikeAnAlbumsLine() {
        assertEquals("Podcast · Society · 5 episodes", bookDetails(podcast, 5, finished = false))
        assertEquals("Podcast · Society · 1 episode", bookDetails(podcast, 1, finished = false))
        assertEquals("Audiobook · read by Jefferson Mays · The Expanse, book 1 · 2011 · 20 h", bookDetails(book, null, finished = false))
        assertTrue(bookDetails(book, null, finished = true).endsWith("· Finished"))
    }

    @Test fun anEpisodeWithEmptySeasonAndNumberShowsNoLetters() {
        // Audiobookshelf sends "" for these; the list showed "S E · 22 Oct 2026".
        assertEquals("", episodeLine(Episode("e", "p", "t", null, 0L, 0, "", "")))
        assertEquals("E2570", episodeLine(Episode("e", "p", "t", null, 0L, 0, " ", "2570")))
    }
}
