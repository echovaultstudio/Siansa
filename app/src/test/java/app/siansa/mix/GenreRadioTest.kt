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

    /** The owner, 12 Nov: the picks prefer the songs the sound is sure of; without scores every song is alike. */
    @Test fun sureSongsComeUpFarMoreOften() {
        val ids = genre.map { it.id }
        // Half the genre sure (0.6), half faint (0.15): over many radios the sure half is picked about 16 times as often.
        val weights = ids.mapIndexed { i, id -> id to if (i % 2 == 0) 0.6f else 0.15f }.toMap()
        var sure = 0
        var faint = 0
        repeat(200) { round ->
            GenreRadio.weightedPicks(ids, weights, GenreRadio.PICKS, Random(round)).forEach { if (weights[it]!! >= 0.4f) sure++ else faint++ }
        }
        assertTrue("sure $sure vs faint $faint", sure > faint * 8)
        assertEquals(GenreRadio.PICKS, GenreRadio.weightedPicks(ids, weights, GenreRadio.PICKS, Random(9)).toSet().size)
        // No scores: as likely as each other, give or take.
        var low = 0
        repeat(200) { round -> GenreRadio.weightedPicks(ids, emptyMap(), GenreRadio.PICKS, Random(round)).forEach { if (it.removePrefix("s").toInt() <= 500) low++ } }
        assertTrue("low $low of 6000", low in 2500..3500)
        assertEquals(GenreRadio.weightOf(0.6f) / GenreRadio.weightOf(0.15f), 16.0, 0.01)
        assertEquals("", GenreRadio.scoresLine(ids.take(3), emptyMap()))
        assertEquals(" · scores 0.15 to 0.60, 1 sure", GenreRadio.scoresLine(ids.take(2), weights))
    }

    @Test fun aShortGenreQueuesWhatItHas() {
        assertEquals(5, GenreRadio.firstPicks(genre.take(5), null, GenreRadio.PICKS, Random(4)).size)
    }
}
