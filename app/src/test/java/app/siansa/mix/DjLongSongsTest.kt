package app.siansa.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjLongSongsTest {
    @Test
    fun `an ordinary song and a long extended mix fit`() {
        assertTrue(fitsDjMix(3 * 60 + 30))
        assertTrue(fitsDjMix(11 * 60))
        assertTrue(fitsDjMix(DJ_LONGEST_SEC))
    }

    @Test
    fun `a continuous mix of over an hour doesn't`() {
        assertFalse(fitsDjMix(72 * 60 + 27))
        assertFalse(fitsDjMix(DJ_LONGEST_SEC + 1))
    }

    @Test
    fun `a song whose length isn't known fits`() {
        assertTrue(fitsDjMix(0))
    }

    private fun song(id: String, seconds: Int) = app.siansa.data.Song(id = id, title = id, artist = "A", album = "B", durationSec = seconds)

    @Test
    fun `an album's DJ mix leaves its continuous mixes out`() {
        val album = listOf(song("one", 400), song("two", 380), song("continuous", 4347))
        assertEquals(listOf("one", "two"), djMixSongs(album).map { it.id })
    }

    @Test
    fun `an album of only long songs keeps them all`() {
        val album = listOf(song("mix 1", 4347), song("mix 2", 4100))
        assertEquals(album, djMixSongs(album))
    }

    @Test
    fun `the album page knows its DJ mix without the long songs`() {
        val album = listOf(song("one", 400), song("two", 380), song("continuous", 4347))
        val mix = listOf("two", "one")
        assertTrue(albumMixPlaying(album.filter { it.id in mix }.sortedBy { mix.indexOf(it.id) }, album, mix))
    }
}
