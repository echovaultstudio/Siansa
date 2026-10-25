package app.siansa.ui

import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta #18: a playlist the server rewrites (every hour) shows its new songs when opened. */
class PlaylistCheckTest {
    private fun list(name: String, vararg ids: String) =
        PlaylistDetail(Playlist(id = "p1", name = name), ids.map { Song(id = it, title = it) })

    @Test fun newSongsOrderOrNameCountAsAChange() {
        assertTrue(playlistChanged(list("Hourly", "a", "b"), list("Hourly", "a", "b", "c")))
        assertTrue(playlistChanged(list("Hourly", "a", "b"), list("Hourly", "b", "a")))
        assertTrue(playlistChanged(list("Hourly", "a"), list("Hourly mix", "a")))
    }

    @Test fun theSameSongsAreNoChange() {
        assertFalse(playlistChanged(list("Hourly", "a", "b"), list("Hourly", "a", "b")))
    }
}
