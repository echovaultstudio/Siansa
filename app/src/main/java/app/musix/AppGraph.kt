package app.musix

import app.musix.covers.CoverStore
import kotlinx.coroutines.runBlocking

import android.app.Application
import app.musix.data.EqualiserRepository
import app.musix.data.Favorites
import app.musix.data.Guests
import app.musix.data.HistoryStore
import app.musix.data.LibraryRepository
import app.musix.data.LibrarySync
import app.musix.data.LocalPlaylists
import app.musix.data.MixStore
import app.musix.data.PlaylistEditor
import app.musix.data.RemovedPlaylists
import app.musix.data.SelectorStore
import app.musix.data.SettingsRepository
import app.musix.data.SyncPhase
import app.musix.data.TopSongs
import app.musix.log.AppLog
import app.musix.mix.MixGenerator
import app.musix.mix.Radios
import app.musix.mix.Insights
import app.musix.mix.ArtistSuggestions
import app.musix.mix.SongPath
import app.musix.mix.SongRadio
import app.musix.mix.WindDown
import app.musix.mix.Taste
import app.musix.net.AudioMuseApi
import app.musix.net.DeezerApi
import app.musix.net.HistoryApi
import app.musix.net.LastFmApi
import app.musix.net.ListenBrainzApi
import app.musix.net.NavidromeNativeApi
import app.musix.net.OpenRouterApi
import app.musix.net.Connection
import app.musix.net.SubsonicApi
import app.musix.offline.Downloads
import app.musix.playback.PlayerConnection
import app.musix.playback.SleepTimer
import app.musix.scrobble.Scrobbler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import app.musix.playback.StreamStats
import java.io.File
import java.util.concurrent.TimeUnit

/** Everything the app shares, created once in [MusixApp]. */
@OptIn(UnstableApi::class)
class AppGraph(val app: Application) {
    /**
     * Background work. An error in one job is logged (with where it happened) instead of closing the
     * app, and the other jobs carry on.
     */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { context, error ->
            AppLog.e("Background", "Background job failed (${context[kotlinx.coroutines.CoroutineName]?.name ?: "unnamed"})", error)
        },
    )
    val settings = SettingsRepository(app)
    val equaliser = EqualiserRepository(app).also { eq ->
        // There used to be a second pre-amp with ReplayGain; its level moves to the equaliser's, once.
        val old = settings.current.preampDb
        val moved = movedPreamp(old, eq.state.value.preampDb)
        if (moved != null) {
            eq.set(eq.state.value.copy(preampDb = moved))
            settings.save(settings.current.copy(preampDb = 0f))
            AppLog.i("Settings", "Moved the ReplayGain pre-amp (${old} dB) to the equaliser's pre-amp")
        }
    }

    /**
     * One HTTP client for API calls, streaming and covers. When Navidrome sits behind a proxy that asks
     * for a login, that login is added to every request to the Navidrome address.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val own = settings.current
            val navidrome = own.navidromeUrl.toHttpUrlOrNull()
            val ownServer = navidrome != null && request.url.host == navidrome.host && request.url.port == navidrome.port
            // Another shown user's server can sit behind a proxy of its own.
            val s = if (ownServer) own else guests.proxyFor(request.url.host, request.url.port) ?: own
            val proxied = s.proxyUser.isNotBlank() && (ownServer || s !== own) &&
                request.header("Authorization") == null
            if (proxied) {
                chain.proceed(
                    request.newBuilder()
                        .header("Authorization", Credentials.basic(s.proxyUser, s.proxyPassword, Charsets.UTF_8))
                        .build()
                )
            } else {
                chain.proceed(request)
            }
        }
        // Request log (only while logging is on). Cover images are left out to keep the log readable.
        .addInterceptor { chain ->
            val request = chain.request()
            if (!AppLog.enabled || request.url.encodedPath.endsWith("/getCoverArt")) return@addInterceptor chain.proceed(request)
            val started = System.nanoTime()
            val where = request.url.host + request.url.encodedPath
            try {
                val response = chain.proceed(request)
                val ms = (System.nanoTime() - started) / 1_000_000
                if (response.isSuccessful) AppLog.i("Http", "${request.method} $where → ${response.code} in ${ms}ms")
                else AppLog.w("Http", "${request.method} ${AppLog.redact(request.url.toString())} → ${response.code} in ${ms}ms")
                response
            } catch (e: Exception) {
                AppLog.w("Http", "${request.method} $where failed: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
        .build()

    /**
     * Covers and artist photos: their own download queue and connections. OkHttp lets each queue run
     * at most a few downloads per server at once, so when covers and songs shared one, a few big covers
     * kept the song waiting in line and starved it of bandwidth.
     */
    val imageHttp: OkHttpClient = http.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 24; maxRequestsPerHost = 6 })
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        // Animated covers Navidrome can't shrink: downloaded once, then answered from the phone.
        .addInterceptor(CoverStore.interceptor)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Big images are worth knowing about when playback struggles.
            val length = response.header("Content-Length")?.toLongOrNull() ?: -1
            if (length > 1_000_000) {
                val url = chain.request().url
                val what = url.queryParameter("id")?.let { "cover $it (size ${url.queryParameter("size") ?: "original"})" } ?: url.encodedPath.take(24)
                AppLog.i("Http", "Image $what is ${"%.1f".format(length / 1_048_576.0)} MB")
            }
            response
        }
        .build()

    /**
     * Song streams (and pre-caching): a queue and connections of their own, over HTTP/1.1 so every stream
     * gets its own TCP connection instead of sharing one with covers and API calls.
     */
    val streamHttp: OkHttpClient = http.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 8; maxRequestsPerHost = 4 })
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_1_1))
        .addNetworkInterceptor(StreamStats.interceptor)
        .build()

    val subsonic = SubsonicApi(http) { settings.current }.apply { loginFor = { id -> guests.loginFor(id) } }
    val audioMuse = AudioMuseApi(http) { settings.current }
    val openRouter = OpenRouterApi(http) { settings.current }
    val deezer = DeezerApi(http)

    val selectors = SelectorStore(app)
    /** Other users' music shown alongside the user in use's (issue #141). */
    val guests: Guests by lazy { Guests(app, http, library, scope) }
    val mixes = MixStore(app)
    val history = HistoryStore(app)
    val library = LibraryRepository(app)
    val topSongs = TopSongs(app, subsonic, library, scope)

    init {
        CoverStore.init(app)
        val songAlbums = java.util.concurrent.ConcurrentHashMap<String, String>()
        CoverStore.albumCoverOfSong = { songCover ->
            songAlbums[songCover] ?: runBlocking { library.albumCoverOfSongCover(songCover) }?.also { songAlbums[songCover] = it }
        }
    }
    val sync = LibrarySync(subsonic, library, settings, scope)

    val lastFm = LastFmApi(http)
    val listenBrainz = ListenBrainzApi(http)
    val scrobbler = Scrobbler(app, settings, subsonic, lastFm, listenBrainz, scope)
    val favorites = Favorites(subsonic, lastFm, settings, library, scope)

    /**
     * Songs kept on the phone as they play (and the next one in the queue), so replays start at once
     * and survive a bad connection. The oldest are removed when the chosen size is reached.
     */
    val songCache: SimpleCache by lazy {
        val megabytes = settings.current.songCacheMb.coerceAtLeast(100)
        SimpleCache(
            File(app.cacheDir, "song_cache"),
            LeastRecentlyUsedCacheEvictor(megabytes * 1024L * 1024L),
            StandaloneDatabaseProvider(app),
        )
    }

    /** Albums, playlists and songs kept on the phone for listening without a connection. */
    val downloads = Downloads(app, subsonic, library, streamHttp, settings, scope).also { d ->
        // "Only downloaded music": every list in the app shows only what's on the phone.
        library.downloadedOnly = { if (settings.current.downloadedOnly) d.shelf.value else null }
        scope.launch(CoroutineName("downloaded-only")) {
            combine(settings.state.map { it.downloadedOnly }.distinctUntilChanged(), d.shelf) { on, shelf -> if (on) shelf else null }
                .distinctUntilChanged()
                .drop(1)
                .collect { library.bump() }
        }
        // Other users' music is checked for changes after each library sync.
        scope.launch(CoroutineName("guest-sync")) {
            sync.progress.map { it.phase }.distinctUntilChanged().collect { if (it == SyncPhase.DONE) guests.syncAll() }
        }
        guests.start()
    }

    val historyApi = HistoryApi(http)
    val taste = Taste(this)
    val generator = MixGenerator(this)
    val insights = Insights(this)
    val radios = Radios(this)
    val suggestions = ArtistSuggestions(this)
    /** Whether the server and AudioMuse can be reached, for offline messages and playing only downloads. */
    val connection = Connection(app, http, { settings.current }, scope)
    val player = PlayerConnection(app, subsonic).apply {
        isOffline = { connection.current.offline }
        onlyDownloads = { settings.current.downloadedOnly }
        isDownloaded = { downloads.isDownloaded(it) }
    }
    /** Playlists kept only on this phone. */
    val localPlaylists = LocalPlaylists(app)
    val playlists = PlaylistEditor(subsonic, NavidromeNativeApi(http) { settings.current }, library, localPlaylists, RemovedPlaylists(app)) { connection.current.offline }
    val songRadio = SongRadio(this)
    val songPath = SongPath(this)
    val sleepTimer = SleepTimer()
    val windDown = WindDown(this)
}

/**
 * The equaliser pre-amp to use after the ReplayGain pre-amp went away: the old level when one was set and
 * the equaliser's is still at 0 (within the equaliser's range), else null (nothing to move).
 */
fun movedPreamp(oldReplayGainPreamp: Float, equaliserPreamp: Float): Float? {
    if (oldReplayGainPreamp == 0f || equaliserPreamp != 0f) return null
    return oldReplayGainPreamp.coerceIn(-app.musix.playback.EqSettings.MAX_PREAMP_DB, app.musix.playback.EqSettings.MAX_PREAMP_DB)
}
