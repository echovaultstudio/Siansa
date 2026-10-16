package app.musix.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/** Compact JSON for the large library cache: defaults and nulls are left out. */
val CacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String? = null,
    val album: String = "",
    val albumId: String? = null,
    val coverArt: String? = null,
    val durationSec: Int = 0,
    val track: Int? = null,
    val disc: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val starred: Boolean = false,
)

/** An album. [ids] holds every Navidrome album id merged into it (the same album in several libraries). */
@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val created: String? = null,
    val playCount: Long = 0,
    val ids: List<String> = emptyList(),
    val releaseTypes: List<String> = emptyList(),
    val isCompilation: Boolean = false,
) {
    val allIds: List<String> get() = ids.ifEmpty { listOf(id) }
}

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val durationSec: Int = 0,
    val coverArt: String? = null,
    val owner: String? = null,
    val comment: String? = null,
    val changed: String? = null,
)

data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

data class AlbumDetail(val album: Album, val songs: List<Song>)
data class ArtistDetail(val artist: Artist, val albums: List<Album>)
data class SearchResults(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)

data class LyricLine(val startMs: Long?, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>)

data class MusicFolder(val id: String, val name: String)

data class SimilarArtist(val id: String?, val name: String, val coverArt: String?, val imageUrl: String?)

data class ArtistInfo(
    val biography: String?,
    val imageUrl: String?,
    val lastFmUrl: String?,
    val similar: List<SimilarArtist>,
)

/** A time-of-day recipe for a daily mix. Hours are 0–23; a window may wrap past midnight. */
@Serializable
data class Selector(
    val id: String,
    val name: String,
    val startHour: Int,
    val endHour: Int,
    val guidance: String,
    val baseQuery: String,
    val avoid: List<String> = emptyList(),
    val length: Int = 40,
    val repeatDays: Int = 3,
    val useAi: Boolean = true,
    val enabled: Boolean = true,
) {
    fun covers(hour: Int): Boolean = when {
        startHour == endHour -> true
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    val hoursLabel: String
        get() = "%02d:00–%02d:00".format(startHour, endHour)
}

@Serializable
data class Mix(
    val id: String,
    val selectorId: String,
    val date: String,
    val title: String,
    val description: String,
    val query: String,
    val songs: List<Song>,
    val createdAt: Long,
    val aiNamed: Boolean,
)

@Serializable
data class PlayEvent(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String? = null,
    val at: Long,
)
