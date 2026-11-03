package app.siansa.mix

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An artist radio with AudioMuse-AI: songs that sound like the artist, with their own songs among them. */
class ArtistRadioMixTest {
    private fun own(n: Int) = (1..n).map { Song("o$it", "Own $it", artist = "Mat Zo") }
    private fun alike(vararg artists: String) = artists.mapIndexed { i, a -> Song("a$i", "Alike $i", artist = a) }

    @Test fun everyFourthSongIsTheArtistsOwn() {
        val songs = ArtistRadioMix.mix(own(5), alike("A", "B", "C", "D", "E", "F", "G", "H", "I"), emptySet(), 12)
        assertEquals(12, songs.size)
        assertEquals(listOf(3, 7, 11), songs.withIndex().filter { it.value.artist == "Mat Zo" }.map { it.index })
    }

    @Test fun whatsQueuedAndTheArtistsSongsFromAudioMuseArentRepeated() {
        val alike = listOf(Song("o1", "Own 1", artist = "Mat Zo"), Song("x", "X", artist = "A"), Song("y", "Y", artist = "B"))
        val songs = ArtistRadioMix.mix(own(3), alike, already = setOf("o2", "y"), size = 10)
        assertEquals(listOf("x", "o1", "o3"), songs.map { it.id })
    }

    @Test fun atMostThreeSongsOfAnyOtherArtist() {
        val songs = ArtistRadioMix.mix(emptyList(), alike("A", "A", "A", "A", "a", "B"), emptySet(), 10)
        assertEquals(4, songs.size)
        assertEquals(3, songs.count { it.artist.equals("A", ignoreCase = true) })
    }

    @Test fun theArtistsOwnSongsFillInWhenAudioMuseHasFew() {
        val songs = ArtistRadioMix.mix(own(6), alike("A"), emptySet(), 5)
        assertEquals(5, songs.size)
        assertTrue(songs.first().artist == "A")
        assertEquals(4, songs.count { it.artist == "Mat Zo" })
    }
}
