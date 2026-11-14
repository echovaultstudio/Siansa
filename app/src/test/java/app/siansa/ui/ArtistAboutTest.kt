package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The line under an artist's name on their picture (the owner, 14 Nov). */
class ArtistAboutTest {
    @Test fun releasesAndSongs() {
        assertEquals("22 releases · 262 songs", aboutArtist(22, 262, null))
        assertEquals("1 release · 1 song", aboutArtist(1, 1, 5))
    }

    @Test fun anArtistWithNoReleasesSaysWhereTheyAre() {
        assertEquals("On 3 albums by others · 7 songs", aboutArtist(0, 7, 3))
        assertEquals("On 1 album by others · 1 song", aboutArtist(0, 1, 1))
        // Before Appears on is known, only the songs.
        assertEquals("7 songs", aboutArtist(0, 7, null))
        assertEquals("", aboutArtist(0, 0, 0))
    }
}
