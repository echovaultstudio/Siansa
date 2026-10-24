package app.siansa.net

import app.siansa.data.Album
import app.siansa.data.AlbumDetail
import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.Artist
import app.siansa.data.ArtistDetail
import app.siansa.data.ArtistInfo
import app.siansa.data.ArtistRef
import app.siansa.data.LyricLine
import app.siansa.data.Lyrics
import app.siansa.data.LyricsFormats
import app.siansa.data.MusicFolder
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.SearchResults
import app.siansa.data.SimilarArtist
import app.siansa.data.Song
import app.siansa.data.SongDates
import app.siansa.data.SongDatesCache
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant

/**
 * An Emby server (issue #113): its music through Emby's own REST API, behind the same [MusicSource] as Navidrome.
 *
 * - Logs in once with the user name and password (`Users/AuthenticateByName`) and keeps the access token for the
 *   app run; a request the server refuses (HTTP 401) logs in again once.
 * - Song and cover addresses carry the token as `api_key`, since players, Cast and the image cache can't send headers.
 * - Songs play as the untouched original file (`static=true`), like Navidrome's raw stream.
 * - Playback is reported to Emby as it happens (start, progress, pause, stop), so it shows under Emby's
 *   now playing, and songs heard to the end are marked played.
 * - Lyrics come from the song's lyrics streams (Emby 4.8+). Emby has no sound-alike search and no song paths: those come back empty, so the
 *   app hides them like it does for a Navidrome server without them. Similar songs and artist radio use Emby's
 *   Instant Mix.
 */
class EmbyApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
    /** This phone's id for Emby's device list; stays the same between runs. */
    private val deviceId: () -> String = { "siansa" },
    /** The login kept from the last run, so songs and covers play before the first request of this one. */
    private val recall: () -> Login? = { null },
    /** Keeps a new login for the next run. */
    private val remember: (Login) -> Unit = {},
) : MusicSource {
    override val kind: SourceKind = SourceKind.EMBY

    /** The login for the current address + user: the user's id and access token. */
    data class Login(val key: String, val userId: String, val token: String)

    @Volatile private var login: Login? = null

    /** The login for the current settings, from this run or the last one. */
    private fun current(): Login? {
        val key = loginKey(settings())
        login?.takeIf { it.key == key }?.let { return it }
        return recall()?.takeIf { it.key == key }?.also { login = it }
    }

    private fun base(s: AppSettings = settings()) = s.navidromeUrl.trimEnd('/')
    // A new password needs no new login: a token Emby stops taking (HTTP 401) is replaced then.
    private fun loginKey(s: AppSettings) = base(s) + "|" + s.navidromeUser

    private fun authHeader(token: String?): String = buildString {
        append("Emby Client=\"Siansa\", Device=\"Android\", DeviceId=\"").append(deviceId()).append("\", Version=\"1\"")
        if (token != null) append(", Token=\"").append(token).append('"')
    }

    /** Logs in when there's no login yet for the current settings (or [again] after a refusal). */
    private suspend fun session(again: Boolean = false): Login {
        val s = settings()
        if (base(s).isBlank()) throw ApiException("Emby isn't set up yet")
        val key = loginKey(s)
        if (!again) current()?.let { return it }
        val body = buildJsonObject { put("Username", s.navidromeUser); put("Pw", s.navidromePassword) }.toString()
        val root = exchange("POST", "/Users/AuthenticateByName", body, token = null)
            ?: throw ApiException("Emby didn't answer the login")
        val token = root.str("AccessToken") ?: throw ApiException("Emby refused the login")
        val userId = root.obj("User")?.str("Id") ?: throw ApiException("Emby refused the login")
        AppLog.i("Server", "Logged in to Emby as ${s.navidromeUser}")
        return Login(key, userId, token).also { login = it; remember(it) }
    }

    private class Refused : Exception()

    /** One HTTP exchange, as JSON; null for an empty answer. Throws [Refused] for HTTP 401 with a token. */
    private suspend fun exchange(method: String, path: String, body: String?, token: String?): JsonObject? {
        val text = send(method, path, body, token)
        if (text.isBlank()) return null
        val element = try {
            AppJson.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ApiException("That address doesn't look like an Emby server")
        }
        return when (element) {
            is JsonObject -> element
            is JsonArray -> JsonObject(mapOf("Items" to element))
            else -> null
        }
    }

    /** One HTTP exchange, as text. Throws [Refused] for HTTP 401 with a token. */
    private suspend fun send(method: String, path: String, body: String?, token: String?): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(base() + path).header("X-Emby-Authorization", authHeader(token))
            if (token != null) builder.header("X-Emby-Token", token)
            val json = "application/json".toMediaType()
            when (method) {
                "POST" -> builder.post((body ?: "").toRequestBody(json))
                "DELETE" -> builder.delete()
                else -> builder.get()
            }
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 401) {
                    if (token != null) throw Refused()
                    throw ApiException("Emby refused the user name or password")
                }
                if (response.code == 407) throw ApiException("The server asked for a login (HTTP 407). Fill in the proxy login in Settings.")
                if (!response.isSuccessful) {
                    AppLog.w("Server", "Emby $method ${path.substringBefore('?')} answered HTTP ${response.code}")
                    throw ApiException("Emby answered HTTP ${response.code}")
                }
                text
            }
        }

    /** Runs [block] as the logged-in user, with [path]'s `{user}` filled in; logs in again once when refused. */
    private suspend fun <T> asUser(path: String, block: suspend (path: String, token: String) -> T): T {
        fun at(s: Login) = path.replace("{user}", s.userId).replace(USER_ENCODED, s.userId)
        var s = session()
        return try {
            block(at(s), s.token)
        } catch (e: Refused) {
            AppLog.w("Server", "Emby refused the saved login; logging in again")
            s = session(again = true)
            try {
                block(at(s), s.token)
            } catch (e: Refused) {
                throw ApiException("Emby refused the login")
            }
        }
    }

    /** A call as the logged-in user; [path] may use `{user}` for the user's id. */
    private suspend fun call(path: String, method: String = "GET", body: String? = null): JsonObject =
        asUser(path) { at, token -> exchange(method, at, body, token) } ?: JsonObject(emptyMap())

    /** A file's text as the logged-in user (lyrics). */
    private suspend fun text(path: String): String = asUser(path) { at, token -> send("GET", at, null, token) }

    private fun q(vararg params: Pair<String, Any?>): String =
        params.filter { it.second != null }.joinToString("&") { (k, v) -> k + "=" + enc(v.toString()) }

    /** Music items of the user, with the fields Siansa reads. */
    private suspend fun items(vararg params: Pair<String, Any?>): List<JsonObject> =
        call("/Users/{user}/Items?" + q(*params, "Recursive" to "true", "Fields" to FIELDS)).arr("Items")

    // ---- Addresses ----

    private fun mediaToken(): String = current()?.token.orEmpty()

    override fun streamUrl(id: String): String =
        base() + "/Audio/" + enc(id) + "/stream?" + q("Static" to "true", "api_key" to mediaToken())

    /** A cover: Siansa keeps covers as item ids; Navidrome-style "al-" ids from older data are read too. */
    override fun coverUrl(id: String, size: Int?): String {
        val item = id.removePrefix("al-").removePrefix("ar-").removePrefix("pl-")
        return base() + "/Items/" + enc(item) + "/Images/Primary?" +
            q("maxWidth" to size, "maxHeight" to size, "quality" to "90", "api_key" to mediaToken())
    }

    // ---- Server ----

    override suspend fun ping(): String {
        val info = exchange("GET", "/System/Info/Public", null, token = null)
            ?: throw ApiException("That address doesn't look like an Emby server")
        session(again = true)
        val name = info.str("ProductName")?.takeIf { it.isNotBlank() } ?: "Emby Server"
        return "$name ${info.str("Version").orEmpty()}".trim()
    }

    override suspend fun musicFolders(): List<MusicFolder> =
        call("/Users/{user}/Views").arr("Items")
            .filter { it.str("CollectionType") == "music" }
            .mapNotNull { v -> MusicFolder(v.str("Id") ?: return@mapNotNull null, v.str("Name") ?: "Music") }

    override suspend fun hasSonicMatch(): Boolean = false

    /** Emby doesn't say when it last scanned, so every sync looks for changes. */
    override suspend fun lastScan(): String? = null

    // ---- Music ----

    override suspend fun albums(type: String, size: Int, offset: Int): List<Album> = albums(type, size, offset, null)

    override suspend fun albums(type: String, size: Int, offset: Int, folderId: String?): List<Album> {
        val (sortBy, order) = albumSort(type)
        return items(
            "IncludeItemTypes" to "MusicAlbum", "SortBy" to sortBy, "SortOrder" to order,
            "StartIndex" to offset, "Limit" to size, "ParentId" to folderId,
            "Filters" to if (type == "recent") "IsPlayed" else if (type == "starred") "IsFavorite" else null,
        ).map(::parseAlbum)
    }

    override suspend fun album(id: String): AlbumDetail {
        val album = call("/Users/{user}/Items/" + enc(id))
        if (album.str("Id") == null) throw ApiException("Album not found")
        // Emby can't sort by disc and track number (not among its sort fields), so they're put in order here.
        val songs = items("ParentId" to id, "IncludeItemTypes" to "Audio", "SortBy" to "SortName")
            .map(::parseSong)
            .sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }))
        return AlbumDetail(parseAlbum(album), songs)
    }

    override suspend fun artists(folderId: String?): List<Artist> =
        call("/Artists/AlbumArtists?" + q("UserId" to "{user}", "ParentId" to folderId, "Recursive" to "true", "Fields" to FIELDS))
            .arr("Items").map(::parseArtist)

    override suspend fun artist(id: String): ArtistDetail {
        val artist = call("/Users/{user}/Items/" + enc(id))
        if (artist.str("Id") == null) throw ApiException("Artist not found")
        // Emby filters albums by any artist on them (ArtistIds); the artist's own albums are those with them as
        // album artist, with the rest (appearances) only when there are none of those.
        val albums = items("IncludeItemTypes" to "MusicAlbum", "ArtistIds" to id, "SortBy" to "ProductionYear,SortName")
        val own = albums.filter { a -> a.arr("AlbumArtists").any { it.str("Id") == id } }
        return ArtistDetail(parseArtist(artist), own.ifEmpty { albums }.map(::parseAlbum))
    }

    override suspend fun artistInfo(id: String, includeNotPresent: Boolean): ArtistInfo {
        val artist = call("/Users/{user}/Items/" + enc(id))
        val similar = runCatching {
            call("/Items/" + enc(id) + "/Similar?" + q("UserId" to "{user}", "Limit" to "20")).arr("Items")
        }.getOrDefault(emptyList()).mapNotNull { a ->
            val name = a.str("Name") ?: return@mapNotNull null
            SimilarArtist(a.str("Id"), name, a.str("Id")?.takeIf { hasImage(a) }, null)
        }
        return ArtistInfo(
            biography = artist.str("Overview")?.let { SubsonicApi.cleanBiography(it) }?.takeIf { it.isNotBlank() },
            imageUrl = null,
            lastFmUrl = null,
            similar = similar,
        )
    }

    override suspend fun topSongs(artistName: String, count: Int): List<Song> {
        val artist = call("/Artists/" + enc(artistName) + "?" + q("UserId" to "{user}"))
        val id = artist.str("Id") ?: return emptyList()
        return items(
            "IncludeItemTypes" to "Audio", "ArtistIds" to id, "SortBy" to "PlayCount,CommunityRating,SortName",
            "SortOrder" to "Descending", "Limit" to count,
        ).map(::parseSong)
    }

    override suspend fun song(id: String): Song {
        val o = call("/Users/{user}/Items/" + enc(id))
        if (o.str("Id") == null) throw ApiException("Song not found")
        return parseSong(o)
    }

    override suspend fun songDetails(id: String): SongDetails {
        val o = call("/Users/{user}/Items/" + enc(id) + "?" + q("Fields" to "$FIELDS,MediaSources,Path"))
        val source = o.arr("MediaSources").firstOrNull()
        val audio = source?.arr("MediaStreams")?.firstOrNull { it.str("Type") == "Audio" }
        return SongDetails(
            codec = codecName(source?.str("Container") ?: o.str("Container"), null),
            bitRateKbps = (audio?.int("BitRate") ?: source?.int("Bitrate"))?.let { it / 1000 }?.takeIf { it > 0 },
            sampleRateHz = audio?.int("SampleRate")?.takeIf { it > 0 },
            bitDepth = audio?.int("BitDepth")?.takeIf { it > 0 },
            channels = audio?.int("Channels")?.takeIf { it > 0 },
            sizeBytes = source?.long("Size")?.takeIf { it > 0 },
            path = (source?.str("Path") ?: o.str("Path"))?.takeIf { it.isNotBlank() },
            playCount = o.obj("UserData")?.long("PlayCount"),
        )
    }

    override suspend fun randomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> {
        // Emby takes several years comma-separated; several genres would be "|"-separated, one is all Siansa sends.
        val years = if (fromYear != null || toYear != null) {
            val from = fromYear ?: 1900
            val to = toYear ?: java.time.Year.now().value
            (minOf(from, to)..maxOf(from, to)).joinToString(",")
        } else null
        return items("IncludeItemTypes" to "Audio", "SortBy" to "Random", "Limit" to size, "Genres" to genre, "Years" to years)
            .map(::parseSong)
    }

    override suspend fun randomSongPlays(size: Int): List<Pair<Song, Long>> =
        items("IncludeItemTypes" to "Audio", "SortBy" to "Random", "Limit" to size)
            .map { parseSong(it) to (it.obj("UserData")?.long("PlayCount") ?: 0L) }

    override suspend fun songsPage(offset: Int, size: Int, folderId: String?): List<Song> =
        items(
            "IncludeItemTypes" to "Audio", "SortBy" to "SortName", "StartIndex" to offset, "Limit" to size,
            "ParentId" to folderId,
        ).map(::parseSong)

    override suspend fun search(query: String): SearchResults {
        val found = items("SearchTerm" to query, "IncludeItemTypes" to "Audio,MusicAlbum,MusicArtist", "Limit" to "60")
        return SearchResults(
            artists = found.filter { it.str("Type") == "MusicArtist" }.map(::parseArtist),
            albums = found.filter { it.str("Type") == "MusicAlbum" }.map(::parseAlbum),
            songs = found.filter { it.str("Type") == "Audio" }.map(::parseSong),
        )
    }

    /**
     * Lyrics (Emby 4.8 and later): Emby lists a song's lyrics (an .lrc or .txt next to the file, or embedded) as
     * subtitle streams of the song, read through its subtitle address. Several (languages): the most detailed.
     */
    override suspend fun lyrics(song: Song): Lyrics? {
        val item = call("/Users/{user}/Items/" + enc(song.id) + "?" + q("Fields" to "MediaSources,MediaStreams"))
        val source = item.arr("MediaSources").firstOrNull()
        val sourceId = source?.str("Id") ?: song.id
        val streams = (source?.arr("MediaStreams").orEmpty().ifEmpty { item.arr("MediaStreams") })
            .filter { it.str("Type") == "Subtitle" || it.str("Type") == "Lyrics" }
        if (streams.isEmpty()) return null
        val options = streams.mapNotNull { stream ->
            val index = stream.int("Index") ?: return@mapNotNull null
            val raw = runCatching {
                text("/Items/" + enc(song.id) + "/" + enc(sourceId) + "/Subtitles/$index/Stream." + lyricsFormat(stream.str("Codec")))
            }.onFailure { AppLog.w("Lyrics", "Emby lyrics ${song.id} #$index not read: ${it.message}") }.getOrNull()
            readLyrics(raw)?.let { stream.str("Language") to it }
        }
        return LyricsFormats.best(options)
    }

    // ---- Similar ----

    override suspend fun similarSongs(songId: String, count: Int): List<Song> =
        call("/Songs/" + enc(songId) + "/InstantMix?" + q("UserId" to "{user}", "Limit" to count, "Fields" to FIELDS))
            .arr("Items").map(::parseSong).filter { it.id != songId }

    override suspend fun artistRadio(artistId: String, count: Int): List<Song> =
        call("/Items/" + enc(artistId) + "/InstantMix?" + q("UserId" to "{user}", "Limit" to count, "Fields" to FIELDS))
            .arr("Items").map(::parseSong)

    override suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>> = emptyList()
    override suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song> = emptyList()

    // ---- Playlists ----

    override suspend fun playlists(): List<Playlist> =
        items("IncludeItemTypes" to "Playlist", "SortBy" to "SortName")
            .filter { it.str("MediaType").let { type -> type == null || type == "Audio" } }
            .map(::parsePlaylist)

    private suspend fun entries(playlistId: String): List<JsonObject> =
        call("/Playlists/" + enc(playlistId) + "/Items?" + q("UserId" to "{user}", "Fields" to FIELDS)).arr("Items")

    override suspend fun playlist(id: String): PlaylistDetail {
        val playlist = call("/Users/{user}/Items/" + enc(id))
        if (playlist.str("Id") == null) throw ApiException("Playlist not found")
        val songs = entries(id).map(::parseSong)
        return PlaylistDetail(parsePlaylist(playlist).copy(songCount = songs.size, durationSec = songs.sumOf { it.durationSec }), songs)
    }

    override suspend fun createPlaylist(name: String, songIds: List<String>): Playlist? {
        val made = call(
            "/Playlists?" + q("Name" to name, "Ids" to songIds.joinToString(",").ifEmpty { null }, "MediaType" to "Audio"),
            method = "POST",
        )
        val id = made.str("Id") ?: return null
        return Playlist(id = id, name = name, songCount = songIds.size)
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        // Emby updates an item from the whole item, so it's read first and sent back with the new name.
        val item = call("/Users/{user}/Items/" + enc(playlistId))
        val changed = JsonObject(item + ("Name" to JsonPrimitive(name)))
        call("/Items/" + enc(playlistId), method = "POST", body = changed.toString())
    }

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) {
        val entries = entries(playlistId)
        val ids = indexes.mapNotNull { entries.getOrNull(it)?.str("PlaylistItemId") }
        if (ids.isEmpty()) return
        call("/Playlists/" + enc(playlistId) + "/Items?" + q("EntryIds" to ids.joinToString(",")), method = "DELETE")
    }

    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        // In groups, so the address stays short enough for proxies.
        songIds.chunked(ADD_CHUNK).forEach { group ->
            call("/Playlists/" + enc(playlistId) + "/Items?" + q("Ids" to group.joinToString(","), "UserId" to "{user}"), method = "POST")
        }
    }

    // ---- Favourites ----

    private suspend fun favourite(id: String, on: Boolean) {
        call("/Users/{user}/FavoriteItems/" + enc(id), method = if (on) "POST" else "DELETE")
    }

    override suspend fun star(id: String) = favourite(id, true)
    override suspend fun unstar(id: String) = favourite(id, false)
    override suspend fun starAlbum(albumId: String) = favourite(albumId, true)
    override suspend fun unstarAlbum(albumId: String) = favourite(albumId, false)
    override suspend fun starArtist(artistId: String) = favourite(artistId, true)
    override suspend fun unstarArtist(artistId: String) = favourite(artistId, false)

    override suspend fun starredAlbums(): List<Album> =
        items("IncludeItemTypes" to "MusicAlbum", "Filters" to "IsFavorite", "SortBy" to "SortName").map(::parseAlbum)

    override suspend fun starredSongs(): List<Song> =
        items("IncludeItemTypes" to "Audio", "Filters" to "IsFavorite", "SortBy" to "SortName").map(::parseSong)

    override suspend fun starredArtists(): List<Artist> =
        items("IncludeItemTypes" to "MusicArtist", "Filters" to "IsFavorite", "SortBy" to "SortName").map(::parseArtist)

    // ---- Listening ----

    override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) {
        if (submission) {
            val at = Instant.ofEpochMilli(timeMs ?: System.currentTimeMillis()).toString()
            call("/Users/{user}/PlayedItems/" + enc(id) + "?" + q("DatePlayed" to at), method = "POST")
        } else {
            call("/Sessions/Playing", method = "POST", body = timeline(id, 0, paused = false, event = null))
        }
    }

    /** Emby's now-playing timeline: where the song is, and whether it's paused. */
    override suspend fun playbackProgress(id: String, positionMs: Long, paused: Boolean) {
        call("/Sessions/Playing/Progress", method = "POST", body = timeline(id, positionMs, paused, if (paused) "Pause" else "TimeUpdate"))
    }

    override suspend fun playbackStopped(id: String, positionMs: Long) {
        call("/Sessions/Playing/Stopped", method = "POST", body = timeline(id, positionMs, paused = false, event = null))
    }

    private fun timeline(id: String, positionMs: Long, paused: Boolean, event: String?): String = buildJsonObject {
        put("ItemId", id)
        put("PositionTicks", positionMs.coerceAtLeast(0) * TICKS_PER_MS)
        put("IsPaused", paused)
        put("CanSeek", true)
        put("PlayMethod", "DirectStream")
        if (event != null) put("EventName", event)
    }.toString()

    // ---- Reading Emby's items ----

    private fun hasImage(o: JsonObject): Boolean = o.obj("ImageTags")?.containsKey("Primary") == true

    /** The id whose picture is the song's cover: its own, else its album's. */
    private fun songCover(o: JsonObject): String? = if (hasImage(o)) o.str("Id") else o.str("AlbumId")

    internal fun parseSong(o: JsonObject): Song {
        val user = o.obj("UserData")
        val id = o.str("Id").orEmpty()
        SongDatesCache.remember(id, SongDates(null, o.str("DateCreated"), user?.str("LastPlayedDate")))
        val artistItems = o.arr("ArtistItems").mapNotNull { a -> ArtistRef(a.str("Id") ?: return@mapNotNull null, a.str("Name").orEmpty()) }
        val albumArtist = o.arr("AlbumArtists").firstOrNull()
        return Song(
            id = id,
            title = o.str("Name") ?: "Unknown title",
            artist = names(o["Artists"]).joinToString(", ").ifEmpty { o.str("AlbumArtist").orEmpty() },
            artistId = artistItems.firstOrNull()?.id ?: albumArtist?.str("Id"),
            album = o.str("Album").orEmpty(),
            albumId = o.str("AlbumId"),
            coverArt = songCover(o),
            durationSec = ticksToSec(o.long("RunTimeTicks")),
            track = o.int("IndexNumber"),
            disc = o.int("ParentIndexNumber"),
            year = o.int("ProductionYear"),
            genre = names(o["Genres"]).firstOrNull(),
            starred = user?.bool("IsFavorite") == true,
            artists = artistItems,
            suffix = o.str("Container")?.lowercase()?.takeIf { it.isNotBlank() },
        )
    }

    internal fun parseAlbum(o: JsonObject): Album {
        val albumArtist = o.arr("AlbumArtists").firstOrNull()
        val user = o.obj("UserData")
        return Album(
            id = o.str("Id").orEmpty(),
            name = o.str("Name") ?: "Unknown album",
            artist = o.str("AlbumArtist") ?: albumArtist?.str("Name").orEmpty(),
            artistId = albumArtist?.str("Id"),
            coverArt = o.str("Id")?.takeIf { hasImage(o) },
            songCount = o.int("ChildCount") ?: o.int("SongCount") ?: 0,
            durationSec = ticksToSec(o.long("RunTimeTicks")),
            year = o.int("ProductionYear"),
            genre = names(o["Genres"]).firstOrNull(),
            created = o.str("DateCreated"),
            playCount = user?.long("PlayCount") ?: 0,
            played = user?.str("LastPlayedDate"),
        )
    }

    internal fun parseArtist(o: JsonObject) = Artist(
        id = o.str("Id").orEmpty(),
        name = o.str("Name") ?: "Unknown artist",
        albumCount = o.int("AlbumCount") ?: o.int("ChildCount") ?: 0,
        coverArt = o.str("Id")?.takeIf { hasImage(o) },
        mbid = o.obj("ProviderIds")?.str("MusicBrainzArtist")?.trim()?.takeIf { it.length == 36 },
    )

    internal fun parsePlaylist(o: JsonObject) = Playlist(
        id = o.str("Id").orEmpty(),
        name = o.str("Name") ?: "Playlist",
        songCount = o.int("ChildCount") ?: o.int("SongCount") ?: 0,
        durationSec = ticksToSec(o.long("RunTimeTicks")),
        coverArt = o.str("Id")?.takeIf { hasImage(o) },
        comment = o.str("Overview"),
        changed = o.str("DateLastMediaAdded") ?: o.str("DateCreated"),
    )

    internal companion object {
        /** The extra fields Siansa reads on lists (Emby leaves most out unless asked). */
        const val FIELDS = "Genres,DateCreated,ProductionYear,ChildCount,ProviderIds,Overview,Container,AlbumArtist,ParentId"

        /** `{user}` once it has been through [enc] as a parameter value. */
        const val USER_ENCODED = "%7Buser%7D"

        /** Songs added to a playlist per request. */
        const val ADD_CHUNK = 100

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

        /** The format to ask Emby for: the lyrics' own when Siansa reads it, else plain text. */
        fun lyricsFormat(codec: String?): String = when (val c = codec?.lowercase()?.trim()) {
            "lrc", "srt", "vtt", "ttml" -> c
            "subrip" -> "srt"
            "webvtt" -> "vtt"
            else -> "txt"
        }

        /** Lyrics from Emby's text: timed when it is LRC or subtitles, else plain lines; null when empty. */
        fun readLyrics(raw: String?): Lyrics? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return LyricsFormats.parse(text) ?: Lyrics(false, text.lines().map { LyricLine(null, it.trimEnd()) })
        }

        /** Emby's time ticks (100 nanoseconds) in a millisecond. */
        const val TICKS_PER_MS = 10_000L

        /** Emby counts time in ticks of 100 nanoseconds. */
        fun ticksToSec(ticks: Long?): Int = ((ticks ?: 0L) / 10_000_000L).toInt()

        private fun names(e: JsonElement?): List<String> =
            (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }.orEmpty()

        /** Navidrome's album list kinds as Emby's sort: newest, recent, frequent, random, by name, by year. */
        fun albumSort(type: String): Pair<String, String> = when (type) {
            "newest" -> "DateCreated,SortName" to "Descending"
            "recent" -> "DatePlayed,SortName" to "Descending"
            "frequent" -> "PlayCount,SortName" to "Descending"
            "random" -> "Random" to "Ascending"
            "byYear" -> "ProductionYear,SortName" to "Ascending"
            "highest" -> "CommunityRating,SortName" to "Descending"
            else -> "SortName" to "Ascending"
        }
    }
}
