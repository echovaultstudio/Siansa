package app.siansa

import kotlinx.serialization.json.JsonElement
import app.siansa.net.BeatsApi
import app.siansa.data.StationRepository
import app.siansa.net.OutsideSource
import app.siansa.net.WebStationPictures
import app.siansa.playback.ActivePlayer
import app.siansa.playback.MiniFor
import app.siansa.mix.Dice
import app.siansa.mix.DjReady
import app.siansa.mix.GenreRadio
import app.siansa.mix.GenreVote
import app.siansa.data.ArtistPhotos
import app.siansa.data.FavouriteRadios
import app.siansa.data.audioMuseUserFor
import app.siansa.data.Users
import app.siansa.data.NoticeStore
import app.siansa.data.SavedVibes
import app.siansa.mix.MixResume
import app.siansa.data.RecentPlays
import app.siansa.data.FavouriteAlbums
import app.siansa.data.FavouriteArtists
import app.siansa.mix.MadeForYouCards
import app.siansa.mix.PinnedCards
import app.siansa.playback.ProfileStore
import app.siansa.playback.BookPlayer
import app.siansa.data.BookSpeeds
import app.siansa.offline.BookDownloads
import app.siansa.covers.CoverStore
import app.siansa.covers.CoverVersions
import app.siansa.data.QuietWrites
import app.siansa.covers.CoverRefresh
import app.siansa.covers.CoverCache
import app.siansa.covers.CoverEtags
import app.siansa.covers.StandIns
import kotlinx.coroutines.runBlocking

import android.app.Application
import app.siansa.data.CarouselStore
import app.siansa.data.ListSortStore
import app.siansa.data.PlaylistCache
import app.siansa.data.EqualiserRepository
import app.siansa.data.Favorites
import app.siansa.data.Guests
import app.siansa.data.HistoryStore
import app.siansa.data.LibraryRepository
import app.siansa.data.LibrarySync
import app.siansa.data.LocalPlaylists
import app.siansa.data.MixStore
import app.siansa.data.PlaylistEditor
import app.siansa.data.RemovedPlaylists
import app.siansa.data.SearchHistory
import app.siansa.data.SelectorStore
import app.siansa.data.SettingsRepository
import app.siansa.data.SyncPhase
import app.siansa.data.TopSongs
import app.siansa.log.AppLog
import app.siansa.log.NetLog
import app.siansa.mix.Similar
import app.siansa.mix.Sonic
import app.siansa.situation.SituationReader
import app.siansa.mix.SonicFingerprint
import app.siansa.mix.MixGenerator
import app.siansa.mix.Radios
import app.siansa.mix.Insights
import app.siansa.mix.ArtistSuggestions
import app.siansa.mix.SongPath
import app.siansa.mix.SongRadio
import app.siansa.mix.ContinuousPlay
import app.siansa.mix.WindDown
import app.siansa.mix.Taste
import app.siansa.net.AudioMuseApi
import app.siansa.net.AudiobookshelfApi
import app.siansa.net.BookCoverSource
import app.siansa.data.Book
import app.siansa.data.BookRepository
import app.siansa.net.DeezerApi
import app.siansa.net.HistoryApi
import app.siansa.net.LastFmApi
import app.siansa.net.ListenBrainzApi
import app.siansa.net.NavidromeNativeApi
import app.siansa.net.ChatApi
import app.siansa.net.Connection
import app.siansa.net.SubsonicApi
import app.siansa.net.MusicSource
import app.siansa.net.ActiveSource
import app.siansa.net.EmbyApi
import app.siansa.net.EmbyLogins
import app.siansa.net.SourceKind
import app.siansa.offline.Downloads
import app.siansa.offline.OfflineItem
import app.siansa.offline.OfflineKind
import app.siansa.offline.offlineItem
import app.siansa.playback.PlayerConnection
import app.siansa.playback.SleepTimer
import app.siansa.scrobble.Scrobbler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import app.siansa.playback.StreamStats
import java.io.File
import java.util.concurrent.TimeUnit
import app.siansa.data.mixScopeKey
import app.siansa.mix.PlayCountLog

/** Everything the app shares, created once in [SiansaApp]. */
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
        // Request log (only while logging is on): one line per request, covers included (issue #180). The
        // full detail of what went over the network (IP address, parameters, headers, size) is NetLog's.
        .addInterceptor { chain ->
            val request = chain.request()
            if (!AppLog.enabled) return@addInterceptor chain.proceed(request)
            val started = System.nanoTime()
            val where = request.url.host + request.url.encodedPath
            try {
                val response = chain.proceed(request)
                val ms = (System.nanoTime() - started) / 1_000_000
                // Answered by Siansa itself (an animated cover kept on the phone) rather than the server.
                val local = if (response.networkResponse == null && response.cacheResponse == null) " (from the phone)" else ""
                if (response.isSuccessful) AppLog.i("Http", "${request.method} $where → ${response.code} in ${ms}ms$local")
                else AppLog.w("Http", "${request.method} ${AppLog.redact(request.url.toString())} → ${response.code} in ${ms}ms$local")
                response
            } catch (e: Exception) {
                AppLog.w("Http", "${request.method} ${AppLog.redact(request.url.toString())} failed: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
        .addNetworkInterceptor(NetLog.interceptor)
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
        // The server's stand-in for a cover it doesn't have right now: its fingerprint is kept, so one kept on the
        // phone before can be told from a real cover and removed (StandIns, CoverRefresh).
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (StandIns.isStandIn(response)) {
                runCatching { response.peekBody(StandIns.PEEK_BYTES).bytes() }.getOrNull()
                    ?.takeIf { it.size < StandIns.PEEK_BYTES }
                    ?.let(StandIns::learn)
            }
            response
        }
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

    val subsonic = SubsonicApi(http) { settings.current }.apply {
        loginFor = { id -> guests.loginFor(id) }
        // Lists asked for straight from Navidrome keep to the libraries chosen for the sync (issue #206).
        chosenFolders = { settings.current.syncFolders }
    }

    /** An Emby server (issue #113), when that's the source picked in setup. */
    val emby = EmbyApi(
        http,
        settings = { settings.current },
        deviceId = { EmbyLogins.deviceId(app) },
        recall = { EmbyLogins.recall(app) },
        remember = { EmbyLogins.remember(app, it) },
    )

    /** A Jellyfin server (issue #703): the Emby client in its Jellyfin mode. */
    val jellyfin = EmbyApi(
        http,
        settings = { settings.current },
        deviceId = { EmbyLogins.deviceId(app) },
        recall = { EmbyLogins.recall(app) },
        remember = { EmbyLogins.remember(app, it) },
        jellyfin = true,
    )

    /**
     * Where the music comes from (issue #113): what screens, mixes and services use, rather than one server's API.
     * Follows the source in the settings, so picking Emby or Navidrome in setup takes effect at once.
     */
    val source: MusicSource = ActiveSource(
        pick = {
            when (settings.current.sourceKind) {
                SourceKind.EMBY -> emby
                SourceKind.JELLYFIN -> jellyfin
                else -> subsonic
            }
        },
        // Another shown user's songs, albums and covers go to their own server, of whatever kind (issue #427);
        // a book's cover ("bk-…") to Audiobookshelf (issue #521), through the same cover cache as everything.
        // A radio station's or podcast episode's id and a web cover (issue #353) to their own addresses.
        route = { id ->
            when {
                id != null && id.startsWith(Book.COVER_PREFIX) -> bookCovers
                OutsideSource.handles(id) -> outside
                else -> guests.sourceFor(id)
            }
        },
    )
    /** Stations and podcast episodes (issue #353): only ever asked about their own ids. */
    private val outside: MusicSource by lazy { OutsideSource(subsonic) }
    /** Only [MusicSource.coverUrl] is ever asked of it: book cover ids are routed here and nowhere else. */
    private val bookCovers: MusicSource by lazy { BookCoverSource(audiobookshelf, subsonic) }
    // Users shown together share a music server, but AudioMuse reads each login as a server of its own: a shown
    // user's songs go to AudioMuse with that user's "Server name in AudioMuse" (issue #708).
    val audioMuse = AudioMuseApi(
        http,
        serverFor = { ids ->
            val user = audioMuseUserFor(ids, { guests.ownerOf(it) }, Users.active, guests.shown.value, library.ownHidden.value)
            if (user == Users.active) settings.current.audioMuseServer else guests.settingsOf(user).current.audioMuseServer
        },
    ) { settings.current }
    /** Audiobooks and podcasts (issue #521): one login for the whole app, like AudioMuse. */
    val audiobookshelf = AudiobookshelfApi(http) { settings.current }
    val books = BookRepository(audiobookshelf) { settings.current }
    val bookSpeeds = BookSpeeds(app)
    /** Whole-book downloads (issue #521). */
    val bookDownloads: BookDownloads by lazy { BookDownloads(app, streamHttp, audiobookshelf) }
    /** Plays books and podcasts, apart from the music player (issue #521). */
    val bookPlayer: BookPlayer by lazy { BookPlayer(this) }
    /** Which mini player shows: the player started last (beta #45). */
    val activePlayer: ActivePlayer by lazy { ActivePlayer(scope, player.state, bookPlayer.state) }
    /** Siansa Beats, asked directly when it's set up (Siansa Dev, the owner, 10 Nov). */
    val beats = BeatsApi(http) { settings.current }

    /**
     * A GET of the Siansa Beats (dev) plugin's [pluginPath], answered by Siansa Beats itself when it's set up (its v1
     * API has the same paths and answers), by the plugin on AudioMuse otherwise.
     */
    suspend fun siansaBeats(pluginPath: String): JsonElement =
        if (settings.current.beatsReady) beats.get(BeatsApi.fromPlugin(pluginPath)) else audioMuse.plugin(pluginPath)

    /** A POST to the plugin's [pluginPath], or to Siansa Beats itself (as [siansaBeats]). */
    suspend fun siansaBeatsPost(pluginPath: String, body: String): JsonElement =
        if (settings.current.beatsReady) beats.post(BeatsApi.fromPlugin(pluginPath), body) else audioMuse.pluginPost(pluginPath, body)

    /** Song loudness and fade shapes for levelling and crossfades (issue #60). */
    val profiles = ProfileStore({ path -> siansaBeats(path) }, { settings.current }, beats = { beats.capabilities() })
    /** Genre radios by Siansa Beats' vote, when it's there (Siansa Dev); by the songs' tags otherwise. */
    val genreVote = GenreVote(
        { path -> siansaBeats(path) }, { settings.current.let { it.beatsReady || it.audioMuseReady } },
        // The genre list by vote, saved so Search and Home show it at once (and while Siansa Beats is away).
        load = { java.io.File(app.filesDir, "voted-genres.json").takeIf { it.exists() }?.readText() },
        save = { java.io.File(app.filesDir, "voted-genres.json").writeText(it) },
        // Each genre's description, kept so its page shows it at once, also while Siansa Beats is away.
        loadAbouts = { java.io.File(app.filesDir, "genre-abouts.json").takeIf { it.exists() }?.readText() },
        saveAbouts = { java.io.File(app.filesDir, "genre-abouts.json").writeText(it) },
    )
    /** The AI service that writes mix titles and sound searches (issue #665). */
    val ai = ChatApi(http) { settings.current }
    val deezer = DeezerApi(http)

    val selectors = SelectorStore(app)
    /** The home carousels set in Vibes (issue #253). */
    val carousels = CarouselStore(app)
    val searchHistory = SearchHistory(app)
    /** Other users' music shown alongside the user in use's (issue #141). */
    val guests: Guests by lazy { Guests(app, http, library, scope) }
    // A mix stays as it was made when the source filter changes (issue #486).
    val mixes = MixStore(app)
    /** Each song's play count per day as seen on the server, for On repeat's last 30 days (issue #495). */
    val playCounts = PlayCountLog(app)

    /** The source filter's key for mixes ([mixScopeKey]). */
    fun mixScope(): String = mixScopeKey(library.ownHidden.value, guests.others(), settings.current.syncFolders)
    /** Vibes made on Dscvr (issue #339). */
    val savedVibes = SavedVibes(app)
    val history = HistoryStore(app)
    /** What was last played as a whole, for Home's shortcuts (issue #271). */
    val recentPlays = RecentPlays(app)
    /** How Liked songs and playlists are sorted (issue #257). */
    val listSort = ListSortStore(app)
    val library = LibraryRepository(app)
    val topSongs = TopSongs(app, source, library, scope)

    init {
        CoverStore.init(app)
        val songAlbums = java.util.concurrent.ConcurrentHashMap<String, String>()
        CoverStore.albumCoverOfSong = { songCover ->
            songAlbums[songCover] ?: runBlocking { library.albumCoverOfSongCover(songCover) }?.also { songAlbums[songCover] = it }
        }
    }
    val sync = LibrarySync(source, library, settings, scope)

    init {
        // Disc covers follow their album's cover version (issue #639): read again after the library loads and after
        // each sync, so a cover replaced on the server shows everywhere, the mini player included.
        scope.launch {
            library.version.collectLatest {
                runCatching { CoverVersions.update(library.albumCovers()) }
                    .onFailure { AppLog.w("Cover", "Couldn't read the albums' cover versions: ${it.message}") }
            }
        }
    }

    val lastFm = LastFmApi(http)
    val listenBrainz = ListenBrainzApi(http)
    val scrobbler = Scrobbler(app, settings, source, lastFm, listenBrainz, scope) { id -> library.userOf(id) }
    val favorites = Favorites(subsonic, source, lastFm, settings, library, scope)
    /** Favourite albums, starred in Navidrome (issue #275). */
    val favouriteAlbums = FavouriteAlbums(app, source, scope)
    /** Favourite artists, starred in Navidrome (issue #397). */
    val favouriteArtists = FavouriteArtists(app, source, scope)
    /** Radios hearted on their page (the owner, 10 Nov): in Your Library › Favourites, and as custom cards. */
    val favouriteRadios = FavouriteRadios(app, scope)

    /**
     * Songs kept on the phone as they play (and the next one in the queue), so replays start at once
     * and survive a bad connection. The oldest are removed when the chosen size is reached.
     */
    val songCache: SimpleCache by lazy {
        // Off (0) still needs room for the songs loaded ahead, which are removed once played.
        val megabytes = settings.current.songCacheMb.let { if (it <= 0) 1024 else it.coerceAtLeast(100) }
        SimpleCache(
            File(app.cacheDir, "song_cache"),
            LeastRecentlyUsedCacheEvictor(megabytes * 1024L * 1024L),
            StandaloneDatabaseProvider(app),
        )
    }

    /** Albums, playlists and songs kept on the phone for listening without a connection. */
    val downloads = Downloads(app, source, library, streamHttp, settings, scope).also { d ->
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
    val notices = NoticeStore(app)
    /** Whether the server and AudioMuse can be reached, for offline messages and playing only downloads. */
    val connection = Connection(app, http, { settings.current }, scope)
    val player = PlayerConnection(app, source).apply {
        isOffline = { connection.current.offline }
        onlyDownloads = { settings.current.downloadedOnly }
        isDownloaded = { downloads.isDownloaded(it) }
        onPlayStarted = { activePlayer.choose(MiniFor.MUSIC); bookPlayer.pause() }
    }
    /** Whether a DJ mix can run: AudioMuse and Siansa Beats answering ([DjReady]). */
    val djReady = DjReady()

    /** Why a DJ mix can't run now (a few words for its button), or null when it can. */
    fun djProblem(): String? = DjReady.problem(settings.current, connection.current, djReady.beats.value)

    init {
        // DJ mixes need Siansa Beats' beat grids (directly, or through its plugin on AudioMuse): checked at the start,
        // again every half hour (every two minutes while it doesn't answer), and from scratch when either is set up anew.
        scope.launch(CoroutineName("dj-ready")) {
            settings.state.map { Triple(it.audioMuseUrl, it.beatsUrl, it.beatsKey) }.distinctUntilChanged().drop(1).collect { djReady.forget() }
        }
        scope.launch(CoroutineName("dj-ready-check")) {
            while (true) {
                val s = settings.current
                // Siansa Beats directly when it's set up; else the plugin, once AudioMuse answers.
                if ((s.beatsReady || s.audioMuseReady && connection.current.audioMuse == true) && djReady.due()) {
                    val result = runCatching { profiles.check() }.getOrNull()
                    if (result != null) djReady.checked(result.ok, result.message)
                }
                delay(DJ_CHECK_LOOK_MS)
            }
        }
    }

    init {
        // The playing artist's popular songs, fetched ahead so their page shows them at once (issue #398). After
        // a short wait, so skipping through songs doesn't ask for every artist on the way.
        scope.launch(CoroutineName("top-songs-ahead")) {
            player.state.map { it.current?.artistId }.distinctUntilChanged().collectLatest { artistId ->
                if (artistId == null) return@collectLatest
                delay(TOP_SONGS_AHEAD_WAIT_MS)
                if (!connection.current.offline) topSongs.fetchAhead(artistId)
            }
        }
    }

    /** Your Stations (issue #353): the server's radio stations and the stations and podcasts added on this phone. */
    val stations = StationRepository(
        app.filesDir,
        http,
        { settings.current },
        WebStationPictures(http, "Siansa/" + (runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "dev")),
        scope,
        online = { !connection.current.offline },
    ) { subsonic.internetRadioStations() }

    /** Playlists kept only on this phone. */
    val localPlaylists = LocalPlaylists(app).also { local ->
        // Phone-only playlists are kept downloaded, so they play without a connection.
        scope.launch(CoroutineName("phone-playlist-downloads")) {
            local.all()
            // Also when the download list changes, so "Remove all downloads" doesn't leave them behind.
            combine(local.state.filterNotNull(), downloads.ids.map { it.items }.distinctUntilChanged()) { playlists, _ -> playlists }
                .collect { downloads.syncPhonePlaylists(it) }
        }
    }
    /** Every Navidrome playlist's songs kept on the phone (issue #251). */
    val playlistCache = PlaylistCache(app, source, library).also { cache ->
        downloads.playlistSongs = { id -> cache.get(id).songs }
    }
    val playlists = PlaylistEditor(
        source,
        NavidromeNativeApi(http) { settings.current },
        library,
        localPlaylists,
        RemovedPlaylists(app),
        isOffline = { connection.current.offline },
        moved = { localId, made -> downloads.moveItem(OfflineItem.key(OfflineKind.PLAYLIST, localId), made.offlineItem()) },
        changed = { id -> playlistCache.refresh(id) },
        forgotten = { id -> playlistCache.forget(id) },
    )
    /** "Sounds like" from Navidrome's sonic similarity or AudioMuse (issue #208). */
    val sonic = Sonic(this)
    /** The moment a vibe's mix is made in: weather, movement, light, charging (issue #247). */
    val situation = SituationReader(app, http)
    /** Similar songs, albums and artists (issue #223). */
    val similar = Similar(this)
    /** Fresh photos for Home's artist radios (beta report 95a30366). */
    val artistPhotos = ArtistPhotos(source, library)

    // ---- Covers kept on the phone: the server's stand-ins never, changed ones fetched fresh (the owner, 11 Nov) ----

    private val coverPrefs = app.getSharedPreferences("cover_refresh", android.content.Context.MODE_PRIVATE)

    @Volatile
    private var refreshCoversAfterSync = false

    /**
     * Sync all also checks the covers kept on the phone (CoverRefresh): after the library's sync when one was started
     * ([afterSync]), so it reads the library as it is now; else straight away.
     */
    fun refreshCovers(afterSync: Boolean) {
        if (afterSync) refreshCoversAfterSync = true
        else scope.launch(CoroutineName("cover-refresh")) { runCoverRefresh(askServer = true) }
    }

    private suspend fun runCoverRefresh(askServer: Boolean) = runCatching {
        CoverRefresh.run(app, this, askServer)
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        AppLog.w("Covers", "Cover refresh failed: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrNull()

    init {
        StandIns.restore(coverPrefs.getStringSet("stand_ins", emptySet()).orEmpty())
        StandIns.onLearned = { prints ->
            AppLog.i("Covers", "The server sent a stand-in picture not seen before (${prints.size} known now)")
            QuietWrites.save(coverPrefs) { putStringSet("stand_ins", prints.mapTo(HashSet()) { it.toString() }) }
        }
        // The server's version of each kept cover whose id has none, for Sync all to ask about.
        val etags = app.getSharedPreferences("cover_etags", android.content.Context.MODE_PRIVATE)
        scope.launch(Dispatchers.IO) {
            CoverEtags.restore(etags.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap())
        }
        CoverEtags.onChanged = { key, etag ->
            QuietWrites.save(etags) { if (etag == null) remove(key) else putString(key, etag) }
        }
        // Emby's and Jellyfin's picture tags: an item seen with a new one has its kept covers fetched fresh.
        val tags = File(app.filesDir, "cover-tags.txt")
        CoverVersions.kept = { runCatching { tags.takeIf { it.exists() }?.readText() }.getOrNull() }
        CoverVersions.onChanged = { id ->
            AppLog.i("Covers", "Cover $id changed on the server: fetched fresh at its next showing")
            runCatching { CoverCache.forget(coil3.SingletonImageLoader.get(app), id) }
        }
        scope.launch(CoroutineName("cover-tags")) {
            library.version.collectLatest {
                kotlinx.coroutines.delay(2_000)
                CoverVersions.dump()?.let { text -> kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { tags.writeText(text) } } }
            }
        }
        scope.launch(CoroutineName("cover-refresh")) {
            sync.progress.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (!refreshCoversAfterSync || (phase != SyncPhase.DONE && phase != SyncPhase.FAILED)) return@collect
                refreshCoversAfterSync = false
                if (phase == SyncPhase.DONE) launch { runCoverRefresh(askServer = true) }
            }
        }
        // Once after updating: the stand-ins earlier versions kept are removed, without asking the server anything.
        if (!coverPrefs.getBoolean("stand_ins_removed", false)) {
            scope.launch(CoroutineName("cover-clean-up")) {
                kotlinx.coroutines.delay(20_000)
                if (runCoverRefresh(askServer = false) != null) {
                    QuietWrites.save(coverPrefs) { putBoolean("stand_ins_removed", true) }
                }
            }
        }
    }
    /** Roll the dice on Home (issue #347). */
    val dice = Dice(this)
    /** Your sonic fingerprint on Home (issue #216). */
    val fingerprint = SonicFingerprint(this)
    val songRadio = SongRadio(this)
    /** Today's mixes carry on where you left them (issue #269). */
    val mixResume = MixResume(this, app)
    /** Your own cards on Made for you (issue #274). */
    val cards = MadeForYouCards(this, app)
    /** Custom cards pinned to Home (issue #622). */
    val pinnedCards = PinnedCards(this, app)
    val songPath = SongPath(this)
    val sleepTimer = SleepTimer()
    val windDown = WindDown(this)
    /** Keep playing: songs added when the queue is about to end (issue #337). */
    /** A genre's radio: 30 random songs of it, more as the queue ends (the owner, 10 Nov). Before Keep playing, which asks it. */
    val genreRadio = GenreRadio(this)
    val continuousPlay = ContinuousPlay(this)
}

/**
 * The equaliser pre-amp to use after the ReplayGain pre-amp went away: the old level when one was set and
 * the equaliser's is still at 0 (within the equaliser's range), else null (nothing to move).
 */
fun movedPreamp(oldReplayGainPreamp: Float, equaliserPreamp: Float): Float? {
    if (oldReplayGainPreamp == 0f || equaliserPreamp != 0f) return null
    return oldReplayGainPreamp.coerceIn(-app.siansa.playback.EqSettings.MAX_PREAMP_DB, app.siansa.playback.EqSettings.MAX_PREAMP_DB)
}

/** How long a song plays before its artist's popular songs are fetched ahead (issue #398). */
private const val TOP_SONGS_AHEAD_WAIT_MS = 5_000L
/** How often the DJ check looks whether the plugin is due to be asked again (it asks only every 2 or 30 minutes). */
private const val DJ_CHECK_LOOK_MS = 15_000L
