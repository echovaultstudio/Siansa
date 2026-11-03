package app.siansa.playback.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutputProvider
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(UnstableApi::class)
class MixerOutputProviderTest {
    @Test fun sixteenBitGoesStraightThrough() {
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY, MixerOutputProvider.supportLevel(C.ENCODING_PCM_16BIT, false))
    }

    @Test fun floatIsTurnedInto16BitUnlessTheSinkAsksForFloat() {
        // 0.52.0-dev: saying float went straight through made FFmpeg decode to float, and after songs changed back to
        // back it handed over only silence (Waxcap, Passion, MPD, Rampage, Yeah!). As Media3's own output answers.
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_WITH_TRANSCODING, MixerOutputProvider.supportLevel(C.ENCODING_PCM_FLOAT, false))
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY, MixerOutputProvider.supportLevel(C.ENCODING_PCM_FLOAT, true))
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_WITH_TRANSCODING, MixerOutputProvider.supportLevel(C.ENCODING_PCM_24BIT, false))
    }
}
