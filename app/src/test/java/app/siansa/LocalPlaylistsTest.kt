package app.siansa

import app.siansa.data.LocalPlaylist
import app.siansa.data.LocalPlaylists
import app.siansa.data.PlaylistEditor
import app.siansa.data.Song
import app.siansa.data.toPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Playlists kept only on the phone, as the screens show them. */
class LocalPlaylistsTest {
    private val songs = listOf(
        Song("s1", "One", albumId = "a1", durationSec = 100),
        Song("s2", "Two", coverArt = "mf-2", durationSec = 50),
    )

    @Test
    fun phonePlaylistsAreToldApartByTheirId() {
        assertTrue(LocalPlaylists.isLocal("local-1234"))
        assertFalse(LocalPlaylists.isLocal("4f2a9c"))
    }

    @Test
    fun withoutAPictureTheFirstSongsCoverShows() {
        val p = LocalPlaylist("local-1", "Road trip", songs).toPlaylist()
        assertEquals("al-a1", p.coverArt)
        assertEquals(2, p.songCount)
        assertEquals(150, p.durationSec)
        assertFalse(p.readonly!!)
    }

    @Test
    fun itsOwnPictureComesFirst() {
        val p = LocalPlaylist("local-1", "Road trip", songs, picture = "/data/pic.jpg").toPlaylist()
        assertEquals(LocalPlaylists.FILE_COVER + "/data/pic.jpg", p.coverArt)
    }

    @Test
    fun anEmptyPhonePlaylistCanBeChanged() {
        val p = LocalPlaylist("local-1", "Empty").toPlaylist()
        assertEquals(null, p.coverArt)
        assertTrue(PlaylistEditor.canEdit(p, "max"))
    }

    @Test
    fun picturesKeepTheirTypeWhenMoved() {
        assertEquals("image/png", PlaylistEditor.mimeOf("local-1-99.png"))
        assertEquals("image/jpeg", PlaylistEditor.mimeOf("local-1-99.jpg"))
        assertEquals("image/jpeg", PlaylistEditor.mimeOf("noextension"))
    }
}
