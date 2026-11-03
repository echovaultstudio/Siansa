package app.siansa.data

import app.siansa.log.AppLog
import app.siansa.net.ApiException
import app.siansa.net.PodcastFeeds
import app.siansa.net.SourceKind
import app.siansa.net.StreamLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
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
 */
class StationRepository(
    filesDir: File,
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
    private val serverList: suspend () -> List<Station>,
) {
    private val own = JsonFile(File(filesDir, Users.scoped("stations.json")), OwnStations.serializer()) { OwnStations() }
    private val lastServer = JsonFile(File(filesDir, Users.scoped("server_stations.json")), ListSerializer(Station.serializer())) { emptyList() }

    /** What's been added on this phone; null until first read. */
    val state: StateFlow<OwnStations?> = own.state

    suspend fun mine(): OwnStations = own.read()

    /** Whether the music server can have radio stations (OpenSubsonic: Navidrome and the like). */
    fun serverHasStations(): Boolean = settings().let { it.sourceKind == SourceKind.OPENSUBSONIC && it.navidromeUrl.isNotBlank() }

    /** The server's stations as last read, without asking it. */
    suspend fun knownServerStations(): List<Station> = if (serverHasStations()) lastServer.read() else emptyList()

    /** The server's stations, asked afresh; the last list when it can't be reached. */
    suspend fun serverStations(): List<Station> {
        if (!serverHasStations()) return emptyList()
        return try {
            val list = serverList()
            lastServer.update { list }
            AppLog.i("Stations", "Server has ${list.size} radio station(s)")
            list
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLog.w("Stations", "Server radio stations not loaded: ${e.message}")
            lastServer.read()
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
        existing.stations.firstOrNull { it.link == clean || it.streamUrl == clean }?.let { throw ApiException("“${it.name}” is in Your Stations already") }
        existing.podcasts.firstOrNull { it.feedUrl == clean }?.let { throw ApiException("“${it.title}” is in Your Stations already") }
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
                Added.Radio(station)
            }
        }
    }.onFailure { AppLog.w("Stations", "Adding a link failed: ${it.message}") }

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

        /** A name for a station from its address, when neither you nor its playlist gave one: the host. */
        fun nameFrom(url: String): String =
            url.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.").ifBlank { "Radio station" }
    }
}
