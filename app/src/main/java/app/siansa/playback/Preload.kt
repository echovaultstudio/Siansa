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
    /**
     * With the song cache off: none, or one to three songs. They're held on the phone only until they've played
     * ([songCacheKeysToDrop]), so this caps the space they take.
     */
    val CHOICES_WITHOUT_CACHE = 0..3
    const val DEFAULT_WIFI = 2
    const val DEFAULT_MOBILE = 1

    /** Separate from the song cache: with it off, up to three songs are still loaded ahead ([CHOICES_WITHOUT_CACHE]). */
    fun count(s: AppSettings, unmetered: Boolean): Int =
        (if (unmetered) s.preloadWifi else s.preloadMobile).coerceIn(choices(s))

    fun choices(s: AppSettings): IntRange = if (songCacheOn(s)) CHOICES else CHOICES_WITHOUT_CACHE

    /**
     * Whether original streams go through the song cache's store at all: while it's on, or while songs are loaded
     * ahead with it off (they're held there until they've played). With both off, nothing touches the disk.
     */
    fun usesCacheStore(s: AppSettings, unmetered: Boolean): Boolean = songCacheOn(s) || count(s, unmetered) > 0

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
