package app.musix.data

import app.musix.log.AppLog
import app.musix.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Copies the Navidrome library to the phone, like Symfonium: artists, albums and every song of the
 * chosen libraries, page by page. Albums that exist in more than one library are merged into one.
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

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                run()
            } catch (e: CancellationException) {
                _progress.update { it.copy(phase = SyncPhase.FAILED, error = "Sync was stopped") }
                throw e
            } catch (e: Exception) {
                AppLog.e("Sync", "Failed", e)
                _progress.update { it.copy(phase = SyncPhase.FAILED, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun run() {
        _progress.value = SyncProgress(phase = SyncPhase.CONNECTING)
        val all = api.musicFolders()
        AppLog.i("Sync", "Started. Libraries on server: ${all.joinToString { it.name + " (" + it.id + ")" }}; chosen: ${settings.current.syncFolders}")
        val chosen = settings.current.syncFolders
        val folders = all.filter { it.id in chosen }
        // Nothing chosen, or every library chosen: one pass without a library filter.
        val passes: List<MusicFolder?> = if (folders.isEmpty() || folders.size == all.size) listOf(null) else folders

        val artists = LinkedHashMap<String, Artist>()
        val albums = ArrayList<Album>()
        val songs = ArrayList<Song>()

        passes.forEachIndexed { index, folder ->
            val name = folder?.name ?: if (all.size > 1) "All libraries" else all.firstOrNull()?.name
            _progress.update {
                it.copy(phase = SyncPhase.ARTISTS, libraryName = name, libraryIndex = index + 1, libraryCount = passes.size)
            }
            api.artists(folder?.id).forEach { artists.putIfAbsent(it.id, it) }
            _progress.update { it.copy(artists = artists.size) }

            _progress.update { it.copy(phase = SyncPhase.ALBUMS) }
            var offset = 0
            val passAlbums = ArrayList<Album>()
            while (true) {
                val page = api.albums("alphabeticalByName", PAGE, offset, folder?.id)
                passAlbums += page
                _progress.update { it.copy(albums = albums.size + passAlbums.size) }
                if (page.size < PAGE) break
                offset += PAGE
            }
            albums += passAlbums

            val expected = passAlbums.sumOf { it.songCount }
            _progress.update { it.copy(phase = SyncPhase.SONGS, songsExpected = it.songs + expected) }
            offset = 0
            while (true) {
                val page = api.songsPage(offset, PAGE, folder?.id)
                songs += page
                _progress.update { it.copy(songs = songs.size, songsExpected = maxOf(it.songsExpected, songs.size)) }
                if (page.size < PAGE) break
                offset += PAGE
            }
        }

        _progress.update { it.copy(phase = SyncPhase.SAVING) }
        val playlists = runCatching { api.playlists() }
            .onFailure { AppLog.w("Sync", "Playlists couldn't be loaded", it) }
            .getOrDefault(emptyList())
        val cache = merge(artists.values.toList(), albums, songs, passes.mapNotNull { it?.id })
            .copy(playlists = playlists)
        AppLog.i(
            "Sync",
            "Fetched ${artists.size} artists, ${albums.size} albums, ${songs.size} songs, ${playlists.size} playlists; " +
                "kept ${cache.albums.size} albums and ${cache.songs.size} songs after merging",
        )
        library.replace(cache)
        _progress.update {
            it.copy(
                phase = SyncPhase.DONE,
                artists = cache.artists.size,
                albums = cache.albums.size,
                songs = cache.songs.size,
                songsExpected = cache.songs.size,
                merged = albums.size - cache.albums.size,
            )
        }
    }

    companion object {
        private const val PAGE = 500

        /** Merges albums with the same name and album artist, and the duplicate songs inside them. */
        fun merge(artists: List<Artist>, albums: List<Album>, songs: List<Song>, folderIds: List<String>): LibraryCache {
            val groups = albums
                .distinctBy { it.id }
                .groupBy { normalizeKey(it.name) + "|" + normalizeKey(it.artist) }
            val primaryOf = HashMap<String, String>()
            val mergedAlbums = groups.values.map { group ->
                val primary = group.maxByOrNull { it.songCount } ?: group.first()
                group.forEach { primaryOf[it.id] = primary.id }
                if (group.size == 1) {
                    primary
                } else {
                    primary.copy(
                        ids = listOf(primary.id) + group.map { it.id }.filter { it != primary.id },
                        playCount = group.sumOf { it.playCount },
                        created = group.mapNotNull { it.created }.maxOrNull(),
                        year = primary.year ?: group.firstNotNullOfOrNull { it.year },
                        coverArt = primary.coverArt ?: group.firstNotNullOfOrNull { it.coverArt },
                    )
                }
            }

            val aliases = HashMap<String, String>()
            val kept = LinkedHashMap<String, Song>()
            val seen = HashMap<String, String>()
            for (song in songs) {
                if (song.id in kept || song.id in aliases) continue
                val albumId = song.albumId?.let { primaryOf[it] ?: it }
                val moved = if (albumId != song.albumId) song.copy(albumId = albumId) else song
                val key = listOf(
                    albumId ?: "",
                    (song.disc ?: 1).toString(),
                    song.track?.toString() ?: "d${song.durationSec / 3}",
                    normalizeKey(song.title),
                ).joinToString("|")
                val existing = seen[key]
                if (existing != null && albumId != null) {
                    aliases[song.id] = existing
                } else {
                    seen[key] = song.id
                    kept[song.id] = moved
                }
            }

            val counts = kept.values.groupingBy { it.albumId }.eachCount()
            val finalAlbums = mergedAlbums.map { album ->
                val count = counts[album.id] ?: 0
                if (count > 0 && count != album.songCount) album.copy(songCount = count) else album
            }
            val mergedArtists = artists
                .distinctBy { it.id }
                .groupBy { normalizeKey(it.name) }
                .values.map { group -> group.maxByOrNull { it.albumCount } ?: group.first() }

            return LibraryCache(
                syncedAt = System.currentTimeMillis(),
                folderIds = folderIds,
                artists = mergedArtists,
                albums = finalAlbums,
                songs = kept.values.toList(),
                songAliases = aliases,
            )
        }
    }
}
