package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Songs already in a playlist (issue #1235): the badge's number, the new ones, and the question asked. */
class PlaylistDuplicatesTest {
    private fun song(id: String) = Song(id = id, title = "Song $id")

    @Test fun oneSongCountsHowManyTimesItsThere() {
        assertEquals(0, PlaylistDuplicates.count(listOf("a", "b"), listOf(song("c"))))
        assertEquals(1, PlaylistDuplicates.count(listOf("a", "b"), listOf(song("b"))))
        assertEquals(3, PlaylistDuplicates.count(listOf("b", "a", "b", "b"), listOf(song("b"))))
        assertEquals("This song is in it once already.", PlaylistDuplicates.question(listOf("b"), listOf(song("b"))))
        assertEquals("This song is in it twice already.", PlaylistDuplicates.question(listOf("b", "b"), listOf(song("b"))))
        assertEquals("This song is in it 3 times already.", PlaylistDuplicates.question(listOf("b", "b", "b"), listOf(song("b"))))
        assertEquals(0, PlaylistDuplicates.count(emptyList(), listOf(song("b"))))
    }

    @Test fun severalSongsOfferOnlyTheNewOnes() {
        val adding = listOf(song("a"), song("b"), song("c"), song("d"))
        val there = listOf("x", "b", "d", "b")
        assertEquals(3, PlaylistDuplicates.count(there, adding))
        assertEquals(listOf("a", "c"), PlaylistDuplicates.missing(there, adding).map { it.id })
        assertEquals("2 of 4 songs are in it already.", PlaylistDuplicates.question(there, adding))
        assertEquals("All 2 songs are in it already.", PlaylistDuplicates.question(listOf("a", "b"), listOf(song("a"), song("b"))))
        assertEquals(emptyList<Song>(), PlaylistDuplicates.missing(listOf("a", "b"), listOf(song("a"), song("b"))))
    }
}
