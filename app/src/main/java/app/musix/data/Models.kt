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
    // Every artist on the song (Navidrome's OpenSubsonic "artists"), e.g. both sides of a "feat.".
    val artists: List<ArtistRef> = emptyList(),
    /** The file's type as Navidrome names it ("flac", "wma"…), when it sent one; not kept in the library. */
    val suffix: String? = null,
)

/** The song's cover, or its album's when Navidrome didn't send one with the song. */
val Song.cover: String? get() = coverArt ?: albumId?.let { "al-$it" }

@Serializable
data class ArtistRef(val id: String, val name: String)

/** An album. [ids] holds every Navidrome album id merged into it (the same album in several libraries). */
@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val durationSec: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val created: String? = null,
    val playCount: Long = 0,
    val ids: List<String> = emptyList(),
    val releaseTypes: List<String> = emptyList(),
    val isCompilation: Boolean = false,
    // When it was last played (ISO time), from Navidrome's album lists; not stored in the library.
    val played: String? = null,
    /** Which Navidrome library it's in (issue #376), when the server has several; null otherwise. */
    val folderId: String? = null,
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
    /** The artist's MusicBrainz ID (a 36-character UUID), when its files are tagged with one (issue #359). */
    val mbid: String? = null,
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
    /** Navidrome's OpenSubsonic "readonly": true for smart playlists and other people's; null when it didn't say. */
    val readonly: Boolean? = null,
)

data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

/** [discTitles]: the names of the album's discs by disc number (Navidrome's "disctitle" tag), when it has any. */
data class AlbumDetail(val album: Album, val songs: List<Song>, val discTitles: Map<Int, String> = emptyMap())
data class ArtistDetail(val artist: Artist, val albums: List<Album>)
data class SearchResults(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)

/** A word (or syllable) of a line, with when it's sung, for lyrics that are timed word by word. */
data class LyricWord(val startMs: Long, val text: String)

/** A line of lyrics; [startMs] when it's synced, and its [words] when they're timed one by one. */
data class LyricLine(val startMs: Long?, val text: String, val words: List<LyricWord> = emptyList())

data class Lyrics(val synced: Boolean, val lines: List<LyricLine>) {
    /** Timed word by word (karaoke style), not only line by line. */
    val wordTimed: Boolean get() = lines.any { it.words.isNotEmpty() }
}

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
    /** Your this many most played songs never come in (issue #236); up to 80 are known. */
    val leaveOutTopPlayed: Int = 50,
    /** This share of the mix (percent) comes from outside your usual artists and sound (issue #236). */
    val surprise: Int = 30,
    /**
     * Days it's for (issue #348), as ISO days of the week (1 Monday … 7 Sunday); empty means every day. Days and
     * hours work on their own: a vibe can be for Fridays at any time, or every day from 6 to 12.
     */
    val days: List<Int> = emptyList(),
    /** For the whole day, whatever [startHour] and [endHour] say (issue #348). */
    val anyTime: Boolean = false,
) {
    /** Whether it's for this hour, whatever the day. */
    fun covers(hour: Int): Boolean = when {
        anyTime || startHour == endHour -> true
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    /** Whether it's for this day of the week (ISO, 1 Monday … 7 Sunday). */
    fun coversDay(day: Int): Boolean = days.isEmpty() || day in days

    /** Whether it's for this hour on this day (issue #348). */
    fun appliesAt(hour: Int, day: Int): Boolean = covers(hour) && coversDay(day)

    /**
     * How narrowly it's set, so the most fitting vibe wins when several apply: one for a day beats one for every
     * day, and one for some hours beats one for the whole day.
     */
    val specificity: Int
        get() = (if (days.isNotEmpty()) 2 else 0) + (if (anyTime || startHour == endHour) 0 else 1)

    /** When it's for, in a line: "06:00–12:00", "Fri · 12:00–17:00", "Mon, Tue · any time" (issue #348). */
    val hoursLabel: String
        get() {
            val hours = if (anyTime || startHour == endHour) "any time" else "%02d:00–%02d:00".format(startHour, endHour)
            return if (days.isEmpty()) hours else daysLabel(days) + " · " + hours
        }
}

/** Days of the week in short, in week order: "Mon, Fri", "Weekdays", "Weekends", "Every day" (issue #348). */
fun daysLabel(days: Collection<Int>): String {
    val set = days.filter { it in 1..7 }.toSortedSet()
    return when {
        set.isEmpty() || set.size == 7 -> "Every day"
        set == sortedSetOf(1, 2, 3, 4, 5) -> "Weekdays"
        set == sortedSetOf(6, 7) -> "Weekends"
        else -> set.joinToString(", ") { DAY_SHORT[it - 1] }
    }
}

/** Monday first, as ISO days of the week. */
val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Of [selectors], the enabled one most fitting for [hour] on [day]: the most narrowly set, then the first. */
fun fittingSelector(selectors: List<Selector>, hour: Int, day: Int): Selector? =
    selectors.filter { it.enabled && it.appliesAt(hour, day) }.sortedByDescending { it.specificity }.firstOrNull()

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
    // Filled in when the song ends: how long it was heard, and whether it was skipped early.
    val listenedMs: Long? = null,
    val skipped: Boolean = false,
)
