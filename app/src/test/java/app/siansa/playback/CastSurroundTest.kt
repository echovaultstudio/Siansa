package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Surround files (Dolby Atmos, 5.1) cast as they are when that's on; everything else as MP3. */
class CastSurroundTest {
    private fun song(suffix: String?, channels: Int?) = Song(id = "1", title = "t", suffix = suffix, channels = channels)

    @Test fun anAtmosM4aGoesAsItIsWhenOn() {
        assertEquals("audio/mp4", Streaming.untouchedMime(song("m4a", 8), keepSurround = true))
        assertEquals("audio/flac", Streaming.untouchedMime(song("FLAC", 6), keepSurround = true))
        assertEquals("audio/eac3", Streaming.untouchedMime(song(".ec3", 6), keepSurround = true))
    }

    @Test fun offStaysMp3() {
        assertNull(Streaming.untouchedMime(song("m4a", 8), keepSurround = false))
    }

    @Test fun stereoAndUnknownChannelsStayMp3() {
        assertNull(Streaming.untouchedMime(song("m4a", 2), keepSurround = true))
        assertNull(Streaming.untouchedMime(song("m4a", null), keepSurround = true))
    }

    @Test fun anUnknownFileTypeStaysMp3() {
        assertNull(Streaming.untouchedMime(song("dsf", 6), keepSurround = true))
        assertNull(Streaming.untouchedMime(song(null, 6), keepSurround = true))
    }
}
