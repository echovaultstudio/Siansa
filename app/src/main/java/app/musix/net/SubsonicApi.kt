package app.musix.net

import app.musix.data.Album
import app.musix.data.AlbumDetail
import app.musix.data.AppJson
import app.musix.data.AppSettings
import app.musix.data.Artist
import app.musix.data.ArtistDetail
import app.musix.data.ArtistInfo
import app.musix.data.ArtistRef
import app.musix.data.LyricLine
import app.musix.data.Lyrics
import app.musix.data.MusicFolder
import app.musix.data.Playlist
import app.musix.data.PlaylistDetail
import app.musix.data.SearchResults
import app.musix.data.SimilarArtist
import app.musix.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Navidrome through the Subsonic / OpenSubsonic API, with token authentication. */
class SubsonicApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    // One salt per app run keeps cover URLs stable, so images stay cached while scrolling.
    private val salt: String = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
    private var tokenFor: Pair<String, String>? = null

    private fun token(password: String): String {
        tokenFor?.let { if (it.first == password) return it.second }
        val token = md5(password + salt)
        tokenFor = password to token
        return token
    }

    fun url(endpoint: String, params: Map<String, String?> = emptyMap(), repeated: List<Pair<String, String>> = emptyList()): String {
        val s = settings()
        val sb = StringBuilder(s.navidromeUrl)
            .append("/rest/").append(endpoint)
            .append("?u=").append(enc(s.navidromeUser))
            .append("&t=").append(token(s.navidromePassword))
            .append("&s=").append(salt)
            .append("&v=1.16.1&c=musix&f=json")
        for ((key, value) in params) {
            if (value != null) sb.append('&').append(enc(key)).append('=').append(enc(value))
        }
        for ((key, value) in repeated) sb.append('&').append(enc(key)).append('=').append(enc(value))
        return sb.toString()
    }

    /** The untouched original file (format=raw), whatever Navidrome's own transcoding defaults say. */
    fun streamUrl(id: String): String = url("stream", mapOf("id" to id, "format" to "raw"))

    fun coverUrl(id: String, size: Int?): String =
        url("getCoverArt", mapOf("id" to id, "size" to size?.toString()))

    private suspend fun call(
        endpoint: String,
        params: Map<String, String?> = emptyMap(),
        repeated: List<Pair<String, String>> = emptyList(),
    ): JsonObject =
        withContext(Dispatchers.IO) {
            if (settings().navidromeUrl.isBlank()) throw ApiException("Navidrome isn't set up yet")
            val request = Request.Builder().url(url(endpoint, params, repeated)).build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 407) {
                    throw ApiException("The server asked for a login (HTTP ${response.code}). Fill in the proxy login in Settings.")
                }
                if (!response.isSuccessful) throw ApiException("Navidrome answered HTTP ${response.code}")
                val root = try {
                    AppJson.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject
                } catch (e: Exception) {
                    null
                } ?: throw ApiException("That address doesn't look like a Navidrome server")
                if (root.str("status") != "ok") {
                    throw ApiException(root.obj("error")?.str("message") ?: "Navidrome refused the request")
                }
                root
            }
        }

    /** When Navidrome last scanned the music folders (Navidrome's lastScan), to skip syncs with no changes. */
    suspend fun lastScan(): String? {
        val status = call("getScanStatus").obj("scanStatus") ?: return null
        if (status.bool("scanning") == true) return "scanning"
        return status.str("lastScan")
    }

    suspend fun ping(): String {
        val root = call("ping")
        val type = root.str("type") ?: "Subsonic server"
        val version = root.str("serverVersion") ?: root.str("version") ?: ""
        return "$type $version".trim()
    }

    /** Navidrome's libraries. */
    suspend fun musicFolders(): List<MusicFolder> =
        call("getMusicFolders").obj("musicFolders")?.arr("musicFolder").orEmpty().mapNotNull { f ->
            val id = f.str("id") ?: return@mapNotNull null
            MusicFolder(id, f.str("name") ?: "Library $id")
        }

    /** type: alphabeticalByName, newest, frequent, recent, random, starred… */
    suspend fun albums(type: String, size: Int, offset: Int, folderId: String? = null): List<Album> =
        call(
            "getAlbumList2",
            mapOf("type" to type, "size" to "$size", "offset" to "$offset", "musicFolderId" to folderId),
        ).obj("albumList2")?.arr("album").orEmpty().map(::parseAlbum)

    suspend fun album(id: String): AlbumDetail {
        val album = call("getAlbum", mapOf("id" to id)).obj("album") ?: throw ApiException("Album not found")
        return AlbumDetail(parseAlbum(album), album.arr("song").map(::parseSong), parseDiscTitles(album))
    }

    /** OpenSubsonic "discTitles": [{"disc": 1, "title": "…"}], which Navidrome fills from the disctitle tag. */
    private fun parseDiscTitles(album: JsonObject): Map<Int, String> =
        album.arr("discTitles").mapNotNull { d ->
            val disc = d.int("disc") ?: return@mapNotNull null
            val title = d.str("title")?.trim()
            if (title.isNullOrEmpty()) null else disc to title
        }.toMap()

    suspend fun artists(folderId: String? = null): List<Artist> =
        call("getArtists", mapOf("musicFolderId" to folderId)).obj("artists")?.arr("index").orEmpty()
            .flatMap { index -> index.arr("artist").map(::parseArtist) }

    suspend fun artist(id: String): ArtistDetail {
        val artist = call("getArtist", mapOf("id" to id)).obj("artist") ?: throw ApiException("Artist not found")
        return ArtistDetail(parseArtist(artist), artist.arr("album").map(::parseAlbum))
    }

    /**
     * Biography, image and similar artists that Navidrome already has (from its agents or AudioMuse).
     * With [includeNotPresent], similar artists that aren't in the library are listed too (by name only).
     */
    suspend fun artistInfo(id: String, includeNotPresent: Boolean = false): ArtistInfo {
        val info = call("getArtistInfo2", mapOf("id" to id, "count" to "20", "includeNotPresent" to "$includeNotPresent"))
            .obj("artistInfo2")
        return ArtistInfo(
            biography = info?.str("biography")?.let(::cleanBiography)?.takeIf { it.isNotBlank() },
            imageUrl = info?.str("largeImageUrl") ?: info?.str("mediumImageUrl"),
            lastFmUrl = info?.str("lastFmUrl"),
            similar = info?.arr("similarArtist").orEmpty().map { a ->
                SimilarArtist(
                    id = a.str("id")?.takeIf { it.isNotBlank() },
                    name = a.str("name") ?: "",
                    coverArt = a.str("coverArt"),
                    imageUrl = a.str("artistImageUrl"),
                )
            }.filter { it.name.isNotBlank() },
        )
    }

    suspend fun topSongs(artistName: String, count: Int = 10): List<Song> =
        call("getTopSongs", mapOf("artist" to artistName, "count" to "$count"))
            .obj("topSongs")?.arr("song").orEmpty().map(::parseSong)

    suspend fun playlists(): List<Playlist> =
        call("getPlaylists").obj("playlists")?.arr("playlist").orEmpty().map(::parsePlaylist)

    suspend fun playlist(id: String): PlaylistDetail {
        val p = call("getPlaylist", mapOf("id" to id)).obj("playlist") ?: throw ApiException("Playlist not found")
        return PlaylistDetail(parsePlaylist(p), p.arr("entry").map(::parseSong))
    }

    /** Songs that sound like this one. With the AudioMuse plugin, Navidrome answers from sonic analysis. */
    suspend fun similarSongs(songId: String, count: Int = 50): List<Song> =
        call("getSimilarSongs", mapOf("id" to songId, "count" to "$count"))
            .obj("similarSongs")?.arr("song").orEmpty().map(::parseSong)

    /** Songs by this artist and similar artists. */
    suspend fun artistRadio(artistId: String, count: Int = 50): List<Song> =
        call("getSimilarSongs2", mapOf("id" to artistId, "count" to "$count"))
            .obj("similarSongs2")?.arr("song").orEmpty().map(::parseSong)

    suspend fun song(id: String): Song {
        val song = call("getSong", mapOf("id" to id)).obj("song") ?: throw ApiException("Song not found")
        return parseSong(song)
    }

    /** The song's codec, bit rate, file path and play count, as Navidrome reports them. */
    suspend fun songDetails(id: String): SongDetails {
        val song = call("getSong", mapOf("id" to id)).obj("song") ?: throw ApiException("Song not found")
        return parseSongDetails(song)
    }

    /** One page of every song (empty search), used by library sync. */
    suspend fun songsPage(offset: Int, size: Int, folderId: String? = null): List<Song> =
        call(
            "search3",
            mapOf(
                "query" to "",
                "artistCount" to "0",
                "albumCount" to "0",
                "songCount" to "$size",
                "songOffset" to "$offset",
                "musicFolderId" to folderId,
            ),
        ).obj("searchResult3")?.arr("song").orEmpty().map(::parseSong)

    suspend fun search(query: String): SearchResults {
        val result = call(
            "search3",
            mapOf("query" to query, "artistCount" to "20", "albumCount" to "30", "songCount" to "60"),
        ).obj("searchResult3")
        return SearchResults(
            artists = result?.arr("artist").orEmpty().map(::parseArtist),
            albums = result?.arr("album").orEmpty().map(::parseAlbum),
            songs = result?.arr("song").orEmpty().map(::parseSong),
        )
    }

    /** Synced lyrics from the OpenSubsonic extension when available, plain lyrics otherwise. */
    suspend fun lyrics(song: Song): Lyrics? {
        val structured = runCatching { call("getLyricsBySongId", mapOf("id" to song.id)) }.getOrNull()
        val sets = structured?.obj("lyricsList")?.arr("structuredLyrics").orEmpty()
        val pick = sets.firstOrNull { it.bool("synced") == true } ?: sets.firstOrNull()
        if (pick != null) {
            val synced = pick.bool("synced") == true
            val offset = pick.long("offset") ?: 0L
            val lines = pick.arr("line").map { line ->
                LyricLine(
                    startMs = if (synced) line.long("start")?.let { it + offset } else null,
                    text = line.str("value").orEmpty(),
                )
            }
            if (lines.any { it.text.isNotBlank() }) return Lyrics(synced, lines)
        }
        val plain = runCatching {
            call("getLyrics", mapOf("artist" to song.artist, "title" to song.title))
        }.getOrNull()?.obj("lyrics")?.str("value")
        if (plain.isNullOrBlank()) return null
        return Lyrics(false, plain.lines().map { LyricLine(null, it) })
    }

    /** submission=false marks "now playing"; true records a finished play, at [timeMs] when given. */
    suspend fun scrobble(id: String, submission: Boolean, timeMs: Long? = null) {
        call("scrobble", mapOf("id" to id, "submission" to submission.toString(), "time" to timeMs?.toString()))
    }

    /** Saves [songIds], in order, as a new playlist on Navidrome. */
    suspend fun createPlaylist(name: String, songIds: List<String>) {
        call("createPlaylist", mapOf("name" to name), songIds.map { "songId" to it })
    }

    /** Adds songs to the end of one of your playlists. */
    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        call("updatePlaylist", mapOf("playlistId" to playlistId), songIds.map { "songIdToAdd" to it })
    }

    suspend fun star(id: String) {
        call("star", mapOf("id" to id))
    }

    suspend fun unstar(id: String) {
        call("unstar", mapOf("id" to id))
    }

    /** Liked (starred) songs. */
    suspend fun starredSongs(): List<Song> =
        call("getStarred2").obj("starred2")?.arr("song").orEmpty().map(::parseSong)

    private fun parseSong(o: JsonObject): Song {
        val gain = o.obj("replayGain")
        return Song(
            id = o.str("id").orEmpty(),
            title = o.str("title") ?: "Unknown title",
            artist = o.str("artist") ?: o.str("displayArtist") ?: "",
            artistId = o.str("artistId"),
            album = o.str("album").orEmpty(),
            albumId = o.str("albumId"),
            coverArt = o.str("coverArt"),
            durationSec = o.int("duration") ?: 0,
            track = o.int("track"),
            disc = o.int("discNumber"),
            year = o.int("year"),
            genre = o.str("genre"),
            trackGain = gain?.flt("trackGain"),
            albumGain = gain?.flt("albumGain"),
            trackPeak = gain?.flt("trackPeak"),
            albumPeak = gain?.flt("albumPeak"),
            starred = o.str("starred") != null,
            artists = o.arr("artists").mapNotNull { a ->
                val artistId = a.str("id") ?: return@mapNotNull null
                ArtistRef(artistId, a.str("name").orEmpty())
            },
        )
    }

    private fun parseAlbum(o: JsonObject) = Album(
        id = o.str("id").orEmpty(),
        name = o.str("name") ?: o.str("title") ?: "Unknown album",
        artist = o.str("artist") ?: o.str("displayArtist") ?: "",
        artistId = o.str("artistId"),
        coverArt = o.str("coverArt"),
        songCount = o.int("songCount") ?: 0,
        durationSec = o.int("duration") ?: 0,
        year = o.int("year"),
        genre = o.str("genre"),
        created = o.str("created"),
        playCount = o.long("playCount") ?: 0,
        releaseTypes = (o["releaseTypes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
        isCompilation = o.bool("isCompilation") == true,
        played = o.str("played"),
    )

    private fun parsePlaylist(o: JsonObject) = Playlist(
        id = o.str("id").orEmpty(),
        name = o.str("name") ?: "Playlist",
        songCount = o.int("songCount") ?: 0,
        durationSec = o.int("duration") ?: 0,
        coverArt = o.str("coverArt"),
        owner = o.str("owner"),
        comment = o.str("comment"),
        changed = o.str("changed"),
    )

    private fun parseArtist(o: JsonObject) = Artist(
        id = o.str("id").orEmpty(),
        name = o.str("name") ?: "Unknown artist",
        albumCount = o.int("albumCount") ?: 0,
        coverArt = o.str("coverArt"),
        imageUrl = o.str("artistImageUrl"),
    )

    private companion object {
        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
        fun md5(value: String): String =
            MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).toHex()
        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        /** Last.fm biographies come as HTML ending in a "Read more" link. */
        fun cleanBiography(html: String): String = html
            .replace(Regex("<a [^>]*>.*?</a>\\.?", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
            .trim()
    }
}
