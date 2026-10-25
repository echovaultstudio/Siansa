package app.siansa.mix

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Daily mixes from the music shown, without AudioMuse (beta #25). */
class LibraryMixTest {
    private fun song(id: String, artist: String, genre: String?) = Song(id = id, title = id, artist = artist, genre = genre)

    @Test fun vibeWordsLeaveOutCommonWords() {
        assertEquals(setOf("soft", "warm", "folk", "acoustic", "guitar"), LibraryMix.words("Soft warm folk music with acoustic guitar, male vocals"))
        assertEquals(emptySet<String>(), LibraryMix.words(null))
    }

    @Test fun genresTheVibeNamesComeFirst() {
        val vibe = LibraryMix.words("Mellow folk rock with acoustic guitar")
        assertEquals(2, LibraryMix.fit(song("a", "x", "Folk Rock"), vibe, emptySet()))
        assertEquals(1, LibraryMix.fit(song("b", "x", "Children's Music"), vibe, setOf("children's music")))
        assertEquals(0, LibraryMix.fit(song("c", "x", "Techno"), vibe, emptySet()))
        assertEquals(0, LibraryMix.fit(song("d", "x", null), vibe, emptySet()))
    }

    @Test fun picksFittingSongsVariedByArtist() {
        val folk = (1..10).map { song("f$it", "folk${it % 5}", "Folk") }
        val techno = (1..20).map { song("t$it", "techno$it", "Techno") }
        val picked = LibraryMix.pick(folk + techno, "warm folk evening", emptyList(), length = 8, random = Random(1))
        assertEquals(8, picked.size)
        // Every folk artist has at most two songs, so the folk ones come first, then the rest.
        assertTrue(picked.take(8).count { it.genre == "Folk" } == 8)
        assertTrue(picked.groupingBy { it.artist }.eachCount().values.all { it <= 2 })
    }

    @Test fun recentSongsOnlyWhenNothingElseIsLeft() {
        val songs = (1..6).map { song("s$it", "a$it", "Pop") }
        val picked = LibraryMix.pick(songs, "pop", emptyList(), length = 4, avoid = setOf("s1", "s2", "s3"), random = Random(2))
        assertEquals(4, picked.size)
        assertEquals(3, picked.count { it.id !in setOf("s1", "s2", "s3") })
        assertTrue(picked.take(3).none { it.id in setOf("s1", "s2", "s3") })
    }

    @Test fun anotherMixesSongsNeverReturn() {
        val songs = (1..5).map { song("s$it", "a$it", null) }
        val picked = LibraryMix.pick(songs, "", emptyList(), length = 10, exclude = setOf("s1"), random = Random(3))
        assertEquals(4, picked.size)
        assertTrue(picked.none { it.id == "s1" })
    }

    @Test fun aSmallLibraryOfFewArtistsStillFillsTheMix() {
        // kids: 949 songs by a handful of artists.
        val songs = (1..60).map { song("k$it", "artist${it % 3}", "Children's Music") }
        val picked = LibraryMix.pick(songs, "Soft warm ambient folk", listOf("Children's Music"), length = 30, random = Random(4))
        assertEquals(30, picked.size)
    }

    @Test fun nothingAskedNothingPicked() {
        assertEquals(emptyList<Song>(), LibraryMix.pick(listOf(song("a", "x", "Pop")), "pop", emptyList(), length = 0))
    }
}
