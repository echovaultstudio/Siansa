package app.siansa.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a song's tags say beyond the basics, for Song details (issue #504): every artist, the album artists,
 * composers and other roles, every genre and mood, dates, BPM, ISRC, explicit, comment, MusicBrainz id.
 * Navidrome gives these through OpenSubsonic, Emby through its own fields; empty when the server has none.
 */
data class SongTags(
    val artists: List<String> = emptyList(),
    val albumArtists: List<String> = emptyList(),
    /** Composer, Lyricist, Conductor… with who, in the order the server gives them. */
    val roles: List<Pair<String, List<String>>> = emptyList(),
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val year: Int? = null,
    /** The release date as the server gives it (2017-05-05), when it has more than the year. */
    val released: String? = null,
    val bpm: Int? = null,
    val isrc: List<String> = emptyList(),
    /** true explicit, false clean, null not tagged. */
    val explicit: Boolean? = null,
    val comment: String? = null,
    val musicBrainzId: String? = null,
)

/**
 * An album's tags beyond the basics, for its page (issue #504): its version ("Deluxe edition"), the year it
 * first came out (a remaster says 2017, the album 1977), labels, every genre and mood, explicit.
 */
data class AlbumInfo(
    val version: String? = null,
    val originalYear: Int? = null,
    val labels: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val explicit: Boolean? = null,
)

/** Reading tags from Navidrome (OpenSubsonic) and Emby. */
object Tags {
    /** OpenSubsonic's Child: artists, albumArtists, contributors (role, subRole), genres, moods, bpm, isrc… */
    fun subsonicSong(o: JsonObject): SongTags {
        val roles = LinkedHashMap<String, MutableList<String>>()
        for (c in o.arr("contributors")) {
            val name = c.obj("artist")?.str("name")?.trim().orEmpty()
            val role = c.str("role")?.trim().orEmpty()
            if (name.isEmpty() || role.isEmpty()) continue
            val list = roles.getOrPut(roleLabel(role, c.str("subRole"))) { mutableListOf() }
            if (name !in list) list += name
        }
        // Older servers only send who composed it as one text.
        val composer = o.str("displayComposer")?.trim()?.takeIf { it.isNotEmpty() }
        if (composer != null && roles.keys.none { it.startsWith("Composer") }) roles["Composer"] = mutableListOf(composer)
        return SongTags(
            artists = o.arr("artists").mapNotNull { it.str("name")?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
            albumArtists = o.arr("albumArtists").mapNotNull { it.str("name")?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
            roles = roles.map { (k, v) -> k to v.toList() },
            genres = o.arr("genres").mapNotNull { it.str("name")?.trim()?.takeIf(String::isNotEmpty) }.distinct()
                .ifEmpty { listOfNotNull(o.str("genre")?.trim()?.takeIf(String::isNotEmpty)) },
            moods = strings(o["moods"]),
            year = o.int("year")?.takeIf { it > 0 },
            bpm = o.int("bpm")?.takeIf { it > 0 },
            isrc = strings(o["isrc"]),
            explicit = explicitOf(o.str("explicitStatus")),
            comment = o.str("comment")?.trim()?.takeIf(String::isNotEmpty),
            musicBrainzId = o.str("musicBrainzId")?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    /** OpenSubsonic's AlbumID3: version, originalReleaseDate, recordLabels, genres, moods, explicitStatus. */
    fun subsonicAlbum(o: JsonObject): AlbumInfo = AlbumInfo(
        version = o.str("version")?.trim()?.takeIf(String::isNotEmpty),
        originalYear = o.obj("originalReleaseDate")?.int("year")?.takeIf { it > 0 },
        labels = o.arr("recordLabels").mapNotNull { it.str("name")?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
        genres = o.arr("genres").mapNotNull { it.str("name")?.trim()?.takeIf(String::isNotEmpty) }.distinct()
            .ifEmpty { listOfNotNull(o.str("genre")?.trim()?.takeIf(String::isNotEmpty)) },
        moods = strings(o["moods"]),
        explicit = explicitOf(o.str("explicitStatus")),
    )

    /** Emby's item with People, Genres, AlbumArtists, ProviderIds and PremiereDate. */
    fun embySong(o: JsonObject): SongTags {
        val roles = LinkedHashMap<String, MutableList<String>>()
        for (p in o.arr("People")) {
            val name = p.str("Name")?.trim().orEmpty()
            val type = p.str("Type")?.trim().orEmpty()
            if (name.isEmpty() || type.isEmpty() || type.equals("Actor", true)) continue
            val list = roles.getOrPut(roleLabel(type, null)) { mutableListOf() }
            if (name !in list) list += name
        }
        val premiere = o.str("PremiereDate")?.take(10)?.takeIf { DATE.matches(it) }
        return SongTags(
            artists = strings(o["Artists"]),
            albumArtists = o.arr("AlbumArtists").mapNotNull { it.str("Name")?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
            roles = roles.map { (k, v) -> k to v.toList() },
            genres = strings(o["Genres"]),
            year = o.int("ProductionYear")?.takeIf { it > 0 },
            released = premiere?.takeUnless { it.endsWith("-01-01") },
            musicBrainzId = o.obj("ProviderIds")?.let { it.str("MusicBrainzTrack") ?: it.str("MusicBrainzRecording") }
                ?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    /** Emby's album: Studios are its labels; Emby has no version or original date. */
    fun embyAlbum(o: JsonObject): AlbumInfo = AlbumInfo(
        labels = o.arr("Studios").mapNotNull { it.str("Name")?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
        genres = strings(o["Genres"]),
    )

    /** "composer" → "Composer", "performer" + "guitar" → "Performer (guitar)", "djmixer" → "DJ mixer". */
    fun roleLabel(role: String, subRole: String?): String {
        val base = when (role.lowercase()) {
            "djmixer", "dj-mixer", "dj mixer" -> "DJ mixer"
            "albumartist" -> "Album artist"
            "mixer" -> "Mixed by"
            else -> role.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }
        }
        val sub = subRole?.trim()?.takeIf { it.isNotEmpty() }
        return if (sub != null) "$base ($sub)" else base
    }

    private fun explicitOf(status: String?): Boolean? = when (status?.trim()?.lowercase()) {
        "explicit", "e" -> true
        "clean", "c" -> false
        else -> null
    }

    private fun strings(e: JsonElement?): List<String> =
        (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }?.distinct().orEmpty()

    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
}
