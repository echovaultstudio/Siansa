package app.musix.data

import android.content.Context
import androidx.room.withTransaction
import app.musix.data.db.AlbumEntity
import app.musix.data.db.ArtistEntity
import app.musix.data.db.LibraryDatabase
import app.musix.data.db.MetaEntity
import app.musix.data.db.PlaylistEntity
import app.musix.data.db.SongEntity
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

    suspend fun load() = withContext(Dispatchers.IO) {
        refreshStatus()
        _loaded.value = true
        bump()
    }

    suspend fun refreshStatus() {
        val syncedAt = dao.meta(META_SYNCED_AT)?.toLongOrNull()
        _status.value = syncedAt?.let { LibraryStatus(it, dao.artistCount(), dao.albumCount(), dao.songCount()) }
    }

    fun bump() {
        _version.value++
    }

    // ---- Reading ----

    suspend fun song(id: String): Song? = dao.song(id)?.toSong()

    /** Songs by id, in the order asked for; ids the library doesn't have are left out. */
    suspend fun songs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val found = ids.distinct().chunked(CHUNK).flatMap { dao.songs(it) }.associateBy { it.id }
        return ids.mapNotNull { found[it]?.toSong() }
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
    }

    suspend fun albums(sort: String): List<Album> = when (sort) {
        "newest" -> dao.albumsByNewest()
        "frequent" -> dao.albumsByPlays()
        "year" -> dao.albumsByYear()
        "random" -> dao.albumsRandom(100_000)
        else -> dao.albumsByName()
    }.map { it.toAlbum() }

    suspend fun randomAlbums(count: Int): List<Album> = dao.albumsRandom(count).map { it.toAlbum() }

    /** Release categories present, in display order, with album counts. */
    suspend fun categories(): List<Pair<AlbumCategory, Int>> = dao.albumTypes()
        .map { Album(id = "", name = "", releaseTypes = splitTypes(it.releaseTypes), isCompilation = it.isCompilation).category }
        .groupingBy { it }.eachCount()
        .toList()
        .sortedBy { it.first.ordinal }

    suspend fun artists(): List<Artist> = dao.artists().map { it.toArtist() }
    suspend fun artist(id: String): Artist? = dao.artist(id)?.toArtist()
    suspend fun artistByName(name: String): Artist? = dao.artistByKey(normalizeKey(name))?.toArtist()

    suspend fun albumsOf(artist: Artist): List<Album> =
        dao.albumsOfArtist(artist.id, normalizeKey(artist.name)).map { it.toAlbum() }

    suspend fun songsOf(artist: Artist): List<Song> =
        dao.songsOfArtist(artist.id, artist.name).map { it.toSong() }.distinctBy { songKey(it) }

    suspend fun genres(limit: Int = 60): List<Genre> = dao.genres(limit).map { Genre(it.name, it.songCount) }

    suspend fun songsOfGenre(genre: String, limit: Int): List<Song> =
        dao.songsOfGenre(genre, limit).map { it.toSong() }.distinctBy { songKey(it) }

    suspend fun starredSongs(): List<Song> = dao.starredSongs().map { it.toSong() }.distinctBy { songKey(it) }

    suspend fun setStarred(id: String, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun playlists(): List<Playlist> = dao.playlists().map {
        Playlist(it.id, it.name, it.songCount, it.durationSec, it.coverArt, it.owner, it.comment, it.changed)
    }

    suspend fun updatePlaylists(playlists: List<Playlist>) {
        db.withTransaction {
            dao.deleteAllPlaylists()
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
        SearchResults(
            artists = dao.searchArtists(first, 200).filter { all(it.sortKey) }.take(30).map { it.toArtist() },
            albums = dao.searchAlbums(first, 400).filter { all(it.sortKey + " " + it.artistKey) }.take(40).map { it.toAlbum() },
            songs = dao.searchSongs(first, 1500).filter { all(it.searchKey) }.map { it.toSong() }.distinctBy { songKey(it) }.take(100),
        )
    }

    // ---- Writing (used by sync) ----

    suspend fun albumSignatures(): Map<String, String> = dao.albumSignatures().associate { it.id to it.signature }
    suspend fun meta(key: String): String? = dao.meta(key)
    suspend fun putMeta(key: String, value: String) = dao.putMeta(MetaEntity(key, value))

    suspend fun replaceArtists(artists: List<Artist>) = db.withTransaction {
        dao.deleteAllArtists()
        artists.distinctBy { it.id }.chunked(CHUNK).forEach { chunk -> dao.putArtists(chunk.map { it.toEntity() }) }
    }

    /** Stores albums and their songs; [replaceSongsOf] are albums whose songs are swapped for [songs]. */
    suspend fun putAlbumsAndSongs(albums: List<Album>, songs: List<Song>, replaceSongsOf: Collection<String>, removedAlbums: Collection<String>) =
        db.withTransaction {
            replaceSongsOf.toList().chunked(CHUNK).forEach { dao.deleteSongsOfAlbums(it) }
            removedAlbums.toList().chunked(CHUNK).forEach {
                dao.deleteSongsOfAlbums(it)
                dao.deleteAlbums(it)
            }
            albums.chunked(500).forEach { chunk -> dao.putAlbums(chunk.map { it.toEntity() }) }
            songs.chunked(500).forEach { chunk -> dao.putSongs(chunk.map { it.toEntity() }) }
        }

    suspend fun replaceAllSongs(songs: List<Song>) = db.withTransaction {
        dao.deleteAllSongs()
        songs.chunked(500).forEach { chunk -> dao.putSongs(chunk.map { it.toEntity() }) }
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

        /** What decides whether an album's songs need fetching again. Play counts are left out on purpose. */
        fun Album.signature(): String =
            listOf(name, artist, songCount, durationSec, year, created, coverArt, releaseTypes.joinToString(","), isCompilation)
                .joinToString("|")
    }
}
