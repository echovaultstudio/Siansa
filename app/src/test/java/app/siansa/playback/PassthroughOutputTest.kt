package app.siansa.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class PassthroughOutputTest {
    private fun atmos(buffer: Int = 96_000, session: Int = 7) = OutputConfig.Builder()
        .setEncoding(C.ENCODING_E_AC3_JOC).setSampleRate(48_000).setChannelMask(SIX_CHANNELS)
        .setBufferSize(buffer).setAudioSessionId(session).build()

    @Test
    fun twoAtmosSongsAreOneStream() {
        // Issue #860: the output to the receiver goes on from one song of a DJ mix to the next.
        assertTrue(PassthroughRenderer.sameStream(atmos(), atmos(buffer = 120_000)))
        assertEquals("buffer size", PassthroughRenderer.differences(atmos(), atmos(buffer = 120_000)))
        assertEquals("", PassthroughRenderer.differences(atmos(), atmos()))
    }

    @Test
    fun aDifferentFormatOrRateIsNot() {
        val ac3 = atmos().buildUpon().setEncoding(C.ENCODING_AC3).build()
        val rate = atmos().buildUpon().setSampleRate(44_100).build()
        val stereo = atmos().buildUpon().setChannelMask(STEREO).build()
        assertFalse(PassthroughRenderer.sameStream(atmos(), ac3))
        assertFalse(PassthroughRenderer.sameStream(atmos(), rate))
        assertFalse(PassthroughRenderer.sameStream(atmos(), stereo))
    }

    @Test
    fun decodedAudioIsLeftToMedia3() {
        val pcm = atmos().buildUpon().setEncoding(C.ENCODING_PCM_16BIT).build()
        assertFalse(PassthroughRenderer.sameStream(pcm, pcm.buildUpon().setBufferSize(1).build()))
        assertEquals("audio attributes, session",
            PassthroughRenderer.differences(atmos(), atmos(session = 9).buildUpon().setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_ALARM).build()).build()))
    }

    private companion object {
        const val SIX_CHANNELS = 0xFC
        const val STEREO = 0xC
    }
}
