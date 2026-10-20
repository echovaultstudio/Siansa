package app.musix.net

import app.musix.data.SongDates
import app.musix.data.SongDatesCache
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
import app.musix.log.AppLog
import app.musix.data.LyricsFormats
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Navidrome through the Subsonic / OpenSubsonic API (issue #206):
 * - Asks the server once which OpenSubsonic extensions it has ([extensions]) and only uses those.
 * - Token login with a new salt for every API call. Song and cover addresses keep one salt per app run, since
 *   players, the cast receiver and the image cache hold on to them (their cache keys leave the login out).
 * - When Navidrome refuses token login for the user (error 41 or 42, e.g. LDAP or reverse-proxy users), the
 *   password is sent hex-encoded instead (`p=enc:…`), as the spec says to retry with another method.
 * - Long requests (playlists with many songs) go as a form POST when the server has `formPost`.
 * - Navidrome's error codes become plain messages ([SubsonicErrors]).
 */
class SubsonicApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    // Song and cover addresses: one salt per app run (see above).
    private val mediaSalt: String = newSalt()
    private var mediaToken: Pair<String, String>? = null

    /**
     * The libraries chosen in Settings → Library sync, empty for all. Only the app's own login sets it; lists
     * asked for straight from Navidrome are then kept to those libraries, like the synced library (issue #206).
     */
    @Volatile var chosenFolders: () -> Set<String> = { emptySet() }

    /**
     * The login for a request about an item (by its id) when the item is another shown user's (issue #141):
     * their songs stream, download, show covers and scrobble with their own login, from their server.
     */
    @Volatile var loginFor: (String?) -> AppSettings? = { null }

    /** Servers (address + user) that refused token login, so the password is sent instead. */
    private val passwordLogin = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun loginKey(s: AppSettings) = s.navidromeUrl.trimEnd('/') + "|" + s.navidromeUser

    private fun newSalt(): String = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()

    /** md5(password + salt); the media salt's token is kept, since covers ask for it all the time. */
    private fun token(password: String, salt: String): String {
        if (salt != mediaSalt) return md5(password + salt)
        mediaToken?.let { if (it.first == password) return it.second }
        return md5(password + salt).also { mediaToken = password to it }
    }

    /** The login parameters: u plus t and s, or p=enc: when this server refused tokens. */
    private fun auth(s: AppSettings, salt: String): String = buildString {
        append("u=").append(enc(s.navidromeUser))
        if (loginKey(s) in passwordLogin) {
            append("&p=enc:").append(s.navidromePassword.toByteArray(Charsets.UTF_8).toHex())
        } else {
            append("&t=").append(token(s.navidromePassword, salt)).append("&s=").append(salt)
        }
        append("&v=1.16.1&c=musix&f=json")
    }

    private fun query(params: Map<String, String?>, repeated: List<Pair<String, String>>): String = buildString {
        for ((key, value) in params) {
            if (value != null) append('&').append(enc(key)).append('=').append(enc(value))
        }
        for ((key, value) in repeated) append('&').append(enc(key)).append('=').append(enc(value))
    }

    private fun loginOf(params: Map<String, String?>) = loginFor(params["id"] ?: params["playlistId"]) ?: settings()

    /** A song or cover address (one salt per app run, see above). */
    fun url(endpoint: String, params: Map<String, String?> = emptyMap(), repeated: List<Pair<String, String>> = emptyList()): String {
        val s = loginOf(params)
        return s.navidromeUrl.trimEnd('/') + "/rest/" + endpoint + "?" + auth(s, mediaSalt) + query(params, repeated)
    }

    /** The untouched original file (format=raw), whatever Navidrome's own transcoding defaults say. */
    fun streamUrl(id: String): String = url("stream", mapOf("id" to id, "format" to "raw"))

    fun coverUrl(id: String, size: Int?): String =
        url("getCoverArt", mapOf("id" to id, "size" to size?.toString()))

    // ---- OpenSubsonic extensions ----

    /** Each server's extensions (name → versions), asked once per app run. */
    private val known = java.util.concurrent.ConcurrentHashMap<String, Map<String, List<Int>>>()

    /**
     * The OpenSubsonic extensions this server has, by name with their versions; empty for a plain Subsonic
     * server or when it couldn't be asked (then nothing optional is used).
     */
    suspend fun extensions(): Map<String, List<Int>> {
        val s = settings()
        val key = s.navidromeUrl.trimEnd('/')
        known[key]?.let { return it }
        val found = try {
            parseExtensions(call("getOpenSubsonicExtensions"))
        } catch (e: ApiException) {
            // Navidrome answered, but without the list: a plain Subsonic server.
            AppLog.w("Server", "No OpenSubsonic extensions: ${e.message}")
            emptyMap()
        } catch (e: java.io.IOException) {
            // Not reachable now: ask again next time.
            return emptyMap()
        }
        known[key] = found
        AppLog.i("Server", "OpenSubsonic extensions: " + found.entries.joinToString(", ") { "${it.key} ${it.value.joinToString("/")}" }.ifEmpty { "none" })
        return found
    }

    suspend fun supports(name: String, version: Int = 1): Boolean = extensions()[name]?.contains(version) == true

    // ---- Calls ----

    private suspend fun call(
        endpoint: String,
        params: Map<String, String?> = emptyMap(),
        repeated: List<Pair<String, String>> = emptyList(),
    ): JsonObject {
        if (settings().navidromeUrl.isBlank()) throw ApiException("Navidrome isn't set up yet")
        // A long list of songs as a form POST, so the address doesn't get too long for proxies.
        val post = repeated.size > POST_OVER && endpoint != "getOpenSubsonicExtensions" && supports("formPost")
        return try {
            send(endpoint, params, repeated, post)
        } catch (e: ApiException) {
            val s = loginOf(params)
            if (e.code != 41 && e.code != 42 || loginKey(s) in passwordLogin) throw e
            // Token login refused for this user: the spec's next method is the (hex-encoded) password.
            AppLog.w("Server", "Navidrome refused token login (error ${e.code}); sending the password instead")
            passwordLogin += loginKey(s)
            send(endpoint, params, repeated, post)
        }
    }

    private suspend fun send(
        endpoint: String,
        params: Map<String, String?>,
        repeated: List<Pair<String, String>>,
        post: Boolean,
    ): JsonObject = withContext(Dispatchers.IO) {
        val s = loginOf(params)
        val base = s.navidromeUrl.trimEnd('/') + "/rest/" + endpoint
        // A new salt for every call, as the spec asks.
        val fields = auth(s, newSalt()) + query(params, repeated)
        val request = if (post) {
            Request.Builder().url(base)
                .post(fields.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
        } else {
            Request.Builder().url("$base?$fields").build()
        }
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
                val error = root.obj("error")
                val code = error?.int("code")
                val message = error?.str("message")
                AppLog.w("Server", "$endpoint failed: error ${code ?: "?"} ${message.orEmpty()}".trim())
                throw ApiException(SubsonicErrors.message(code, message), code)
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
    suspend fun albums(type: String, size: Int, offset: Int, folderId: String?): List<Album> =
        call(
            "getAlbumList2",
            mapOf("type" to type, "size" to "$size", "offset" to "$offset", "musicFolderId" to folderId),
        ).obj("albumList2")?.arr("album").orEmpty().map(::parseAlbum)

    /** Albums of the libraries chosen in Settings (all when none are): one call per chosen library, merged. */
    suspend fun albums(type: String, size: Int, offset: Int): List<Album> {
        val folders = chosenFolders()
        if (folders.size <= 1) return albums(type, size, offset, folders.firstOrNull())
        val all = folders.flatMap { albums(type, size + offset, 0, it) }.distinctBy { it.id }
        return mergeAlbumLists(type, all).drop(offset).take(size)
    }

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

    /**
     * Songs that sound like this one through Navidrome's own sonic similarity (OpenSubsonic `sonicSimilarity`,
     * the AudioMuse-AI Navidrome plugin), with their similarity (1 = the same song). Issue #208.
     */
    suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>> =
        parseSonicMatches(call("getSonicSimilarTracks", mapOf("id" to songId, "count" to "$count")))

    /** A song path through Navidrome's sonic similarity (`findSonicPath`), both ends included. */
    suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song> =
        parseSonicMatches(call("findSonicPath", mapOf("startSongId" to startId, "endSongId" to endId, "count" to "$count")))
            .map { it.first }

    private fun parseSonicMatches(root: JsonObject): List<Pair<Song, Double>> =
        root.arr("sonicMatch").mapNotNull { m ->
            val entry = m.obj("entry") ?: return@mapNotNull null
            parseSong(entry) to (m.dbl("similarity") ?: 0.0)
        }.filter { it.first.id.isNotEmpty() }

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
        val parts = folderPasses().map { folder ->
            call(
                "search3",
                mapOf("query" to query, "artistCount" to "20", "albumCount" to "30", "songCount" to "60", "musicFolderId" to folder),
            ).obj("searchResult3")
        }
        return SearchResults(
            artists = parts.flatMap { it?.arr("artist").orEmpty() }.map(::parseArtist).distinctBy { it.id },
            albums = parts.flatMap { it?.arr("album").orEmpty() }.map(::parseAlbum).distinctBy { it.id },
            songs = parts.flatMap { it?.arr("song").orEmpty() }.map(::parseSong).distinctBy { it.id },
        )
    }

    /** One pass per chosen library, or a single pass over all of them (null). */
    private fun folderPasses(): List<String?> = chosenFolders().toList().ifEmpty { listOf(null) }

    /** Synced lyrics from the OpenSubsonic extension when available, plain lyrics otherwise. */
    suspend fun lyrics(song: Song): Lyrics? {
        // Navidrome reads lyrics from the file's tags and from .lrc / .txt files next to it, sometimes several
        // (languages, or both synced and plain). Each is read in whatever format it's in (LRC, enhanced LRC
        // with word times, TTML, SRT…), and the most detailed one is used.
        // Only what the server says it has (issue #206): enhanced=true (word and syllable timings) with songLyrics
        // version 2, the structured lyrics with version 1, otherwise only Subsonic's plain getLyrics.
        val versions = extensions()["songLyrics"].orEmpty()
        val structured = when {
            2 in versions -> runCatching { call("getLyricsBySongId", mapOf("id" to song.id, "enhanced" to "true")) }.getOrNull()
            versions.isNotEmpty() -> runCatching { call("getLyricsBySongId", mapOf("id" to song.id)) }.getOrNull()
            else -> null
        }
        val sets = structured?.obj("lyricsList")?.arr("structuredLyrics").orEmpty()
        val options = sets.mapNotNull { set ->
            structuredLyrics(set)?.let { (lang, lyrics) -> lang to LyricsFormats.normalize(lyrics) }
        }
        LyricsFormats.best(options, java.util.Locale.getDefault().language)?.let { picked ->
            AppLog.d("Lyrics", "Song ${song.id}: ${options.size} lyrics, using " + when {
                picked.wordTimed -> "word-timed"
                picked.synced -> "synced"
                else -> "plain"
            })
            return picked
        }
        val plain = runCatching {
            call("getLyrics", mapOf("artist" to song.artist, "title" to song.title))
        }.getOrNull()?.obj("lyrics")?.str("value")
        if (plain.isNullOrBlank()) return null
        return LyricsFormats.normalize(Lyrics(false, plain.lines().map { LyricLine(null, it) }))
    }

    /** submission=false marks "now playing"; true records a finished play, at [timeMs] when given. */
    suspend fun scrobble(id: String, submission: Boolean, timeMs: Long? = null) {
        call("scrobble", mapOf("id" to id, "submission" to submission.toString(), "time" to timeMs?.toString()))
    }

    /** Saves [songIds], in order, as a new playlist on Navidrome; the new playlist, when the server sends it back. */
    suspend fun createPlaylist(name: String, songIds: List<String>): Playlist? =
        call("createPlaylist", mapOf("name" to name), songIds.map { "songId" to it }).obj("playlist")?.let(::parsePlaylist)

    suspend fun renamePlaylist(playlistId: String, name: String) {
        call("updatePlaylist", mapOf("playlistId" to playlistId, "name" to name))
    }

    /** Removes the songs at these positions (from 0, in the playlist's own order). */
    suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) {
        call("updatePlaylist", mapOf("playlistId" to playlistId), indexes.map { "songIndexToRemove" to "$it" })
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
        folderPasses().flatMap { folder ->
            call("getStarred2", mapOf("musicFolderId" to folder)).obj("starred2")?.arr("song").orEmpty().map(::parseSong)
        }.distinctBy { it.id }

    private fun parseSong(o: JsonObject): Song {
        val gain = o.obj("replayGain")
        // Kept aside for sorting lists by date liked, added or played (issue #257).
        o.str("id")?.let { SongDatesCache.remember(it, SongDates(o.str("starred"), o.str("created"), o.str("played"))) }
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
        readonly = o.bool("readonly"),
    )

    private fun parseArtist(o: JsonObject) = Artist(
        id = o.str("id").orEmpty(),
        name = o.str("name") ?: "Unknown artist",
        albumCount = o.int("albumCount") ?: 0,
        coverArt = o.str("coverArt"),
        imageUrl = o.str("artistImageUrl"),
    )

    internal companion object {
        /** More repeated parameters than this (songs in a playlist call) go as a form POST when possible. */
        const val POST_OVER = 20

        /** `openSubsonicExtensions: [{name, versions: [1, 2]}]` as name → versions. */
        fun parseExtensions(root: JsonObject): Map<String, List<Int>> =
            root.arr("openSubsonicExtensions").mapNotNull { e ->
                val name = e.str("name") ?: return@mapNotNull null
                val versions = (e["versions"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty()
                name to versions
            }.toMap()

        /** Albums from several libraries in the order [type] asks for (the server sorted each library's own). */
        fun mergeAlbumLists(type: String, albums: List<Album>): List<Album> = when (type) {
            "newest" -> albums.sortedByDescending { it.created.orEmpty() }
            "recent" -> albums.sortedByDescending { it.played.orEmpty() }
            "frequent" -> albums.sortedByDescending { it.playCount }
            "random" -> albums.shuffled()
            "alphabeticalByName" -> albums.sortedBy { it.name.lowercase() }
            "alphabeticalByArtist" -> albums.sortedBy { it.artist.lowercase() }
            else -> albums
        }

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
