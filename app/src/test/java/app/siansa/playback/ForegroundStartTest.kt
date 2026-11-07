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

    @Test fun `playing sound in the background, no longer in the foreground, does not ask again`() {
        // 0.52.0-dev: a cast update arrived after Android had taken the foreground away; asking crashed the app.
        assertFalse(mayStartForeground(startInForegroundRequired = true, playing = true, importance = 300))
        assertFalse(mayStartForeground(startInForegroundRequired = true, playing = true, importance = cached))
    }

    @Test fun `with the app open it may ask for the foreground`() {
        assertTrue(mayStartForeground(startInForegroundRequired = true, playing = false, importance = foreground))
    }

    @Test fun `updates that stay as they are are always allowed`() {
        assertTrue(mayStartForeground(startInForegroundRequired = false, playing = false, importance = cached))
    }
}
