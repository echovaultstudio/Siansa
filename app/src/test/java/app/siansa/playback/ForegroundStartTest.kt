package app.siansa.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundStartTest {
    private val foreground = 100 // ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    private val service = 125 // IMPORTANCE_FOREGROUND_SERVICE: only the playback service is running
    private val cached = 400 // IMPORTANCE_CACHED

    @Test fun `waiting for data in the background does not ask for the foreground`() {
        assertFalse(mayStartForeground(startInForegroundRequired = true, playing = false, importance = service))
        assertFalse(mayStartForeground(startInForegroundRequired = true, playing = false, importance = cached))
    }

    @Test fun `playing sound may ask for the foreground`() {
        assertTrue(mayStartForeground(startInForegroundRequired = true, playing = true, importance = service))
    }

    @Test fun `playing again in the background after a long pause asks for the foreground again`() {
        // 10 Nov: resumed from the notification after a 10-minute pause, it stayed out of the foreground, Android cut
        // its network with the screen off and the music stopped. A refusal is caught now (0.52.0-dev crashed on it).
        assertTrue(mayStartForeground(startInForegroundRequired = true, playing = true, importance = 300))
        assertTrue(mayStartForeground(startInForegroundRequired = true, playing = true, importance = cached))
    }

    @Test fun `only network errors are tried again for minutes`() {
        assertTrue(keepsTryingAfter(androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(keepsTryingAfter(androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertFalse(keepsTryingAfter(androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(keepsTryingAfter(androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
    }

    @Test fun `with the app open it may ask for the foreground`() {
        assertTrue(mayStartForeground(startInForegroundRequired = true, playing = false, importance = foreground))
    }

    @Test fun `updates that stay as they are are always allowed`() {
        assertTrue(mayStartForeground(startInForegroundRequired = false, playing = false, importance = cached))
    }
}
