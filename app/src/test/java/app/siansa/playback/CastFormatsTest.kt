package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File types for casting (issue #114): the library keeps none, so an ALAC album played from it went to the TV as MP3
 * in "Lossless" (0.52.0-dev). They're remembered and asked from the server before the songs go to the TV.
 */
class CastFormatsTest {
    private val raw = "https://music.example/rest/stream?u=me&id=x&format=raw"

    private fun song(id: String, album: String? = "al1", suffix: String? = null) = Song(id = id, title = id, albumId = album, suffix = suffix)

    @Test fun aTypeLearnedOnceIsKnownForTheSameSongFromTheLibrary() {
        assertNull(CastFormats.suffixOf(song("fmt-1")))
        CastFormats.record(listOf(song("fmt-1", suffix = "M4A")))
        assertEquals("m4a", CastFormats.suffixOf(song("fmt-1")))
        // The song's own type comes first.
        assertEquals("flac", CastFormats.suffixOf(song("fmt-1", suffix = "flac")))
    }

    @Test fun theSongRegistryKeepsATypeWhenTheLibrarysCopyComesWithout() {
        SongRegistry.register(listOf(song("fmt-2", suffix = "m4a")))
        SongRegistry.register(listOf(song("fmt-2")))
        assertEquals("m4a", SongRegistry.get("fmt-2")?.suffix)
    }

    @Test fun oneAskPerAlbumThenSongsWithoutOne() {
        val ask = CastFormats.plan(listOf(song("a", "al1"), song("b", "al1"), song("c", "al2"), song("d", null)))
        assertEquals(listOf("al1", "al2"), ask.albums)
        assertEquals(listOf("d"), ask.songs)
    }

    @Test fun albumsPastTheLimitAreAskedSongBySongUpToALimit() {
        val songs = (1..5).map { song("s$it", "al$it") }
        val ask = CastFormats.plan(songs, maxAlbums = 2, maxSongs = 2)
        assertEquals(listOf("al1", "al2"), ask.albums)
        assertEquals(listOf("s3", "s4"), ask.songs)
    }

    @Test fun theLogSaysHowTheQueueGoes() {
        val s = CastFormats.summary(listOf("m4a", "m4a", "flac", "wma", null), CastQuality.Lossless, raw)
        assertEquals(CastFormats.Summary(asIs = 1, flac = 2, mp3 = 2, unknownType = 1), s)
        assertTrue(s.describe(CastQuality.Lossless).contains("2 as FLAC from the server"))
        val mp3 = CastFormats.summary(listOf("m4a", "flac"), CastQuality.Mp3, raw)
        assertEquals(CastFormats.Summary(0, 0, 2, 0), mp3)
        assertFalse(mp3.describe(CastQuality.Mp3).contains("unknown"))
    }
}
