package app.musix.data

import app.musix.data.LibraryRepository.Companion.signature
import app.musix.log.AppLog
import app.musix.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

enum class SyncPhase(val label: String) {
    IDLE("Not running"),
    CONNECTING("Connecting"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    SONGS("Songs"),
    SAVING("Saving"),
    DONE("Done"),
    FAILED("Failed"),
}

data class SyncProgress(
    val phase: SyncPhase = SyncPhase.IDLE,
    val libraryName: String? = null,
    val libraryIndex: Int = 0,
    val libraryCount: Int = 0,
    val artists: Int = 0,
    val albums: Int = 0,
    val songs: Int = 0,
    val songsExpected: Int = 0,
    val merged: Int = 0,
    val changedAlbums: Int = -1,
    val upToDate: Boolean = false,
    val quiet: Boolean = false,
    val error: String? = null,
) {
    val running: Boolean get() = phase in setOf(SyncPhase.CONNECTING, SyncPhase.ARTISTS, SyncPhase.ALBUMS, SyncPhase.SONGS, SyncPhase.SAVING)

    /** 0..1 when the share done is known, null for an open-ended step. */
    val fraction: Float?
        get() = when (phase) {
            SyncPhase.SONGS -> if (songsExpected > 0) (songs.toFloat() / songsExpected).coerceIn(0f, 1f) else null
            SyncPhase.SAVING, SyncPhase.DONE -> 1f
            else -> null
        }
}

/**
 * Keeps the phone's library in step with Navidrome, like Symfonium:
 * - nothing to do when Navidrome hasn't scanned since the last sync (only hearts, play counts and playlists refresh);
 * - otherwise the album list is compared and only new or changed albums have their songs fetched;
 * - a full sync, or a first one, fetches every song page by page.
 *
 * Every Navidrome library is kept on the phone, each album knowing which it's in (issue #376); the libraries
 * chosen in Settings are only which of them show ([LibraryRepository.showFolders]), so switching one is instant.
 */
class LibrarySync(
    private val api: SubsonicApi,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()
    private var job: Job? = null

    /** [full] fetches everything again; [quiet] is the automatic check at app start. */
    fun start(full: Boolean = false, quiet: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                run(full, quiet)
            } catch (e: CancellationException) {
                _progress.update { it.copy(phase = SyncPhase.FAILED, error = "Sync was stopped") }
                throw e
            } catch (e: Exception) {
                AppLog.e("Sync", "Failed", e, event = "sync.failed", fields = mapOf("full" to full))
                _progress.update { it.copy(phase = SyncPhase.FAILED, error = e.message ?: e.javaClass.simpleName) }
            } finally {
                library.refreshStatus()
                library.bump()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun run(forceFull: Boolean, quiet: Boolean) {
        _progress.value = SyncProgress(phase = SyncPhase.CONNECTING, quiet = quiet)
        val all = api.musicFolders()
        // Every library, each in its own pass so its albums know where they're from (issue #376); one library
        // needs no filter.
        val passes: List<MusicFolder?> = if (all.size <= 1) listOf(null) else all
        val folderKey = syncScope(all)

        val synced = library.meta(LibraryRepository.META_SYNCED_AT) != null
        val sameFolders = library.meta(LibraryRepository.META_SCOPE) == folderKey
        val lastScan = runCatching { api.lastScan() }.getOrNull()
        AppLog.i("Sync", "Start full=$forceFull quiet=$quiet lastScan=$lastScan libraries=${all.size}", event = "sync.started",
            fields = mapOf("full" to forceFull, "quiet" to quiet, "last_scan" to lastScan, "folders" to folderKey))

        if (!forceFull && synced && sameFolders && lastScan != null && lastScan != "scanning" &&
            lastScan == library.meta(LibraryRepository.META_LAST_SCAN)
        ) {
            // Navidrome hasn't scanned since: the music is the same. Refresh what changes without a scan.
            refreshExtras()
            library.putMeta(LibraryRepository.META_SYNCED_AT, System.currentTimeMillis().toString())
            AppLog.i("Sync", "No scan since last sync, library unchanged", event = "sync.completed", fields = mapOf("changed_albums" to 0, "up_to_date" to true))
            _progress.update { it.copy(phase = SyncPhase.DONE, upToDate = true, changedAlbums = 0) }
            return
        }

        val artists = LinkedHashMap<String, Artist>()
        val albums = LinkedHashMap<String, Album>()
        passes.forEachIndexed { index, folder ->
            val name = folder?.name ?: if (all.size > 1) "All libraries" else all.firstOrNull()?.name
            _progress.update {
                it.copy(phase = SyncPhase.ARTISTS, libraryName = name, libraryIndex = index + 1, libraryCount = passes.size)
            }
            api.artists(folder?.id).forEach { artists.putIfAbsent(it.id, it) }
            _progress.update { it.copy(artists = artists.size, phase = SyncPhase.ALBUMS) }
            var offset = 0
            while (true) {
                val page = api.albums("alphabeticalByName", PAGE, offset, folder?.id)
                page.forEach { albums.putIfAbsent(it.id, it.copy(folderId = folder?.id)) }
                _progress.update { it.copy(albums = albums.size) }
                if (page.size < PAGE) break
                offset += PAGE
            }
        }
        library.replaceArtists(artists.values.toList())

        val stored = library.albumSignatures()
        val known = if (sameFolders) stored else emptyMap()
        val changed = albums.values.filter { known[it.id] != it.signature() }
        val removed = stored.keys - albums.keys
        val full = forceFull || !synced || !sameFolders || changed.size > maxOf(300, albums.size / 3)
        AppLog.i("Sync", "${albums.size} albums on server, ${changed.size} new or changed, ${removed.size} removed, full=$full", event = "sync.progress",
            fields = mapOf("albums_on_server" to albums.size, "changed" to changed.size, "removed" to removed.size, "full" to full))

        if (full) {
            val expected = albums.values.sumOf { it.songCount }
            _progress.update { it.copy(phase = SyncPhase.SONGS, songs = 0, songsExpected = expected, changedAlbums = albums.size) }
            val songs = ArrayList<Song>(expected)
            for (folder in passes) {
                var offset = 0
                while (true) {
                    val page = api.songsPage(offset, PAGE, folder?.id)
                    songs += page
                    _progress.update { it.copy(songs = songs.size, songsExpected = maxOf(it.songsExpected, songs.size)) }
                    if (page.size < PAGE) break
                    offset += PAGE
                }
            }
            _progress.update { it.copy(phase = SyncPhase.SAVING) }
            library.replaceAllSongs(songs.distinctBy { it.id })
            library.putAlbumsAndSongs(albums.values.toList(), emptyList(), emptyList(), removed)
        } else {
            // Only the albums that changed: their songs are fetched album by album, a few at a time.
            val expected = changed.sumOf { it.songCount }
            _progress.update { it.copy(phase = SyncPhase.SONGS, songs = 0, songsExpected = expected, changedAlbums = changed.size) }
            val done = AtomicInteger(0)
            val permits = Semaphore(6)
            val songs = coroutineScope {
                changed.map { album ->
                    async {
                        permits.withPermit {
                            api.album(album.id).songs.also { list ->
                                val n = done.addAndGet(list.size)
                                _progress.update { it.copy(songs = n) }
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            _progress.update { it.copy(phase = SyncPhase.SAVING) }
            // Every album is stored again (cheap), which also refreshes play counts for "Most played".
            library.putAlbumsAndSongs(albums.values.toList(), songs, changed.map { it.id }, removed)
        }

        library.putFolderNames(all)
        library.putMeta(LibraryRepository.META_SCOPE, folderKey)
        val merged = library.showFolders(settings.current.syncFolders)
        refreshExtras()
        lastScan?.takeIf { it != "scanning" }?.let { library.putMeta(LibraryRepository.META_LAST_SCAN, it) }
        library.putMeta(LibraryRepository.META_SYNCED_AT, System.currentTimeMillis().toString())
        library.refreshStatus()
        val status = library.status.value
        AppLog.i("Sync", "Done: ${status?.artists} artists, ${status?.albums} albums, ${status?.songs} songs, $merged merged", event = "sync.completed",
            fields = mapOf("artists" to status?.artists, "albums" to status?.albums, "songs" to status?.songs, "merged" to merged))
        _progress.update {
            it.copy(
                phase = SyncPhase.DONE,
                artists = status?.artists ?: it.artists,
                albums = status?.albums ?: it.albums,
                songs = status?.songs ?: it.songs,
                songsExpected = status?.songs ?: it.songsExpected,
                merged = merged,
            )
        }
    }

    /** Hearts and playlists change without a Navidrome scan, so they're refreshed on every sync. */
    private suspend fun refreshExtras() {
        runCatching { api.starredSongs().map { it.id } }
            .onSuccess { library.setStarredIds(it) }
            .onFailure { AppLog.w("Sync", "Liked songs couldn't be loaded: ${it.message}") }
        runCatching { api.playlists() }
            .onSuccess { library.updatePlaylists(it) }
            .onFailure { AppLog.w("Sync", "Playlists couldn't be loaded: ${it.message}") }
    }

    private companion object {
        const val PAGE = 500
    }
}

/** What the phone keeps after a sync: every library on the server, by id (issue #376). */
internal fun syncScope(all: List<MusicFolder>): String = "all:" + all.map { it.id }.sorted().joinToString(",")
