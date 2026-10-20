package app.musix.playback

import app.musix.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Songs loaded ahead, on Wi-Fi and on mobile data (issue #254). */
class PreloadTest {
    @Test fun wifiAndMobileEachHaveTheirOwnCount() {
        val s = AppSettings(preloadWifi = 4, preloadMobile = 0)
        assertEquals(4, Preload.count(s, unmetered = true))
        assertEquals(0, Preload.count(s, unmetered = false))
    }

    @Test fun defaultsLoadTwoOnWifiAndOneOnMobile() {
        assertEquals(Preload.DEFAULT_WIFI, Preload.count(AppSettings(), unmetered = true))
        assertEquals(Preload.DEFAULT_MOBILE, Preload.count(AppSettings(), unmetered = false))
    }

    @Test fun oddValuesAreKeptInRange() {
        assertEquals(5, Preload.count(AppSettings(preloadWifi = 40), unmetered = true))
        assertEquals(0, Preload.count(AppSettings(preloadMobile = -3), unmetered = false))
    }

    @Test fun loudnessIsFetchedOneFurtherButAlwaysForTheNextSong() {
        assertEquals(1, Preload.profilesAhead(AppSettings(preloadMobile = 0), unmetered = false))
        assertEquals(3, Preload.profilesAhead(AppSettings(preloadWifi = 2), unmetered = true))
        assertEquals(5, Preload.profilesAhead(AppSettings(preloadWifi = 5), unmetered = true))
    }
}
