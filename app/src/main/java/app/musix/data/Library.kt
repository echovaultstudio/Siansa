package app.musix.data

import android.content.Context
import androidx.room.withTransaction
import app.musix.data.db.AlbumEntity
import app.musix.data.db.SongTitleRow
import app.musix.data.db.ArtistEntity
import app.musix.data.db.LibraryDatabase
import app.musix.data.db.MetaEntity
import app.musix.data.db.PlaylistEntity
import app.musix.data.db.SongEntity
import app.musix.data.db.SourceEntity
import app.musix.offline.OfflineShelf
import app.musix.offline.OnlyDownloaded
import app.musix.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

private val MARKS = Regex("\\p{M}+")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

fun normalizeKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_WORD, " ")
        .trim()

data class Genre(val name: String, val songCount: Int)

/** What the phone holds after a sync; null in [LibraryRepository.status] means never synced. */
data class LibraryStatus(val syncedAt: Long, val artists: Int, val albums: Int, val songs: Int)

private const val TYPE_SEPARATOR = "\u001F"
private const val CHUNK = 900

/**
 * The synced library in an on-phone SQLite database. Screens ask for exactly what they show, so
 * nothing has to be loaded at start-up. [version] changes after every sync, for screens to reload.
 */
class LibraryRepository(context: Context) {
    private val db = LibraryDatabase.open(context)
    private val dao = db.dao()

    private val _status = MutableStateFlow<LibraryStatus?>(null)
    val status: StateFlow<LibraryStatus?> = _status.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        // The JSON library file of earlier versions is replaced by the database.
        File(context.filesDir, "library.json").delete()
    }

    val isSynced: Boolean get() = _status.value != null

    /**
     * What's on the phone while "Only downloaded music" is on (issue #141), else null. Every list this
     * gives out is then narrowed to it; looking up one song, album or artist by id still works.
     */
    @Volatile var downloadedOnly: () -> OfflineShelf? = { null }
    @Volatile private var onlyCache: OnlyDownloaded? = null

    /** The filter for "Only downloaded music", or null when it's off. */
    suspend fun onlyDownloaded(): OnlyDownloaded? {
        val shelf = downloadedOnly() ?: return null
        onlyCache?.let { if (it.shelf === shelf) return it }
        val albums = shelf.albums.toList().chunked(CHUNK).flatMap { dao.albumGroupIds(it) }.toSet() + shelf.albums
        return OnlyDownloaded(shelf, albums).also { onlyCache = it }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        refreshStatus()
        _loaded.value = true
        bump()
    }

    suspend fun refreshStatus() {
        val syncedAt = dao.meta(META_SYNCED_AT)?.toLongOrNull()
        _status.value = syncedAt?.let { LibraryStatus(it, dao.artistCount(), dao.albumCount(), dao.songCount()) }
    }

    private val albumCovers = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun bump() {
        // A sync may have changed albums' covers.
        albumCovers.clear()
        _version.value++
    }

    // ---- Reading ----

    suspend fun song(id: String): Song? = dao.song(id)?.toSong()

    suspend fun albumCoverOfSongCover(songCover: String): String? = dao.albumCoverOfSongCover(songCover)

    /**
     * An album's cover id as the library has it (`al-<id>_<last change>`), which is what the cover download
     * saves under. Remembered, since every song row asks while its own cover loads.
     */
    suspend fun albumCover(albumId: String): String? = albumCovers[albumId]
        ?: withContext(Dispatchers.IO) { dao.albumCoverArt(albumId) }?.also { albumCovers[albumId] = it }

    /** Songs by id, in the order asked for; ids the library doesn't have are left out. */
    /** Every song's id, title and artist, for matching many songs from other services at once. */
    suspend fun songTitles(): List<SongTitleRow> = withContext(Dispatchers.IO) { dao.songTitles() }

    suspend fun songs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val found = ids.distinct().chunked(CHUNK).flatMap { dao.songs(it) }.associateBy { it.id }
        return ids.mapNotNull { found[it]?.toSong() }
    }

    /**
     * True when some Navidrome libraries are unchecked in Settings → Library sync. Then songs and albums
     * Navidrome sends that the phone doesn't have are from a left-out library and must not show or play.
     */
    suspend fun leavesOutLibraries(): Boolean = !app.musix.mix.mayFetchUnsynced(meta(META_FOLDERS))

    /**
     * [songs] from Navidrome (popular songs, radios, liked songs…) without those from unchecked libraries,
     * using the phone's copy of each. Unchanged when every library is synced.
     */
    suspend fun keepSyncedSongs(songs: List<Song>, why: String): List<Song> {
        onlyDownloaded()?.let { return it.songs(songs) }
        if (songs.isEmpty() || !leavesOutLibraries()) return songs
        val kept = keepKnown(songs, songs(songs.map { it.id }).associateBy { it.id })
        if (kept.size < songs.size) AppLog.i("Library", "$why: left out ${songs.size - kept.size} of ${songs.size} songs from unchecked libraries")
        return kept
    }

    /** [albums] from Navidrome without those from unchecked libraries. Unchanged when every library is synced. */
    suspend fun keepSyncedAlbums(albums: List<Album>, why: String): List<Album> {
        onlyDownloaded()?.let { return it.albums(albums) }
        if (albums.isEmpty() || !leavesOutLibraries()) return albums
        val known = albums.map { it.id }.distinct().chunked(CHUNK).flatMap { dao.knownAlbumIds(it) }.toSet()
        val kept = albums.filter { it.id in known }
        if (kept.size < albums.size) AppLog.i("Library", "$why: left out ${albums.size - kept.size} of ${albums.size} albums from unchecked libraries")
        return kept
    }

    /** The album for any of its ids, with every id of the same album in other libraries. */
    suspend fun album(id: String): Album? {
        val entity = dao.album(id) ?: return null
        val group = dao.albumGroup(entity.groupKey)
        val primary = group.firstOrNull { it.isPrimary } ?: entity
        return primary.toAlbum().copy(ids = group.map { it.id })
    }

    /** Songs of an album and its copies in other libraries, without duplicates, in disc and track order. */
    suspend fun songsOf(album: Album): List<Song> {
        val ids = album.allIds
        val all = ids.chunked(CHUNK).flatMap { dao.songsOfAlbums(it) }
        val order = ids.withIndex().associate { it.value to it.index }
        return all
            .sortedBy { order[it.albumId] ?: Int.MAX_VALUE }
            .distinctBy { listOf(it.disc ?: 1, it.track?.toString() ?: "d${it.durationSec / 3}", normalizeKey(it.title)) }
            .sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }, { it.title }))
            .map { it.toSong() }
            .let { songs -> onlyDownloaded()?.songs(songs) ?: songs }
    }

    suspend fun albums(sort: String): List<Album> = when (sort) {
        "newest" -> dao.albumsByNewest()
        "frequent" -> dao.albumsByPlays()
        "year" -> dao.albumsByYear()
        "random" -> dao.albumsRandom(100_000)
        else -> dao.albumsByName()
    }.map { it.toAlbum() }.onlyDownloadedAlbums()

    suspend fun randomAlbums(count: Int): List<Album> {
        val only = onlyDownloaded() ?: return dao.albumsRandom(count).map { it.toAlbum() }
        return only.albums(dao.albumsRandom(100_000).map { it.toAlbum() }).take(count)
    }

    private suspend fun List<Album>.onlyDownloadedAlbums(): List<Album> = onlyDownloaded()?.albums(this) ?: this

    /** Release categories present, in display order, with album counts. */
    suspend fun categories(): List<Pair<AlbumCategory, Int>> = (onlyDownloaded()?.let { only -> only.albums(dao.primaryAlbums().map { it.toAlbum() }) }
        ?: dao.albumTypes().map { Album(id = "", name = "", releaseTypes = splitTypes(it.releaseTypes), isCompilation = it.isCompilation) })
        .map { it.category }
        .groupingBy { it }.eachCount()
        .toList()
        .sortedBy { it.first.ordinal }

    suspend fun artists(): List<Artist> = dao.artists().map { it.toArtist() }.let { list -> onlyDownloaded()?.artists(list) ?: list }
    suspend fun artist(id: String): Artist? = dao.artist(id)?.toArtist()
    suspend fun artistByName(name: String): Artist? = dao.artistByKey(normalizeKey(name))?.toArtist()

    suspend fun albumsOf(artist: Artist): List<Album> =
        dao.albumsOfArtist(artist.id, normalizeKey(artist.name)).map { it.toAlbum() }.onlyDownloadedAlbums()

    suspend fun songsOf(artist: Artist): List<Song> =
        dao.songsOfArtist(artist.id, artist.name).map { it.toSong() }.distinctBy { songKey(it) }.onlyDownloadedSongs()

    private suspend fun List<Song>.onlyDownloadedSongs(): List<Song> = onlyDownloaded()?.songs(this) ?: this

    /**
     * Albums the artist appears on without being the album artist (a feature, a compilation, a split),
     * newest first. Only from the synced library; empty before a sync.
     */
    suspend fun appearsOn(artist: Artist): List<Album> {
        val ownKey = normalizeKey(artist.name)
        val own = albumsOf(artist).map { it.id }.toSet()
        val songs = dao.songsCrediting(artist.id, "%" + likeEscape(artist.name) + "%").map { it.toSong() }
        val ids = appearsOnAlbumIds(songs, artist.id, artist.name) { it in own }
        return ids.mapNotNull { album(it) }
            .distinctBy { it.id }
            .filterNot { it.id in own || it.artistId == artist.id || normalizeKey(it.artist) == ownKey }
            .sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy { normalizeKey(it.name) })
            .onlyDownloadedAlbums()
            .also { AppLog.d("Library", "Appears on: ${it.size} albums from ${songs.size} songs checked") }
    }

    suspend fun genres(limit: Int = 60): List<Genre> {
        val only = onlyDownloaded() ?: return dao.genres(limit).map { Genre(it.name, it.songCount) }
        val songs = only.shelf.songs.toList().chunked(CHUNK).flatMap { dao.songsWithGenre(it) }.map { it.toSong() }
        return OnlyDownloaded.genres(songs, limit)
    }

    suspend fun songsOfGenre(genre: String, limit: Int): List<Song> {
        val only = onlyDownloaded()
        return dao.songsOfGenre(genre, if (only != null) 100_000 else limit).map { it.toSong() }.distinctBy { songKey(it) }
            .let { list -> only?.songs(list)?.take(limit) ?: list }
    }

    suspend fun starredSongs(): List<Song> = dao.starredSongs().map { it.toSong() }.distinctBy { songKey(it) }.onlyDownloadedSongs()

    suspend fun setStarred(id: String, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun playlists(): List<Playlist> = dao.playlists().map {
        Playlist(it.id, it.name, it.songCount, it.durationSec, it.coverArt, it.owner, it.comment, it.changed)
    }.let { list -> onlyDownloaded()?.playlists(list) ?: list }

    /**
     * Playlists fresh from Navidrome ([fetch], the user in use's) stored and returned together with other
     * shown users' playlists. When Navidrome can't be reached, the stored ones; before a first sync, the error.
     */
    suspend fun refreshPlaylists(fetch: suspend () -> List<Playlist>): List<Playlist> {
        runCatching { fetch() }
            .onSuccess { updatePlaylists(it) }
            .onFailure { if (!isSynced) throw it }
        return playlists()
    }

    suspend fun updatePlaylists(playlists: List<Playlist>) {
        db.withTransaction {
            dao.deleteAllPlaylists()
            playlists.chunked(CHUNK).forEach { chunk -> dao.forgetSources(chunk.map { it.id }) }
            dao.putPlaylists(playlists.mapIndexed { i, p ->
                PlaylistEntity(p.id, p.name, p.songCount, p.durationSec, p.coverArt, p.owner, p.comment, p.changed, i)
            })
        }
    }

    /** Every word must appear in the name (accents and case ignored). */
    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val terms = normalizeKey(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return@withContext SearchResults(emptyList(), emptyList(), emptyList())
        val first = terms.maxBy { it.length }
        fun all(key: String) = terms.all { key.contains(it) }
        val only = onlyDownloaded()
        val artists = dao.searchArtists(first, 200).filter { all(it.sortKey) }.map { it.toArtist() }
        val albums = dao.searchAlbums(first, 400).filter { all(it.sortKey + " " + it.artistKey) }.map { it.toAlbum() }
        val songs = dao.searchSongs(first, 1500).filter { all(it.searchKey) }.map { it.toSong() }.distinctBy { songKey(it) }
        SearchResults(
            artists = (only?.artists(artists) ?: artists).take(30),
            albums = (only?.albums(albums) ?: albums).take(40),
            songs = (only?.songs(songs) ?: songs).take(100),
        )
    }

    // ---- Writing (used by sync) ----

    suspend fun albumSignatures(): Map<String, String> = dao.albumSignatures().associate { it.id to it.signature }
    suspend fun meta(key: String): String? = dao.meta(key)
    suspend fun putMeta(key: String, value: String) = dao.putMeta(MetaEntity(key, value))

    suspend fun replaceArtists(artists: List<Artist>) = db.withTransaction {
        dao.deleteAllArtists()
        artists.distinctBy { it.id }.chunked(CHUNK).forEach { chunk ->
            dao.forgetSources(chunk.map { it.id })
            dao.putArtists(chunk.map { it.toEntity() })
        }
    }

    /** Stores albums and their songs; [replaceSongsOf] are albums whose songs are swapped for [songs]. */
    suspend fun putAlbumsAndSongs(albums: List<Album>, songs: List<Song>, replaceSongsOf: Collection<String>, removedAlbums: Collection<String>) =
        db.withTransaction {
            replaceSongsOf.toList().chunked(CHUNK).forEach { dao.deleteSongsOfAlbums(it) }
            removedAlbums.toList().chunked(CHUNK).forEach {
                dao.deleteSongsOfAlbums(it)
                dao.deleteAlbums(it)
            }
            albums.chunked(500).forEach { chunk ->
                dao.forgetSources(chunk.map { it.id })
                dao.putAlbums(chunk.map { it.toEntity() })
            }
            songs.chunked(500).forEach { chunk ->
                dao.forgetSources(chunk.map { it.id })
                dao.putSongs(chunk.map { it.toEntity() })
            }
        }

    suspend fun replaceAllSongs(songs: List<Song>) = db.withTransaction {
        dao.deleteAllSongs()
        songs.chunked(500).forEach { chunk ->
            dao.forgetSources(chunk.map { it.id })
            dao.putSongs(chunk.map { it.toEntity() })
        }
    }

    // ---- Other users' music (issue #141) ----

    /** Which items come from which other user, for sending their requests with that user's login. */
    suspend fun sources(): Map<String, Int> = dao.sources().associate { it.id to it.source }

    /** Users whose music is in the library besides the user in use's. */
    suspend fun sourceUsers(): List<Int> = dao.sourceUsers()

    /** Removes everything that came from [user]. */
    suspend fun removeSource(user: Int) {
        db.withTransaction {
            dao.deleteSongsOfSource(user)
            dao.deleteAlbumsOfSource(user)
            dao.deleteArtistsOfSource(user)
            dao.deletePlaylistsOfSource(user)
            dao.deleteSource(user)
        }
        recomputePrimaries()
    }

    /**
     * Replaces [user]'s music with [music]. Items the library already has from someone else (the same
     * server, so the same song) are left as they are; the rest are stored and listed as [user]'s.
     * Returns how many songs were added.
     */
    suspend fun putSource(user: Int, music: GuestMusic): Int {
        val added = db.withTransaction {
            dao.deleteSongsOfSource(user)
            dao.deleteAlbumsOfSource(user)
            dao.deleteArtistsOfSource(user)
            dao.deletePlaylistsOfSource(user)
            dao.deleteSource(user)
            suspend fun known(ids: List<String>, query: suspend (List<String>) -> List<String>) =
                ids.distinct().chunked(CHUNK).flatMap { query(it) }.toSet()
            val plan = GuestMusic.plan(
                music,
                knownSongs = known(music.songs.map { it.id }) { dao.knownSongIds(it) },
                knownAlbums = known(music.albums.map { it.id }) { dao.knownAlbumIds(it) },
                knownArtists = known(music.artists.map { it.id }) { dao.knownArtistIds(it) },
                knownPlaylists = known(music.playlists.map { it.id }) { dao.knownPlaylistIds(it) },
            )
            plan.artists.chunked(CHUNK).forEach { chunk -> dao.putArtists(chunk.map { it.toEntity() }) }
            plan.albums.chunked(500).forEach { chunk -> dao.putAlbums(chunk.map { it.toEntity() }) }
            plan.songs.chunked(500).forEach { chunk -> dao.putSongs(chunk.map { it.toEntity() }) }
            // After the user in use's own playlists.
            dao.putPlaylists(plan.playlists.mapIndexed { i, p ->
                PlaylistEntity(p.id, p.name, p.songCount, p.durationSec, p.coverArt, p.owner, p.comment, p.changed, GUEST_POSITION + user * 10_000 + i)
            })
            plan.ids.chunked(500).forEach { chunk -> dao.putSources(chunk.map { SourceEntity(it, user) }) }
            plan.songs.size
        }
        recomputePrimaries()
        return added
    }

    /** Picks one album per name + album artist (the copy with most songs) to stand for the group in lists. */
    suspend fun recomputePrimaries(): Int = db.withTransaction {
        val rows = dao.albumGroups()
        val primaries = rows.groupBy { it.groupKey }.values.map { group -> group.maxBy { it.songCount }.id }
        dao.clearPrimary()
        primaries.chunked(CHUNK).forEach { dao.markPrimary(it) }
        rows.size - primaries.size
    }

    suspend fun setStarredIds(ids: Collection<String>) = db.withTransaction {
        dao.clearStarred()
        ids.toList().chunked(CHUNK).forEach { dao.markStarred(it) }
    }

    // ---- Mapping ----

    private fun songKey(s: Song) = normalizeKey(s.title) + "|" + normalizeKey(s.artist) + "|" + normalizeKey(s.album)

    private fun splitTypes(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(TYPE_SEPARATOR)

    private fun SongEntity.toSong() = Song(
        id, title, artist, artistId, album, albumId, coverArt, durationSec, track, disc, year, genre,
        trackGain, albumGain, trackPeak, albumPeak, starred,
    )

    private fun Song.toEntity() = SongEntity(
        id, title, artist, artistId, album, albumId, coverArt, durationSec, track, disc, year, genre,
        trackGain, albumGain, trackPeak, albumPeak, starred,
        searchKey = normalizeKey("$title $artist $album"),
    )

    private fun AlbumEntity.toAlbum() = Album(
        id = id, name = name, artist = artist, artistId = artistId, coverArt = coverArt, songCount = songCount,
        durationSec = durationSec, year = year, genre = genre, created = created, playCount = playCount,
        releaseTypes = splitTypes(releaseTypes), isCompilation = isCompilation,
    )

    private fun Album.toEntity() = AlbumEntity(
        id = id, name = name, artist = artist, artistId = artistId, coverArt = coverArt, songCount = songCount,
        durationSec = durationSec, year = year, genre = genre, created = created, playCount = playCount,
        releaseTypes = releaseTypes.joinToString(TYPE_SEPARATOR), isCompilation = isCompilation,
        sortKey = normalizeKey(name), artistKey = normalizeKey(artist),
        groupKey = normalizeKey(name) + "|" + normalizeKey(artist),
        signature = signature(),
    )

    private fun ArtistEntity.toArtist() = Artist(id, name, albumCount, coverArt, imageUrl)
    private fun Artist.toEntity() = ArtistEntity(id, name, albumCount, coverArt, imageUrl, normalizeKey(name))

    companion object {
        const val META_SYNCED_AT = "syncedAt"
        const val META_LAST_SCAN = "lastScan"
        const val META_FOLDERS = "folders"
        private const val GUEST_POSITION = 1_000_000

        /** What decides whether an album's songs need fetching again. Play counts are left out on purpose. */
        fun Album.signature(): String =
            listOf(name, artist, songCount, durationSec, year, created, coverArt, releaseTypes.joinToString(","), isCompilation)
                .joinToString("|")
    }
}

/** Songs from Navidrome that the phone has (by id), in their order, as the phone's copy; the rest left out. */
internal fun keepKnown(songs: List<Song>, local: Map<String, Song>): List<Song> = songs.mapNotNull { local[it.id] }
