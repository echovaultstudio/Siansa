package app.siansa.mix

import app.siansa.data.Album
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.Song
import app.siansa.net.Episode
import app.siansa.ui.coverRowsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cover rows (issue #782): who it's by, what it is and how long, beside its cover. */
class CoverRowsTest {
    private val book = Book(id = "b1", title = "Project Hail Mary", authors = listOf("Andy Weir"), durationSec = 16 * 3600 + 10 * 60)
    private val podcast = Book(id = "p1", title = "Rocket Hour", podcast = true)
    private val episode = Episode("e1", "p1", "Exploring the Dopamine Chamber", null, 2000L, 58 * 60, null, "479")

    @Test fun theDotsMarkTheColumnMostInView() {
        // Two columns of 860 px in a 1000 px row, 40 px at its edges and 30 between (the video in issue #782).
        assertEquals(0, CoverRows.columnInView(listOf(0 to (40 to 860), 1 to (930 to 860)), 0, 1000))
        // Swiped to the end: the last column can't reach the left edge, but it is the one in view.
        assertEquals(1, CoverRows.columnInView(listOf(0 to (-790 to 860), 1 to (100 to 860)), 0, 1000))
        // Halfway, the one past the middle; on a tie, the earlier.
        assertEquals(1, CoverRows.columnInView(listOf(0 to (-500 to 860), 1 to (390 to 860)), 0, 1000))
        assertEquals(0, CoverRows.columnInView(listOf(0 to (-360 to 860), 1 to (500 to 860)), 0, 1000))
        assertEquals(2, CoverRows.columnInView(listOf(1 to (-800 to 860), 2 to (90 to 860), 3 to (980 to 860)), 0, 1000))
        assertEquals(0, CoverRows.columnInView(emptyList(), 0, 1000))
    }

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

    /** Review of #783: an episode heard before isn't "new", whatever episode of its podcast was played last. */
    @Test fun anEpisodeUsesItsOwnProgress() {
        val heard = Episode("e478", "p1", "Last week's", null, 1000L, 50 * 60, null, "478")
        val all = listOf(
            BookProgress("p1", currentSec = 3000.0, durationSec = 3000.0, finished = true, updatedAt = 100, episodeId = "e478"),
            BookProgress("p1", currentSec = 1200.0, durationSec = 3480.0, updatedAt = 200, episodeId = "e1"),
            BookProgress("b1", currentSec = 10.0, updatedAt = 50),
        )
        val byEpisode = episodeProgressById(all)
        assertEquals(setOf("e478", "e1"), byEpisode.keys)
        // The podcast's own progress is only its latest episode's (e1), so before, e478 said "new".
        val latest = mapOf("p1" to all[1])
        val content = CardContent(emptyList(), emptyList(), episodes = listOf(podcast to heard, podcast to episode), episodeProgress = byEpisode)
        val card = PinnedCard.new("Episodes").copy(contents = CardContents.EPISODES, look = CardLook.COVER_ROWS)
        val rows = coverRowsOf(card, content, latest)
        assertEquals("50 min · finished", rows[0].meta)
        assertEquals("58 min · 38 min left", rows[1].meta)
        // Without per-episode progress, the podcast's latest still counts for its own episode.
        assertEquals("58 min · 38 min left", coverRowsOf(card, content.copy(episodeProgress = emptyMap()), latest)[1].meta)
        assertEquals("50 min · new", coverRowsOf(card, content.copy(episodeProgress = emptyMap()), latest)[0].meta)
    }

    /** Two episodes with one title: only the one the player has, by id, is playing. */
    @Test fun theEpisodePlayingIsKnownByItsId() {
        assertTrue(app.siansa.ui.isEpisodePlaying("p1", "e1", "p1", "e1"))
        assertFalse(app.siansa.ui.isEpisodePlaying("p1", "e2", "p1", "e1"))
        assertFalse(app.siansa.ui.isEpisodePlaying("p2", "e1", "p1", "e1"))
        assertFalse(app.siansa.ui.isEpisodePlaying("p1", null, "p1", "e1"))
    }

    /** Keep listening holds at most "How many" in all, the most recent first, books and episodes together. */
    @Test fun keepListeningCountsBooksAndEpisodesTogether() {
        val books = (1..4).map { Book(id = "b$it", title = "Book $it") }
        val eps = (1..4).map { podcast to Episode("e$it", "p1", "Episode $it", null, 0L, 600, null, null) }
        val at = mapOf("b1" to 10L, "b2" to 50L, "b3" to 30L, "b4" to 5L, "e1" to 60L, "e2" to 20L, "e3" to 40L, "e4" to 1L)
        val (keptBooks, keptEps) = keepListening(books, eps, 4) { item ->
            when (item) { is Book -> at[item.id] ?: 0L; is Episode -> at[item.id] ?: 0L; else -> 0L }
        }
        assertEquals(4, keptBooks.size + keptEps.size)
        assertEquals(listOf("b2", "b3"), keptBooks.map { it.id })
        assertEquals(listOf("e1", "e3"), keptEps.map { it.second.id })
    }

    /** A card of books or episodes is kept for the day only when it loaded whole; Keep listening never. */
    @Test fun onlyAWholeLoadIsKeptForTheDay() {
        assertTrue(bookCardKeptForTheDay(CardContents.EPISODES, whole = true, empty = false))
        assertFalse(bookCardKeptForTheDay(CardContents.EPISODES, whole = false, empty = false))  // a podcast failed
        assertFalse(bookCardKeptForTheDay(CardContents.BOOKS, whole = true, empty = true))  // nothing came back
        assertFalse(bookCardKeptForTheDay(CardContents.LISTENING, whole = true, empty = false))  // follows what you play
    }
}
