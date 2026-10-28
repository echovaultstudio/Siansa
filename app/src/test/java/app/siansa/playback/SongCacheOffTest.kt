package app.siansa.playback

import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The song cache can be switched off in Settings › Storage (songCacheMb = 0). */
class SongCacheOffTest {
    @Test fun zeroMeansOff() {
        assertFalse(songCacheOn(AppSettings(songCacheMb = 0)))
        assertTrue(songCacheOn(AppSettings(songCacheMb = 512)))
        assertTrue(songCacheOn(AppSettings()))  // on by default
    }

    @Test fun originalsGoThroughTheCacheOnlyWhileItIsOn() {
        assertTrue(goesThroughCache(format = null, cacheOn = true))
        assertTrue(goesThroughCache(format = "raw", cacheOn = true))
        assertFalse(goesThroughCache(format = null, cacheOn = false))
        assertFalse(goesThroughCache(format = "raw", cacheOn = false))
    }

    @Test fun conversionsNeverGoThroughTheCache() {
        assertFalse(goesThroughCache(format = "opus", cacheOn = true))
    }

    @Test fun nothingIsLoadedAheadWithTheCacheOff() {
        val off = AppSettings(songCacheMb = 0, preloadWifi = 4, preloadMobile = 2)
        assertEquals(0, Preload.count(off, unmetered = true))
        assertEquals(0, Preload.count(off, unmetered = false))
        // Loudness for the next song is still fetched (a small request, needed for crossfades).
        assertEquals(1, Preload.profilesAhead(off, unmetered = true))
    }
}
