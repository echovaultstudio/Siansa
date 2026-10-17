package app.musix

import app.musix.data.DiscHeader
import app.musix.data.Song
import app.musix.data.discHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Headings above each disc of an album page. */
class DiscHeadersTest {
    private fun song(disc: Int?, track: Int) = Song(id = "$disc-$track", title = "Song $track", track = track, disc = disc)

    @Test fun singleDiscHasNoHeadings() {
        assertTrue(discHeaders(listOf(song(1, 1), song(1, 2)), emptyMap()).isEmpty())
        assertTrue(discHeaders(listOf(song(null, 1), song(null, 2)), emptyMap()).isEmpty())
    }

    @Test fun discsWithoutTitlesAreNumbered() {
        val songs = listOf(song(1, 1), song(1, 2), song(2, 1), song(2, 2), song(3, 1))
        assertEquals(
            mapOf(0 to DiscHeader(1, "Disc 1"), 2 to DiscHeader(2, "Disc 2"), 4 to DiscHeader(3, "Disc 3")),
            discHeaders(songs, emptyMap()),
        )
    }

    @Test fun discTitlesAreUsedWhereTheyExist() {
        val songs = listOf(song(1, 1), song(2, 1), song(3, 1))
        val headers = discHeaders(songs, mapOf(1 to "The Early Years", 2 to "  ", 3 to " Live "))
        assertEquals("The Early Years", headers[0]?.label)
        assertEquals("Disc 2", headers[1]?.label)
        assertEquals("Live", headers[2]?.label)
    }

    @Test fun singleDiscWithTitleGetsItsHeading() {
        assertEquals(mapOf(0 to DiscHeader(1, "Bonus")), discHeaders(listOf(song(1, 1), song(1, 2)), mapOf(1 to "Bonus")))
    }

    @Test fun songsWithoutDiscNumberCountAsDiscOne() {
        val headers = discHeaders(listOf(song(null, 1), song(2, 1)), emptyMap())
        assertEquals(mapOf(0 to DiscHeader(1, "Disc 1"), 1 to DiscHeader(2, "Disc 2")), headers)
    }

    @Test fun emptyAlbumHasNoHeadings() {
        assertTrue(discHeaders(emptyList(), mapOf(1 to "Bonus")).isEmpty())
    }
}
