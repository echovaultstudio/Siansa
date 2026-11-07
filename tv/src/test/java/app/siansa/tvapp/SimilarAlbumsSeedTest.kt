package app.siansa.tvapp

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The TV's similar albums asks AudioMuse through a song of the album, not the album's id (issue #967). */
class SimilarAlbumsSeedTest {
    @Test
    fun throughTheAlbumsFirstSong() {
        val songs = listOf(Song(id = "song-1", title = "One"), Song(id = "song-2", title = "Two"))
        assertEquals("song-1", similarAlbumsSeed(songs))
    }

    @Test
    fun noneWithoutSongs() {
        assertNull(similarAlbumsSeed(emptyList()))
        assertNull(similarAlbumsSeed(listOf(Song(id = "", title = "No id"))))
    }
}
