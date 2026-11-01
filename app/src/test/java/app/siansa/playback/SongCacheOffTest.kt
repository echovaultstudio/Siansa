package app.siansa.playback

import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The song cache can be switched off in Settings › Streaming and data (songCacheMb = 0); loading ahead stays separate. */
class SongCacheOffTest {
    @Test fun zeroMeansOff() {
        assertFalse(songCacheOn(AppSettings(songCacheMb = 0)))
        assertTrue(songCacheOn(AppSettings(songCacheMb = 512)))
        assertTrue(songCacheOn(AppSettings()))  // on by default
    }

    @Test fun loadingAheadStillWorksWithTheCacheOffUpToThreeSongs() {
        val off = AppSettings(songCacheMb = 0, preloadWifi = 2, preloadMobile = 5)
        assertEquals(2, Preload.count(off, unmetered = true))
        assertEquals(3, Preload.count(off, unmetered = false))  // capped at 3 while the cache is off
        assertEquals(0..3, Preload.choices(off))
        assertEquals(0..5, Preload.choices(AppSettings()))
        assertEquals(5, Preload.count(AppSettings(preloadMobile = 5), unmetered = false))
    }

    @Test fun theStoreIsOnlyUsedWhenSomethingNeedsIt() {
        assertTrue(Preload.usesCacheStore(AppSettings(), unmetered = true))
        assertTrue(Preload.usesCacheStore(AppSettings(songCacheMb = 0, preloadWifi = 1), unmetered = true))
        assertFalse(Preload.usesCacheStore(AppSettings(songCacheMb = 0, preloadWifi = 0, preloadMobile = 0), unmetered = true))
    }

    @Test fun originalsGoThroughTheStoreOnlyWhileItIsInUse() {
        assertTrue(goesThroughCache(format = null, storeInUse = true))
        assertTrue(goesThroughCache(format = "raw", storeInUse = true))
        assertFalse(goesThroughCache(format = null, storeInUse = false))
        assertFalse(goesThroughCache(format = "opus", storeInUse = true))  // conversions never
    }

    @Test fun playedSongsAreDroppedAndThePlayingAndUpcomingOnesKept() {
        val keys = listOf("song:a:raw:", "song:b:raw:", "song:c:raw:320", "https://other/stream")
        assertEquals(listOf("song:a:raw:", "https://other/stream"), songCacheKeysToDrop(keys, setOf("b", "c")))
        assertEquals(keys, songCacheKeysToDrop(keys, emptySet()))
    }
}
