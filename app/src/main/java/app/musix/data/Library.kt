package app.musix.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** What a sync stores on the phone. [songAliases] maps ids of dropped duplicate songs to the kept song. */
@Serializable
data class LibraryCache(
    val syncedAt: Long,
    val folderIds: List<String> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val songAliases: Map<String, String> = emptyMap(),
    val playlists: List<Playlist> = emptyList(),
)

private val MARKS = Regex("\\p{M}+")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

fun normalizeKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_WORD, " ")
        .trim()

data class Genre(val name: String, val songCount: Int)

/**
 * The synced library with every lookup, sort order and search key prepared once, off the main thread,
 * so screens only read ready-made lists.
 */
class LibraryIndex(val cache: LibraryCache) {
    val albums: List<Album> = cache.albums
    val songCount: Int get() = cache.songs.size
    val playlists: List<Playlist> get() = cache.playlists

    private val albumKey: Map<String, String> = cache.albums.associate { it.id to normalizeKey(it.name) }
    private val artistKey: Map<String, String> = cache.artists.associate { it.id to normalizeKey(it.name) }

    val artists: List<Artist> = cache.artists.sortedBy { artistKey[it.id] }
    private val byName: List<Album> = cache.albums.sortedBy { albumKey[it.id] }
    private val byNewest: List<Album> = cache.albums.sortedByDescending { it.created ?: "" }
    private val byYear: List<Album> = cache.albums.sortedByDescending { it.year ?: 0 }
    private val byPlays: List<Album> = cache.albums.sortedByDescending { it.playCount }

    private val songsById: Map<String, Song> = cache.songs.associateBy { it.id }
    private val albumByAnyId: Map<String, Album> = buildMap {
        for (album in cache.albums) for (id in album.allIds) put(id, album)
    }
    private val artistById: Map<String, Artist> = cache.artists.associateBy { it.id }
    private val artistByName: Map<String, Artist> = cache.artists.associateBy { artistKey[it.id] ?: "" }
    private val songsByAlbum: Map<String, List<Song>> = cache.songs
        .filter { it.albumId != null }
        .groupBy { it.albumId!! }
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }, { it.title })) }
    private val albumsByArtist: Map<String, List<Album>> = cache.albums
        .filter { it.artistId != null }
        .groupBy { it.artistId!! }
    private val songsByArtist: Map<String, List<Song>> = cache.songs
        .filter { it.artistId != null }
        .groupBy { it.artistId!! }

    /** Release categories present in the library, in display order, with album counts. */
    val categories: List<Pair<AlbumCategory, Int>> = cache.albums
        .groupingBy { it.category }.eachCount()
        .toList()
        .sortedBy { it.first.ordinal }

    /** Genres by number of songs, for genre tiles and radios. */
    val genres: List<Genre> = cache.songs
        .mapNotNull { it.genre?.trim()?.takeIf { g -> g.isNotEmpty() } }
        .groupingBy { it }.eachCount()
        .map { Genre(it.key, it.value) }
        .sortedByDescending { it.songCount }
    private val songsByGenre: Map<String, List<Song>> = cache.songs
        .filter { !it.genre.isNullOrBlank() }
        .groupBy { it.genre!!.trim().lowercase(Locale.ROOT) }

    // Search keys, prepared once.
    private val artistSearch = artists.map { artistKey[it.id] ?: "" }
    private val albumSearch = byName.map { (albumKey[it.id] ?: "") + " " + normalizeKey(it.artist) }
    private val songSearch = cache.songs.map { normalizeKey(it.title + " " + it.artist + " " + it.album) }

    fun song(id: String): Song? = songsById[id] ?: cache.songAliases[id]?.let { songsById[it] }
    fun album(id: String): Album? = albumByAnyId[id]
    fun artist(id: String): Artist? = artistById[id]
    fun artistByName(name: String): Artist? = artistByName[normalizeKey(name)]
    fun songsOf(album: Album): List<Song> = songsByAlbum[album.id].orEmpty()
    fun songsOfGenre(genre: String): List<Song> = songsByGenre[genre.trim().lowercase(Locale.ROOT)].orEmpty()

    fun albumsOf(artist: Artist): List<Album> {
        val byId = albumsByArtist[artist.id].orEmpty()
        if (byId.isNotEmpty()) return byId
        val key = artistKey[artist.id] ?: normalizeKey(artist.name)
        return albums.filter { normalizeKey(it.artist) == key }
    }

    /** Songs the artist performs, on their own albums or elsewhere. */
    fun songsOf(artist: Artist): List<Song> {
        val direct = songsByArtist[artist.id].orEmpty()
        if (direct.isNotEmpty()) return direct
        val key = artistKey[artist.id] ?: normalizeKey(artist.name)
        return cache.songs.filter { normalizeKey(it.artist) == key }
    }

    fun sortedAlbums(sort: String): List<Album> = when (sort) {
        "newest" -> byNewest
        "frequent" -> byPlays
        "year" -> byYear
        "random" -> albums.shuffled()
        else -> byName
    }

    /** Call off the main thread for big libraries. */
    fun search(query: String): SearchResults {
        val terms = normalizeKey(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return SearchResults(emptyList(), emptyList(), emptyList())
        fun hit(key: String) = terms.all { key.contains(it) }
        return SearchResults(
            artists = artists.indices.filter { hit(artistSearch[it]) }.take(30).map { artists[it] },
            albums = byName.indices.filter { hit(albumSearch[it]) }.take(40).map { byName[it] },
            songs = cache.songs.indices.asSequence().filter { hit(songSearch[it]) }.take(100).map { cache.songs[it] }.toList(),
        )
    }
}

class LibraryRepository(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val mutex = Mutex()
    private val _index = MutableStateFlow<LibraryIndex?>(null)
    val index: StateFlow<LibraryIndex?> = _index.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val current: LibraryIndex? get() = _index.value

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            _index.value = runCatching<LibraryIndex?> {
                if (file.exists()) file.inputStream().buffered().use { LibraryIndex(CacheJson.decodeFromStream<LibraryCache>(it)) } else null
            }.getOrNull()
            _loaded.value = true
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun replace(cache: LibraryCache) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val index = LibraryIndex(cache)
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().buffered().use { CacheJson.encodeToStream(LibraryCache.serializer(), cache, it) }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            _index.value = index
            _loaded.value = true
        }
    }

    /** Keeps the cached playlist list current after a live refresh. */
    suspend fun updatePlaylists(playlists: List<Playlist>) {
        val index = current ?: return
        if (index.cache.playlists == playlists) return
        replace(index.cache.copy(playlists = playlists))
    }
}
