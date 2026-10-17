package app.musix.ui

import app.musix.data.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

/** The line under a playlist in a list. */
class PlaylistSubtitleTest {
    @Test fun showsKindOwnerAndSongCount() {
        assertEquals("Playlist · max · 12 songs", playlistSubtitle(Playlist("1", "Road trip", songCount = 12, owner = "max")))
    }

    @Test fun oneSongIsSingular() {
        assertEquals("Playlist · max · 1 song", playlistSubtitle(Playlist("1", "One", songCount = 1, owner = "max")))
    }

    @Test fun emptyPlaylistSaysNoSongs() {
        assertEquals("Playlist · max · 0 songs", playlistSubtitle(Playlist("1", "New", songCount = 0, owner = "max")))
    }

    @Test fun missingOwnerIsLeftOutOrUsesFallback() {
        assertEquals("Playlist · 3 songs", playlistSubtitle(Playlist("1", "X", songCount = 3)))
        assertEquals("Playlist · 3 songs", playlistSubtitle(Playlist("1", "X", songCount = 3, owner = " ")))
        assertEquals("Playlist · Navidrome · 3 songs", playlistSubtitle(Playlist("1", "X", songCount = 3), fallbackOwner = "Navidrome"))
        assertEquals("Playlist · ann · 3 songs", playlistSubtitle(Playlist("1", "X", songCount = 3, owner = "ann"), fallbackOwner = "Navidrome"))
    }
}
