package app.siansa.local

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Locale

/**
 * Ids of music on this phone (issue #352). Everything from a local folder has an id starting with [PREFIX], so a
 * request about it goes to [LocalSource] rather than the music server, and the library keeps it as the phone's.
 */
object LocalIds {
    const val PREFIX = "lf-"

    /** The owner of local songs in the library's list of where items come from (other users are 0 and up). */
    const val SOURCE = -1

    /** Whether [id] is the phone's: a song, album, artist or cover, also as a cover id ("al-lf-…"). */
    fun isLocal(id: String?): Boolean = id != null && (id.startsWith(PREFIX) || (id.length > 3 && id[2] == '-' && id.startsWith(PREFIX, 3)))

    fun song(uri: String): String = PREFIX + "s" + hash(uri)
    fun album(artist: String, name: String): String = PREFIX + "a" + hash(app.siansa.data.normalizeKey(artist) + "|" + app.siansa.data.normalizeKey(name))
    fun artist(key: String): String = PREFIX + "r" + hash(key)
    fun cover(key: String): String = PREFIX + "c" + key

    /** The picture file's key of a local cover id, or null when it isn't one. */
    fun coverKey(id: String): String? = if (id.startsWith(PREFIX + "c")) id.removePrefix(PREFIX + "c").substringBefore('_') else null

    internal fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
}

/** A folder picked in Settings › Servers and services › Files on this phone: its tree address and a name to show. */
@Serializable
data class LocalFolder(val uri: String, val name: String)

/**
 * One music file on the phone, as the last scan read it (issue #352). [modified] and [size] tell whether it changed
 * since, so a scan only reads new and changed files. MusicBrainz IDs come from the file's tags ([recordingMbid]
 * …) or, when it has none, from looking it up on MusicBrainz ([mbRecording], [mbArtists]; [mbLooked] once tried).
 */
@Serializable
data class LocalTrack(
    val uri: String,
    val folder: String,
    val path: String,
    val size: Long = 0,
    val modified: Long = 0,
    val mime: String? = null,
    val title: String,
    val artist: String = "",
    val artists: List<String> = emptyList(),
    val album: String = "",
    val albumArtist: String = "",
    val track: Int? = null,
    val disc: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationSec: Int = 0,
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val compilation: Boolean = false,
    val recordingMbid: String? = null,
    val artistMbids: List<String> = emptyList(),
    val albumMbid: String? = null,
    val albumArtistMbids: List<String> = emptyList(),
    val hasCover: Boolean = false,
    val mbLooked: Boolean = false,
    val mbRecording: String? = null,
    val mbArtists: List<String> = emptyList(),
) {
    val id: String get() = LocalIds.song(uri)

    /** The recording's MusicBrainz ID, from the tags or else from the lookup. */
    val recording: String? get() = recordingMbid ?: mbRecording

    /** The artists' MusicBrainz IDs, from the tags or else from the lookup. */
    val artistIds: List<String> get() = artistMbids.ifEmpty { mbArtists }

    /** Whether MusicBrainz could add something: no recording ID or no artist IDs in the tags, not tried yet. */
    val wantsLookup: Boolean get() = !mbLooked && (recordingMbid == null || artistMbids.isEmpty()) && title.isNotBlank() && artist.isNotBlank()

    /** The file type, from its name: "flac", "mp3"… */
    val suffix: String? get() = path.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it.isNotEmpty() && it.length <= 5 }
}

/** What Android's MediaMetadataRetriever found, used where the file's own tags have nothing. */
data class RetrieverTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val compilation: String? = null,
)

/** Turns tags into a [LocalTrack] (issue #352). */
object LocalTags {
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val NUMBER = Regex("^\\s*(\\d+)")
    private val YEAR = Regex("(\\d{4})")
    private val DECIMAL = Regex("[-+]?\\d+(?:[.,]\\d+)?")

    /**
     * The track for the file at [uri] (its [path] in [folder]), from its [tags] (see [TagReader]) and what Android
     * read ([fallback]). A file without a title is named after the file.
     */
    fun track(
        uri: String,
        folder: String,
        path: String,
        size: Long,
        modified: Long,
        mime: String?,
        tags: Map<String, List<String>>,
        fallback: RetrieverTags,
    ): LocalTrack {
        fun first(key: String) = tags[key]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val artistValues = tags["ARTIST"].orEmpty()
        val listed = tags["ARTISTS"].orEmpty()
        val artistText = when {
            artistValues.size > 1 -> artistValues.joinToString(", ")
            else -> artistValues.firstOrNull() ?: fallback.artist?.trim().orEmpty()
        }
        val artists = when {
            listed.isNotEmpty() -> listed
            artistValues.size > 1 -> artistValues
            artistText.isNotBlank() -> listOf(artistText)
            else -> emptyList()
        }
        val albumArtist = first("ALBUMARTIST") ?: tags["ALBUMARTISTS"]?.joinToString(", ") ?: fallback.albumArtist?.trim().orEmpty()
        val fileName = path.substringAfterLast('/').substringBeforeLast('.')
        return LocalTrack(
            uri = uri,
            folder = folder,
            path = path,
            size = size,
            modified = modified,
            mime = mime,
            title = first("TITLE") ?: fallback.title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName,
            artist = artistText,
            artists = artists,
            album = first("ALBUM") ?: fallback.album?.trim().orEmpty(),
            albumArtist = albumArtist,
            track = number(first("TRACKNUMBER") ?: first("TRACK") ?: fallback.track),
            disc = number(first("DISCNUMBER") ?: first("DISC") ?: fallback.disc),
            year = year(first("DATE") ?: first("YEAR") ?: first("ORIGINALDATE") ?: fallback.year),
            genre = first("GENRE") ?: fallback.genre?.trim()?.takeIf { it.isNotEmpty() },
            durationSec = ((fallback.durationMs ?: 0L) / 1000).toInt(),
            trackGain = decibels(first("REPLAYGAIN_TRACK_GAIN")),
            albumGain = decibels(first("REPLAYGAIN_ALBUM_GAIN")),
            trackPeak = decibels(first("REPLAYGAIN_TRACK_PEAK")),
            albumPeak = decibels(first("REPLAYGAIN_ALBUM_PEAK")),
            compilation = (first("COMPILATION") ?: fallback.compilation)?.let { it == "1" || it.equals("true", true) } == true,
            recordingMbid = mbids(tags["MUSICBRAINZ_TRACKID"]).firstOrNull(),
            artistMbids = mbids(tags["MUSICBRAINZ_ARTISTID"]),
            albumMbid = mbids(tags["MUSICBRAINZ_ALBUMID"]).firstOrNull(),
            albumArtistMbids = mbids(tags["MUSICBRAINZ_ALBUMARTISTID"]),
        )
    }

    /** "3", "3/12" or "03" → 3; null for nothing or 0. */
    fun number(text: String?): Int? = text?.let { NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 }

    /** "2019", "2019-05-01" → 2019. */
    fun year(text: String?): Int? = text?.let { YEAR.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 1000..2999 }

    /** "-6.54 dB", "+1,20 dB", "0.988" → the number. */
    fun decibels(text: String?): Float? = text?.let { DECIMAL.find(it)?.value?.replace(',', '.')?.toFloatOrNull() }

    /**
     * MusicBrainz IDs from tag values: each value can hold one, or several joined by "/", ";" or ", " (ID3v2.3 and
     * some taggers), lower case; anything that isn't an ID is left out.
     */
    fun mbids(values: List<String>?): List<String> = values.orEmpty()
        .flatMap { it.split('/', ';', ',', '\u0000') }
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { UUID.matches(it) }
        .distinct()
}
