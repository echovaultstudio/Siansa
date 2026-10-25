package app.siansa.data

import android.content.Context
import app.siansa.log.AppLog
import app.siansa.net.EmbyApi
import app.siansa.net.EmbyLogins
import app.siansa.net.MusicSource
import app.siansa.net.SourceKind
import app.siansa.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/** Another user's music as their server lists it, before it joins the library. */
data class GuestMusic(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    /** What's left to store, and the ids to list as theirs. */
    data class Plan(
        val artists: List<Artist>,
        val albums: List<Album>,
        val songs: List<Song>,
        val playlists: List<Playlist>,
    ) {
        val ids: List<String> get() = (artists.map { it.id } + albums.map { it.id } + songs.map { it.id } + playlists.map { it.id }).distinct()
    }

    companion object {
        /**
         * Leaves out what the library already has (with the same id: the same server, so the same song,
         * which then plays with the login it came with first) and anything listed twice.
         */
        fun plan(
            music: GuestMusic,
            knownSongs: Set<String>,
            knownAlbums: Set<String>,
            knownArtists: Set<String>,
            knownPlaylists: Set<String>,
        ) = Plan(
            artists = music.artists.distinctBy { it.id }.filter { it.id !in knownArtists },
            albums = music.albums.distinctBy { it.id }.filter { it.id !in knownAlbums },
            songs = music.songs.distinctBy { it.id }.filter { it.id !in knownSongs },
            playlists = music.playlists.distinctBy { it.id }.filter { it.id !in knownPlaylists },
        )

        // Disc covers (issue #226) are "dc-<album id>:<disc>", sometimes with "_<stamp>" too.
        private val COVER = Regex("^(?:al|ar|mf|pl|dc)-([^_:]+)(?:[_:].*)?$")

        /**
         * The item a cover id belongs to: "al-123_6543" → "123", a disc cover "dc-123:2" → album "123". Null
         * when it isn't a cover id.
         */
        fun itemOfCover(id: String): String? = COVER.find(id)?.groupValues?.get(1)
    }
}

/** Where loading one other user's music has got to, for their card in the Sources panel. */
data class GuestState(val running: Boolean = false, val songs: Int? = null, val error: String? = null)

/**
 * Other users' music shown alongside the user in use's (issue #141). Each shown user's library is loaded
 * with their own login into the same on-phone library, and every request about one of their songs,
 * albums, artists, playlists or covers (streaming, downloads, covers, lyrics, scrobbles) is then sent with
 * that user's login, to their server. Hiding a user takes all of it out again.
 */
class Guests(
    private val context: Context,
    private val http: OkHttpClient,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val routes = ConcurrentHashMap<String, Int>()
    private val settings = ConcurrentHashMap<Int, SettingsRepository>()
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val writing = Mutex()

    private val _state = MutableStateFlow<Map<Int, GuestState>>(emptyMap())
    val state: StateFlow<Map<Int, GuestState>> = _state.asStateFlow()

    private val _shown = MutableStateFlow(Users.shown(context))
    /** Users whose music shows, the user in use included. */
    val shown: StateFlow<Set<Int>> = _shown.asStateFlow()

    /** [user]'s settings (their login and libraries). */
    fun settingsOf(user: Int): SettingsRepository = settings.getOrPut(user) { SettingsRepository(context, user) }

    private val apis = ConcurrentHashMap<Int, MusicSource>()

    /**
     * [user]'s server with their login, of the kind they set up (issue #427): Emby or OpenSubsonic. Kept per user,
     * so an Emby login is made once; made again if they changed kind.
     */
    fun apiOf(user: Int): MusicSource {
        val kind = settingsOf(user).current.sourceKind
        apis[user]?.takeIf { it.kind == kind }?.let { return it }
        return sourceOf(kind, user).also { apis[user] = it }
    }

    private fun sourceOf(kind: SourceKind, user: Int): MusicSource = when (kind) {
        SourceKind.EMBY -> EmbyApi(
            http,
            settings = { settingsOf(user).current },
            deviceId = { EmbyLogins.deviceId(context) },
            recall = { EmbyLogins.recall(context, user) },
            remember = { EmbyLogins.remember(context, it, user) },
        )
        else -> SubsonicApi(http) { settingsOf(user).current }
    }

    /** The other user whose song, album, artist, playlist or cover [id] is, or null for the user in use's. */
    fun ownerOf(id: String?): Int? {
        if (id.isNullOrEmpty() || routes.isEmpty()) return null
        return routes[id] ?: GuestMusic.itemOfCover(id)?.let { routes[it] }
    }

    /** The source a request about [id] goes to when it's another user's music (their server, their login), else null. */
    fun sourceFor(id: String?): MusicSource? = ownerOf(id)?.let { apiOf(it) }

    /**
     * The login to use for a request about [id] (a song, album, artist, playlist or cover id): another
     * user's when it's theirs, else null (the user in use's).
     */
    fun loginFor(id: String?): AppSettings? {
        val user = ownerOf(id) ?: return null
        return settingsOf(user).current
    }

    /** The proxy login for a request to [host]:[port] when it's another shown user's server behind one. */
    fun proxyFor(host: String, port: Int): AppSettings? {
        if (routes.isEmpty()) return null
        return _shown.value.asSequence().filter { it != Users.active }.map { settingsOf(it).current }.firstOrNull { s ->
            val url = s.navidromeUrl.toHttpUrlOrNull()
            s.proxyUser.isNotBlank() && url != null && url.host == host && url.port == port
        }
    }

    /**
     * At start: forget the routes of users no longer shown, and load (or check for changes in) every
     * shown user's music.
     */
    fun start() {
        scope.launch {
            val guests = _shown.value - Users.active
            writing.withLock {
                library.sourceUsers().filter { it !in guests }.forEach { gone ->
                    AppLog.i("Sources", "User ${gone + 1} isn't shown any more; removing their music")
                    library.removeSource(gone)
                }
                reloadRoutes()
            }
            if (guests.isNotEmpty()) library.bump()
            guests.forEach { sync(it) }
        }
    }

    /** Shows or hides [user]'s music. The user in use's own music is hidden by [LibraryRepository.setOwnHidden] (issue #427). */
    fun setShown(user: Int, show: Boolean) {
        if (user == Users.active) return
        val next = if (show) _shown.value + user else _shown.value - user
        Users.setShown(context, next)
        _shown.value = Users.shown(context)
        if (show) {
            sync(user, force = true)
        } else {
            jobs.remove(user)?.cancel()
            _state.update { it - user }
            scope.launch {
                writing.withLock {
                    library.removeSource(user)
                    reloadRoutes()
                }
                library.refreshStatus()
                library.bump()
                AppLog.i("Sources", "Hid user ${user + 1}")
            }
        }
    }

    /** Loads every shown other user's music again when their server has changed (after a library sync). */
    fun syncAll(force: Boolean = false) {
        (_shown.value - Users.active).forEach { sync(it, force) }
    }

    /** [user]'s libraries chosen for showing; an empty set means all of them. */
    fun setFolders(user: Int, ids: Set<String>) {
        settingsOf(user).setSyncFolders(ids)
        sync(user, force = true)
    }

    /** Loads [user]'s music, unless their server hasn't scanned since the last time ([force] loads anyway). */
    fun sync(user: Int, force: Boolean = false) {
        if (user == Users.active || user !in _shown.value) return
        if (jobs[user]?.isActive == true) return
        jobs[user] = scope.launch {
            _state.update { it + (user to (it[user] ?: GuestState()).copy(running = true, error = null)) }
            try {
                val songs = load(user, force)
                _state.update { it + (user to GuestState(songs = songs)) }
            } catch (e: CancellationException) {
                _state.update { it - user }
                throw e
            } catch (e: Exception) {
                AppLog.w("Sources", "User ${user + 1}'s music couldn't be loaded: ${e.message}")
                _state.update { it + (user to (it[user] ?: GuestState()).copy(running = false, error = e.message ?: "Couldn't load")) }
            }
        }
    }

    private suspend fun load(user: Int, force: Boolean): Int? {
        val s = settingsOf(user).current
        if (s.navidromeUrl.isBlank() || s.navidromeUser.isBlank()) throw IllegalStateException("This user isn't connected yet")
        val api = apiOf(user)
        val all = api.musicFolders()
        val chosen = all.filter { it.id in s.syncFolders }
        val passes: List<MusicFolder?> = if (chosen.isEmpty() || chosen.size == all.size) listOf(null) else chosen
        val folderKey = passes.joinToString(",") { it?.id ?: "*" }
        val lastScan = runCatching { api.lastScan() }.getOrNull()
        val metaScan = "guest.$user.lastScan"
        val metaFolders = "guest.$user.folders"
        if (!force && lastScan != null && lastScan != "scanning" && user in library.sourceUsers() &&
            library.meta(metaScan) == lastScan && library.meta(metaFolders) == folderKey
        ) {
            AppLog.i("Sources", "User ${user + 1}: no scan since last time")
            return _state.value[user]?.songs
        }
        AppLog.i("Sources", "Loading user ${user + 1}'s music (${passes.size} pass(es))")
        val artists = LinkedHashMap<String, Artist>()
        val albums = LinkedHashMap<String, Album>()
        val songs = LinkedHashMap<String, Song>()
        for (folder in passes) {
            api.artists(folder?.id).forEach { artists.putIfAbsent(it.id, it) }
            var offset = 0
            while (true) {
                val page = api.albums("alphabeticalByName", PAGE, offset, folder?.id)
                page.forEach { albums.putIfAbsent(it.id, it) }
                if (page.size < PAGE) break
                offset += PAGE
            }
            offset = 0
            while (true) {
                val page = api.songsPage(offset, PAGE, folder?.id)
                page.forEach { songs.putIfAbsent(it.id, it) }
                _state.update { it + (user to GuestState(running = true, songs = songs.size)) }
                if (page.size < PAGE) break
                offset += PAGE
            }
        }
        val playlists = runCatching { api.playlists() }.getOrElse { emptyList() }
        val music = GuestMusic(artists.values.toList(), albums.values.toList(), songs.values.toList(), playlists)
        val added = writing.withLock {
            // Hidden while loading: nothing to store.
            if (user !in _shown.value) return null
            library.putSource(user, music).also {
                lastScan?.takeIf { it != "scanning" }?.let { library.putMeta(metaScan, it) }
                library.putMeta(metaFolders, folderKey)
                reloadRoutes()
            }
        }
        library.refreshStatus()
        library.bump()
        AppLog.i("Sources", "User ${user + 1}: ${songs.size} songs, $added not already in the library")
        return songs.size
    }

    private suspend fun reloadRoutes() {
        val fresh = library.sources()
        routes.keys.retainAll(fresh.keys)
        routes.putAll(fresh)
    }

    private companion object {
        const val PAGE = 500
    }
}

/**
 * Whose "Server name in AudioMuse" a request uses: the user whose song the first of [ids] is ([owner] gives
 * another user's, null for the user in use's), so each server's songs go with its own name. A request about
 * no song (a text search, a mood radio) uses the music shown: the user in use's when theirs shows, else the
 * first other user shown.
 */
fun audioMuseUserFor(ids: List<String>, owner: (String) -> Int?, active: Int, shown: Set<Int>): Int {
    ids.firstOrNull()?.let { return owner(it) ?: active }
    return if (active in shown || shown.isEmpty()) active else shown.min()
}
