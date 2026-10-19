package app.musix.ui

import app.musix.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Album pages always mark the song playing now (issue #228). */
class PlayingIndexTest {
    private val album = listOf(
        Song("a1", "Intro", album = "Night", track = 1, disc = 1),
        Song("a2", "Streets", album = "Night", track = 2, disc = 1),
        Song("a3", "Streets", album = "Night", track = 2, disc = 2),
    )

    @Test fun bySongId() {
        assertEquals(1, playingIndex(album, Song("a2", "Streets")))
    }

    @Test fun anotherCopyOfTheSameSong() {
        // The album is in two libraries; the page lists one copy and the other plays.
        val copy = Song("b3", "streets", album = "NIGHT", track = 2, disc = 2)
        assertEquals(2, playingIndex(album, copy))
        assertEquals(1, playingIndex(album, copy.copy(disc = null)))
    }

    @Test fun nothingWhenNotOnThisPage() {
        assertNull(playingIndex(album, null))
        assertNull(playingIndex(album, Song("x", "Streets", album = "Other", track = 2)))
        assertNull(playingIndex(album, Song("x", "Streets", album = "Night", track = 7)))
    }
}
