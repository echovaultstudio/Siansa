package app.siansa.net

import app.siansa.data.AppSettings
import app.siansa.data.Song
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Listens sent to Siansa Beats (0.29, the owner, 11 Nov: it keeps the listening history itself, with a page for it,
 * and can send listens on to Last.fm): "Scrobble to Siansa Beats" in Settings › Scrobbling, offered once Siansa Beats
 * is set up and says it takes listens.
 *
 * A listen carries the song's id on the music server (Siansa Beats knows the song by it, on Navidrome, Emby and
 * Jellyfin alike), what it's called (kept as the history's own, so it stays readable when the song leaves the
 * library), and the listener: the name signed in with on the music server, so two people have a history each.
 */
object BeatsListens {
    const val PATH = "/api/v1/listens"
    const val NOW = "/api/v1/listens/now"

    /** The listener's name for Siansa Beats: the music server's user name (none with an API key alone). */
    fun listener(settings: AppSettings): String = settings.navidromeUser.trim()

    /** One finished listen, started at [startedAtSec], as Siansa Beats takes it. */
    fun listen(user: String, song: Song, startedAtSec: Long): String = buildJsonObject {
        put("user", user)
        putJsonArray("listens") {
            addJsonObject {
                put("id", song.id)
                put("at", startedAtSec)
                put("artist", song.artist)
                put("title", song.title)
                if (song.album.isNotBlank()) put("album", song.album)
                if (song.durationSec > 0) put("duration", song.durationSec)
            }
        }
    }.toString()

    /** What's playing now. */
    fun now(user: String, song: Song): String = buildJsonObject {
        put("user", user)
        put("id", song.id)
        put("artist", song.artist)
        put("title", song.title)
        if (song.album.isNotBlank()) put("album", song.album)
        if (song.durationSec > 0) put("duration", song.durationSec)
    }.toString()
}

/** Whether Siansa Beats is set up and takes listens (0.29 or newer), so the switch is offered. */
val AppSettings.beatsTakesListens: Boolean get() = beatsReady && BeatsSound.answers(BeatsListens.PATH)

/** Whether listens go to Siansa Beats: switched on, and it takes them. */
val AppSettings.beatsScrobbleReady: Boolean get() = scrobbleBeats && beatsTakesListens
