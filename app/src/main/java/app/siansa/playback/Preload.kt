package app.siansa.playback

import app.siansa.data.AppSettings

/**
 * How many upcoming songs are loaded ahead into the song cache while one plays (issue #254), set apart for
 * Wi-Fi and mobile data in Settings › Playback. Fewer means fewer downloads on mobile data; more means songs
 * keep playing through a dropped connection. Songs are loaded one at a time, only once the current one is in.
 */
object Preload {
    /** The choices offered: none, or one to five songs. */
    val CHOICES = 0..5
    const val DEFAULT_WIFI = 2
    const val DEFAULT_MOBILE = 1

    /** None while the song cache is off: songs loaded ahead are kept in it, so there'd be nowhere to put them. */
    fun count(s: AppSettings, unmetered: Boolean): Int =
        if (!songCacheOn(s)) 0 else (if (unmetered) s.preloadWifi else s.preloadMobile).coerceIn(CHOICES)

    /**
     * Songs ahead to fetch loudness and fade shapes for: one more than are loaded, so each is ready before
     * its song, and always at least the next one (a small request, needed for crossfades).
     */
    fun profilesAhead(s: AppSettings, unmetered: Boolean): Int = (count(s, unmetered) + 1).coerceIn(1, 5)

    fun label(n: Int): String = when (n) {
        0 -> "None"
        1 -> "1 song"
        else -> "$n songs"
    }
}
