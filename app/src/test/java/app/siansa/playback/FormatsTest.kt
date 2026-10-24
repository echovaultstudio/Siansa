package app.siansa.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** File types the phone can't open are played converted by Navidrome (issue #354). */
class FormatsTest {
    @Test fun containersThePhoneCantOpenAreConverted() {
        listOf("wma", "WMA", ".aiff", "ape", "wv", "mpc", "dsf").forEach { assertTrue(it, Formats.mustConvert(it)) }
    }

    @Test fun formatsThePhonePlaysStayOriginal() {
        listOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "mka", null, "").forEach { assertFalse("$it", Formats.mustConvert(it)) }
    }

    @Test fun onlyErrorsAboutTheFileItselfAreRetriedConverted() {
        assertTrue(Formats.convertible(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertTrue(Formats.convertible(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertFalse(Formats.convertible(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(Formats.convertible(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
    }
}
