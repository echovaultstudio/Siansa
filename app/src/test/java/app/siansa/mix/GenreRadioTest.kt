package app.siansa.mix

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A genre's radio: some of its songs at random, more as they play, never the whole genre (the owner, 10 Nov). */
class GenreRadioTest {
    private val genre = (1..1000).map { Song(id = "s$it", title = "Song $it", artist = "x") }

    @Test fun startsWithThirtyAtRandomAndTheTappedSongFirst() {
        val tapped = genre[500]
        val picks = GenreRadio.firstPicks(genre, tapped, GenreRadio.PICKS, Random(1))
        assertEquals(GenreRadio.PICKS, picks.size)
        assertEquals(tapped, picks.first())
        assertEquals(picks.size, picks.map { it.id }.toSet().size)
        assertEquals(GenreRadio.PICKS, GenreRadio.firstPicks(genre, null, GenreRadio.PICKS, Random(1)).size)
    }

    @Test fun moreAreNewSongsOfTheGenre() {
        val used = genre.take(30).map { it.id }.toMutableSet()
        val next = GenreRadio.nextPicks(genre.map { it.id }, used, GenreRadio.PICKS, Random(2))
        assertEquals(GenreRadio.PICKS, next.size)
        assertTrue(next.none { it in used })
    }

    @Test fun aSmallGenreStartsOverWithoutTheLastPlayed() {
        val small = (1..10).map { "s$it" }
        val used = LinkedHashSet(small)
        val next = GenreRadio.nextPicks(small, used, GenreRadio.PICKS, Random(3))
        assertTrue(next.isNotEmpty())
        assertTrue("the last played don't come straight back", next.none { it == "s10" })
    }

    @Test fun aShortGenreQueuesWhatItHas() {
        assertEquals(5, GenreRadio.firstPicks(genre.take(5), null, GenreRadio.PICKS, Random(4)).size)
    }
}
