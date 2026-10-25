package app.siansa

import app.siansa.data.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

/** Playlists and the source filter: whose playlists show, and which can take which songs. */
class PlaylistRulesTest {
    @Test fun aPlaylistOnlyTakesItsOwnUsersSongs() {
        // Max (0) in use, Kids (1) shown: Max's songs can't go into Kids' playlist, nor Kids' into Max's.
        assertEquals(true, app.siansa.data.PlaylistEditor.takes(playlistOwner = 0, songOwners = setOf(0)))
        assertEquals(false, app.siansa.data.PlaylistEditor.takes(playlistOwner = 1, songOwners = setOf(0)))
        assertEquals(false, app.siansa.data.PlaylistEditor.takes(playlistOwner = 0, songOwners = setOf(0, 1)))
        // A playlist on this phone takes anyone's.
        assertEquals(true, app.siansa.data.PlaylistEditor.takes(playlistOwner = null, songOwners = setOf(0, 1)))
    }

    @Test fun ownPlaylistsHideWithOwnMusic() {
        val mine = Playlist(id = "p-max", name = "Max")
        val kids = Playlist(id = "p-kids", name = "Kids")
        assertEquals(listOf("p-max", "p-kids"), app.siansa.data.shownPlaylists(listOf(mine, kids), ownHidden = false, othersIds = setOf("p-kids")).map { it.id })
        assertEquals(listOf("p-kids"), app.siansa.data.shownPlaylists(listOf(mine, kids), ownHidden = true, othersIds = setOf("p-kids")).map { it.id })
    }
}
