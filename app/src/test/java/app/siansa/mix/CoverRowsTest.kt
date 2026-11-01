package app.siansa.mix

import app.siansa.data.Album
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.Song
import app.siansa.net.Episode
import app.siansa.ui.coverRowsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cover rows (issue #782): who it's by, what it is and how long, beside its cover. */
class CoverRowsTest {
    private val book = Book(id = "b1", title = "Project Hail Mary", authors = listOf("Andy Weir"), durationSec = 16 * 3600 + 10 * 60)
    private val podcast = Book(id = "p1", title = "Rocket Hour", podcast = true)
    private val episode = Episode("e1", "p1", "Exploring the Dopamine Chamber", null, 2000L, 58 * 60, null, "479")

    @Test fun anAlbumSaysItsArtistSongsAndYear() {
        val row = CoverRows.album(Album(id = "a1", name = "Romance", artist = "Fontaines D.C.", songCount = 11, year = 2024))
        assertEquals("Fontaines D.C.", row.small)
        assertEquals("Romance", row.title)
        assertEquals("11 songs · 2024", row.meta)
        assertEquals("1 song", CoverRows.songCount(1))
        assertNull(CoverRows.songCount(0))
    }

    @Test fun aSongSaysItsAlbumAndLength() {
        val row = CoverRows.song(Song(id = "s1", title = "Animal", artist = "Katseye", album = "Beautiful Chaos", durationSec = 221))
        assertEquals("Beautiful Chaos · 3:41", row.meta)
    }

    @Test fun aBookSaysHowLongAndHowMuchIsLeft() {
        assertEquals("16 h 10 min", CoverRows.book(book, null).meta)
        val started = BookProgress("b1", currentSec = 10 * 3600.0, durationSec = book.durationSec.toDouble(), fraction = 0.62, updatedAt = 5)
        val row = CoverRows.book(book, started)
        assertEquals("16 h 10 min · 6 h 10 min left", row.meta)
        assertEquals(0.62f, row.progress!!, 0.001f)
        val done = CoverRows.book(book, started.copy(finished = true))
        assertEquals("16 h 10 min · finished", done.meta)
        assertNull("no bar once finished", done.progress)
    }

    @Test fun anEpisodeSaysItsNumberAndWhetherItsNew() {
        val row = CoverRows.episode(podcast, episode, null)
        assertEquals("Rocket Hour", row.small)
        assertEquals("Ep. 479 · Exploring the Dopamine Chamber", row.title)
        assertEquals("58 min · new", row.meta)
        assertEquals("Live from New York", CoverRows.episodeTitle(episode.copy(title = "Live from New York", number = " ")))
    }

    @Test fun rowsComeInColumnsOfThree() {
        val rows = (1..7).map { CoverRows.song(Song(id = "s$it", title = "Song $it")) }
        assertEquals(listOf(3, 3, 1), CoverRows.columns(rows).map { it.size })
    }

    @Test fun keepListeningPutsTheMostRecentFirst() {
        val card = PinnedCard.new("Keep listening").copy(contents = CardContents.LISTENING, look = CardLook.COVER_ROWS)
        val content = CardContent(emptyList(), emptyList(), books = listOf(book), episodes = listOf(podcast to episode))
        val progress = mapOf(
            "b1" to BookProgress("b1", currentSec = 1.0, updatedAt = 100),
            "p1" to BookProgress("p1", currentSec = 1.0, updatedAt = 200, episodeId = "e1"),
        )
        assertEquals(listOf("episode-e1", "book-b1"), coverRowsOf(card, content, progress).map { it.key })
    }

    @Test fun booksNeedAudiobookshelfAndTheirOwnLook() {
        val card = PinnedCard.new("Audiobooks").copy(contents = CardContents.BOOKS, look = CardLook.COVER_ROWS)
        assertNull("no rules needed", pinnedCardProblem(card, audioMuseReady = false, booksReady = true))
        assertEquals("Set up Audiobookshelf in Settings › Servers and services first.", pinnedCardProblem(card, false, booksReady = false))
        assertEquals("Books and episodes show as Cover rows.", pinnedCardProblem(card.copy(look = CardLook.TILES), false, booksReady = true))
        assertEquals("Audiobooks · Recently added", card.copy(sort = CardSort.ADDED).detail)
    }
}
