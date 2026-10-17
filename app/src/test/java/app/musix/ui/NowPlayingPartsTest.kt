package app.musix.ui

import app.musix.data.LyricLine
import app.musix.data.Lyrics
import app.musix.data.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The redesigned now playing: the lyrics card, the artist card's line and the playlists you can add to. */
class NowPlayingPartsTest {
    private val synced = Lyrics(
        synced = true,
        lines = listOf(
            LyricLine(0, ""),
            LyricLine(10_000, "one"),
            LyricLine(20_000, "two"),
            LyricLine(30_000, "three"),
            LyricLine(40_000, "four"),
            LyricLine(50_000, "five"),
            LyricLine(60_000, "six"),
        ),
    )

    @Test fun previewStartsAtTheLinePlayingNow() {
        assertEquals(listOf("three", "four", "five", "six"), lyricsPreview(synced, 31_000))
    }

    @Test fun previewBeforeTheSingingShowsTheFirstLines() {
        assertEquals(listOf("one", "two", "three", "four"), lyricsPreview(synced, 1_000))
    }

    @Test fun previewNearTheEndStillShowsFourLines() {
        assertEquals(listOf("three", "four", "five", "six"), lyricsPreview(synced, 65_000))
    }

    @Test fun plainLyricsShowTheirFirstLines() {
        val plain = Lyrics(false, listOf(LyricLine(null, "a"), LyricLine(null, " "), LyricLine(null, "b")))
        assertEquals(listOf("a", "b"), lyricsPreview(plain, 0))
        assertTrue(lyricsPreview(Lyrics(true, emptyList()), 0).isEmpty())
    }

    @Test fun artistLine() {
        assertEquals("12 albums · 148 songs in your library", artistLibraryLine(12, 148))
        assertEquals("1 album · 1 song in your library", artistLibraryLine(1, 1))
        assertNull(artistLibraryLine(0, 0))
    }

    @Test fun onlyYourOwnPlaylistsCanBeAddedTo() {
        val lists = listOf(
            Playlist("1", "zeta", owner = "max"),
            Playlist("2", "Alpha", owner = "MAX"),
            Playlist("3", "Ratty's", owner = "ratty"),
            Playlist("4", "Unknown owner"),
        )
        assertEquals(listOf("Alpha", "Unknown owner", "zeta"), editablePlaylists(lists, "max").map { it.name })
    }
}
