package app.siansa.data

import app.siansa.log.AppLog
import app.siansa.net.ApiException
import app.siansa.net.PodcastFeeds
import app.siansa.net.SourceKind
import app.siansa.net.StationPictures
import app.siansa.net.StreamLinks
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.util.UUID

/**
 * Your Stations (issue #353): the music server's radio stations first (Navidrome's list, kept here for when it can't
 * be reached), then the stations and podcasts added on this phone. Links are looked into once when added: a playlist
 * (.m3u, .pls) gives the stream it points at, an RSS feed becomes a podcast. Feeds and links use the app's own HTTP
 * client; their bodies are never logged.
 *
 * Each radio station's picture is looked for once in the background ([findPicturesSoon], see [StationPictures]) and
 * kept with it: in stations.json for those added here, in station_pictures.json (by stream address) for the server's.
 */
class StationRepository(
    filesDir: File,
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
    private val pictureSources: StationPictures.Sources,
    private val scope: CoroutineScope,
    private val online: () -> Boolean = { true },
    private val serverList: suspend () -> List<Station>,
) {
    private val own = JsonFile(File(filesDir, Users.scoped("stations.json")), OwnStations.serializer()) { OwnStations() }
    private val lastServer = JsonFile(File(filesDir, Users.scoped("server_stations.json")), ListSerializer(Station.serializer())) { emptyList() }
    private val serverPictures = JsonFile(
        File(filesDir, Users.scoped("station_pictures.json")),
        MapSerializer(String.serializer(), StationPicture.serializer()),
    ) { emptyMap() }
    private val finding = Mutex()

    /** What's been added on this phone; null until first read. */
    val state: StateFlow<OwnStations?> = own.state

    /** The pictures of the server's stations, by stream address; null until first read. */
    val pictures: StateFlow<Map<String, StationPicture>?> = serverPictures.state

    suspend fun mine(): OwnStations = own.read()

    /** Whether the music server can have radio stations (OpenSubsonic: Navidrome and the like). */
    fun serverHasStations(): Boolean = settings().let { it.sourceKind == SourceKind.OPENSUBSONIC && it.navidromeUrl.isNotBlank() }

    /** The server's stations as last read, without asking it. */
    suspend fun knownServerStations(): List<Station> =
        if (serverHasStations()) withPictures(lastServer.read(), serverPictures.read()) else emptyList()

    /** The server's stations, asked afresh; the last list when it can't be reached. */
    suspend fun serverStations(): List<Station> {
        if (!serverHasStations()) return emptyList()
        return try {
            val list = serverList()
            lastServer.update { list }
            // Pictures of stations the server no longer has go.
            val streams = list.map { it.streamUrl }.toSet()
            if (serverPictures.read().keys.any { it !in streams }) serverPictures.update { m -> m.filterKeys { it in streams } }
            AppLog.i("Stations", "Server has ${list.size} radio station(s)")
            withPictures(list, serverPictures.read())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLog.w("Stations", "Server radio stations not loaded: ${e.message}")
            withPictures(lastServer.read(), serverPictures.read())
        }
    }

    suspend fun podcast(id: String): PodcastFeed? = mine().podcasts.firstOrNull { it.id == id }

    /** What a link that was added turned out to be. */
    sealed interface Added {
        data class Radio(val station: Station) : Added
        data class Podcast(val feed: PodcastFeed) : Added
    }

    /**
     * Adds [link] (a stream, a playlist that points at one, or a podcast's RSS feed), named [name] when one was given.
     * Fails with a plain message when it can't be opened or isn't any of those.
     */
    suspend fun add(link: String, name: String?): Result<Added> = runCatching {
        val clean = StreamLinks.clean(link) ?: throw ApiException("That isn't a web link")
        val existing = mine()
        existing.stations.firstOrNull { it.link == clean || it.streamUrl == clean }?.let { throw ApiException("“${it.name}” is in Your Library already") }
        existing.podcasts.firstOrNull { it.feedUrl == clean }?.let { throw ApiException("“${it.title}” is in Your Library already") }
        val now = System.currentTimeMillis()
        when (val found = inspect(clean)) {
            is Found.Feed -> {
                val feed = PodcastFeed(
                    id = "pod-" + PodcastFeeds.idOf(clean),
                    feedUrl = clean,
                    title = name?.trim()?.takeIf { it.isNotEmpty() } ?: found.feed.title,
                    author = found.feed.author,
                    image = found.feed.image,
                    description = found.feed.description,
                    episodes = found.feed.episodes,
                    addedAt = now,
                    checkedAt = now,
                )
                own.update { it.copy(podcasts = listOf(feed) + it.podcasts) }
                AppLog.i("Stations", "Added podcast \"${feed.title}\" with ${feed.episodes.size} episode(s)")
                Added.Podcast(feed)
            }
            is Found.Stream -> {
                val station = Station(
                    id = "own-" + UUID.randomUUID().toString(),
                    name = name?.trim()?.takeIf { it.isNotEmpty() } ?: found.title ?: nameFrom(clean),
                    streamUrl = found.url,
                    link = clean,
                    addedAt = now,
                )
                own.update { it.copy(stations = listOf(station) + it.stations) }
                AppLog.i("Stations", "Added radio station \"${station.name}\"" + if (found.url != clean) " (from a playlist link)" else "")
                findPicturesSoon()
                Added.Radio(station)
            }
        }
    }.onFailure { AppLog.w("Stations", "Adding a link failed: ${it.message}") }

    /**
     * Changes a station added on this phone, keeping its place in the list. A changed [link] is looked into as when
     * adding; a [pictureLink] is used as the picture, and a blank one finds the picture again by itself.
     */
    suspend fun editStation(id: String, link: String, name: String, pictureLink: String): Result<Station> = runCatching {
        val old = mine().stations.firstOrNull { it.id == id } ?: throw ApiException("That station isn't in Your Library any more")
        val clean = StreamLinks.clean(link) ?: throw ApiException("That isn't a web link")
        val picture = cleanPicture(pictureLink)
        var stream = old.streamUrl
        var title: String? = null
        if (clean != old.link) {
            mine().stations.firstOrNull { it.id != id && (it.link == clean || it.streamUrl == clean) }
                ?.let { throw ApiException("“${it.name}” is in Your Library already") }
            when (val found = inspect(clean)) {
                is Found.Stream -> { stream = found.url; title = found.title }
                is Found.Feed -> throw ApiException("That link is a podcast feed: add it as a podcast")
            }
        }
        val edited = edited(old, name, clean, stream, title, picture, System.currentTimeMillis())
        own.update { o -> o.copy(stations = replaced(o.stations, edited)) }
        AppLog.i("Stations", "Edited radio station \"${edited.name}\"" +
            (if (clean != old.link) ", new link" else "") + (if (picture != null) ", own picture link" else ", picture to be found"))
        if (edited.pictureCheckedAt == 0L) findPicturesSoon()
        edited
    }.onFailure { AppLog.w("Stations", "Editing a station failed: ${it.message}") }

    /**
     * Sets the picture of one of the server's stations, kept on this phone (the server's own list can't be changed
     * from here). A blank [pictureLink] finds the picture again by itself.
     */
    suspend fun editServerPicture(station: Station, pictureLink: String): Result<Unit> = runCatching {
        val picture = cleanPicture(pictureLink)
        val now = System.currentTimeMillis()
        serverPictures.update { m ->
            val old = m[station.streamUrl]
            val next = if (picture != null) StationPicture(image = picture, homePage = old?.homePage, pictureLink = picture, checkedAt = now)
            else StationPicture(image = old?.takeIf { it.pictureLink == null }?.image, homePage = old?.homePage, checkedAt = 0)
            m + (station.streamUrl to next)
        }
        AppLog.i("Stations", "Server station \"${station.name}\": " + if (picture != null) "own picture link" else "picture to be found")
        if (picture == null) findPicturesSoon()
    }.onFailure { AppLog.w("Stations", "Editing a station's picture failed: ${it.message}") }

    private fun cleanPicture(text: String): String? {
        if (text.isBlank()) return null
        return StreamLinks.clean(text) ?: throw ApiException("The picture link isn't a web link")
    }

    /** Looks for the pictures of stations that don't have one looked for yet, in the background. */
    fun findPicturesSoon() {
        scope.launch(CoroutineName("station-pictures")) { findPictures() }
    }

    /**
     * Looks for the picture of every station that needs one, one at a time: those added here, then the server's.
     * Nothing is looked for offline; a lookup that reached no service is tried again next time.
     */
    suspend fun findPictures() = finding.withLock {
        if (!online()) return@withLock
        // What was found is saved once for each list, so Your Library is built again once, not for every station.
        val ownFound = LinkedHashMap<Station, StationPictures.Found>()
        for (st in mine().stations.filter { it.pictureCheckedAt == 0L }) {
            val found = StationPictures.find(st, pictureSources)
            log(st, found)
            if (found.answered) ownFound[st] = found
        }
        if (ownFound.isNotEmpty()) {
            val now = System.currentTimeMillis()
            own.update { o ->
                o.copy(stations = o.stations.map { current ->
                    // Only when the station wasn't changed meanwhile: a changed one is looked for again.
                    val (st, found) = ownFound.entries.firstOrNull { it.key.id == current.id }?.toPair() ?: return@map current
                    if (current.pictureCheckedAt == 0L && current.link == st.link && current.name == st.name && current.pictureLink == st.pictureLink) {
                        current.copy(image = found.image, homePage = current.homePage ?: found.homePage, pictureCheckedAt = now)
                    } else current
                })
            }
        }
        if (!serverHasStations()) return@withLock
        val known = serverPictures.read()
        val serverFound = LinkedHashMap<String, StationPictures.Found>()
        for (st in lastServer.read().filter { known[it.streamUrl].let { p -> p == null || p.checkedAt == 0L } }) {
            val found = StationPictures.find(st.copy(homePage = st.homePage ?: known[st.streamUrl]?.homePage), pictureSources)
            log(st, found)
            if (found.answered) serverFound[st.streamUrl] = found
        }
        if (serverFound.isNotEmpty()) {
            val now = System.currentTimeMillis()
            serverPictures.update { m ->
                val next = m.toMutableMap()
                for ((stream, found) in serverFound) {
                    val old = m[stream]
                    // A picture link entered meanwhile stays.
                    if (old != null && old.checkedAt != 0L) continue
                    next[stream] = StationPicture(image = found.image, homePage = old?.homePage ?: found.homePage, checkedAt = now)
                }
                next
            }
        }
    }

    private fun log(st: Station, found: StationPictures.Found) {
        when {
            found.image != null -> AppLog.i("Stations", "Picture for \"${st.name}\" from ${found.source.words}: ${AppLog.redact(found.image)}")
            found.answered -> AppLog.i("Stations", "No picture found for \"${st.name}\"")
            else -> AppLog.w("Stations", "Picture for \"${st.name}\" not looked for: no service could be reached")
        }
    }

    suspend fun removeStation(id: String) {
        own.update { o -> o.copy(stations = o.stations.filterNot { it.id == id }) }
        AppLog.i("Stations", "Removed a radio station")
    }

    suspend fun removePodcast(id: String) {
        own.update { o -> o.copy(podcasts = o.podcasts.filterNot { it.id == id }) }
        AppLog.i("Stations", "Removed a podcast")
    }

    /** Reads a podcast's feed again for new episodes; the copy kept here when it can't be read. */
    suspend fun refresh(id: String): PodcastFeed? {
        val feed = podcast(id) ?: return null
        return try {
            val found = inspect(feed.feedUrl) as? Found.Feed ?: throw ApiException("The link isn't a podcast feed any more")
            val fresh = feed.copy(
                author = found.feed.author.ifBlank { feed.author },
                image = found.feed.image ?: feed.image,
                description = found.feed.description ?: feed.description,
                episodes = found.feed.episodes,
                checkedAt = System.currentTimeMillis(),
            )
            own.update { o -> o.copy(podcasts = o.podcasts.map { if (it.id == id) fresh else it }) }
            AppLog.i("Stations", "Podcast \"${feed.title}\" read again: ${fresh.episodes.size} episode(s)")
            fresh
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLog.w("Stations", "Podcast \"${feed.title}\" not read again: ${e.message}")
            feed
        }
    }

    /**
     * The station ready to play: a server station whose address is a playlist (.m3u, .pls) is looked into for the
     * stream it points at. Stations added here were looked into when added.
     */
    suspend fun playable(station: Station): Station {
        if (!station.fromServer || !looksLikePlaylist(station.streamUrl)) return station
        return when (val found = runCatching { inspect(station.streamUrl) }.getOrNull()) {
            is Found.Stream -> station.copy(streamUrl = found.url)
            else -> station
        }
    }

    private sealed interface Found {
        data class Stream(val url: String, val title: String?) : Found
        data class Feed(val feed: PodcastFeeds.Parsed) : Found
    }

    /** Opens [url] and tells what it is, following a playlist to the stream it lists (one level deep). */
    private suspend fun inspect(url: String, depth: Int = 0): Found = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val (type, finalUrl, head) = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("The link answered HTTP ${r.code}")
            val contentType = r.header("Content-Type")
            val at = r.request.url.toString()
            val body = r.body
            // A stream is never read: only playlists and feeds (or what may be one) are, up to a limit.
            val text = if (body != null && StreamLinks.worthReading(at, contentType)) readUpTo(body, limitFor(contentType)) else ""
            Triple(StreamLinks.typeOf(at, contentType, text), at, text)
        }
        AppLog.i("Stations", "Link ${AppLog.redact(url)} is ${type.name.lowercase()}")
        when (type) {
            StreamLinks.Type.STREAM, StreamLinks.Type.HLS -> Found.Stream(if (type == StreamLinks.Type.HLS) finalUrl else url, null)
            StreamLinks.Type.FEED -> PodcastFeeds.parse(head, finalUrl)?.let { Found.Feed(it) }
                ?: throw ApiException("That feed couldn't be read")
            StreamLinks.Type.M3U, StreamLinks.Type.PLS -> {
                val pls = type == StreamLinks.Type.PLS
                val entries = if (pls) StreamLinks.parsePls(head, finalUrl) else StreamLinks.parseM3u(head, finalUrl)
                val title = if (pls) StreamLinks.plsTitle(head) else StreamLinks.m3uTitle(head)
                val first = entries.firstOrNull() ?: throw ApiException("That playlist has no stream in it")
                if (depth < 1 && looksLikePlaylist(first)) {
                    when (val inner = inspect(first, depth + 1)) {
                        is Found.Stream -> Found.Stream(inner.url, title ?: inner.title)
                        is Found.Feed -> inner
                    }
                } else {
                    Found.Stream(first, title)
                }
            }
            StreamLinks.Type.UNKNOWN -> throw ApiException("That link isn't a radio stream or a podcast feed")
        }
    }

    private fun readUpTo(body: ResponseBody, limit: Long): String {
        val source = body.source()
        source.request(limit)
        val buffer = source.buffer
        val n = minOf(limit, buffer.size)
        return buffer.readString(n, body.contentType()?.charset() ?: Charsets.UTF_8)
    }

    private fun limitFor(contentType: String?): Long {
        val type = contentType?.lowercase().orEmpty()
        return if (type.contains("xml") || type.contains("rss")) FEED_LIMIT else SNIFF_LIMIT
    }

    companion object {
        /** A podcast feed can be several megabytes; anything else is only read this far to tell what it is. */
        private const val FEED_LIMIT = 12L * 1024 * 1024
        private const val SNIFF_LIMIT = 512L * 1024
        private const val USER_AGENT = "Siansa (Android)"

        /** Whether an address names a playlist file rather than a stream. */
        fun looksLikePlaylist(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return path.endsWith(".m3u") || path.endsWith(".pls") || path.endsWith(".m3u8") && !path.contains("/hls/")
        }

        /** The server's [stations] with the pictures kept for them on this phone. */
        fun withPictures(stations: List<Station>, pictures: Map<String, StationPicture>): List<Station> = stations.map { st ->
            val p = pictures[st.streamUrl] ?: return@map st
            st.copy(image = p.image, homePage = st.homePage ?: p.homePage, pictureLink = p.pictureLink, pictureCheckedAt = p.checkedAt)
        }

        /**
         * [old] as edited: its id, when it was added and where it is stay. A [pictureLink] is the picture; without one
         * it's looked for again (the old picture shows meanwhile, unless the link changed or it was an entered link).
         * A blank [name] is the playlist's title or the address's host when the link changed, else the old name.
         */
        fun edited(old: Station, name: String, link: String, streamUrl: String, foundTitle: String?, pictureLink: String?, now: Long): Station {
            val linkChanged = link != old.link
            val newName = name.trim().ifEmpty { foundTitle ?: if (linkChanged) nameFrom(link) else old.name }
            val base = old.copy(name = newName, link = link, streamUrl = streamUrl, pictureLink = pictureLink)
            return if (pictureLink != null) {
                base.copy(image = pictureLink, pictureCheckedAt = now)
            } else {
                base.copy(
                    image = old.image.takeIf { !linkChanged && old.pictureLink == null },
                    homePage = old.homePage.takeIf { !linkChanged },
                    pictureCheckedAt = 0,
                )
            }
        }

        /** [stations] with [edited] in the place of the station with its id. */
        fun replaced(stations: List<Station>, edited: Station): List<Station> = stations.map { if (it.id == edited.id) edited else it }

        /** A name for a station from its address, when neither you nor its playlist gave one: the host. */
        fun nameFrom(url: String): String =
            url.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.").ifBlank { "Radio station" }
    }
}
