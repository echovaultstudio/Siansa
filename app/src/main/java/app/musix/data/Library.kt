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
)

fun normalizeKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

/** The synced library with lookups prepared once. */
class LibraryIndex(val cache: LibraryCache) {
    val artists: List<Artist> = cache.artists.sortedBy { normalizeKey(it.name) }
    val albums: List<Album> = cache.albums
    val songCount: Int get() = cache.songs.size

    private val songsById: Map<String, Song> = cache.songs.associateBy { it.id }
    private val albumByAnyId: Map<String, Album> = buildMap {
        for (album in cache.albums) for (id in album.allIds) put(id, album)
    }
    private val artistById: Map<String, Artist> = cache.artists.associateBy { it.id }
    private val artistByName: Map<String, Artist> = cache.artists.associateBy { normalizeKey(it.name) }
    private val songsByAlbum: Map<String, List<Song>> = cache.songs
        .filter { it.albumId != null }
        .groupBy { it.albumId!! }
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }, { it.title })) }
    private val albumsByArtist: Map<String, List<Album>> = cache.albums
        .filter { it.artistId != null }
        .groupBy { it.artistId!! }

    fun song(id: String): Song? = songsById[id] ?: cache.songAliases[id]?.let { songsById[it] }
    fun album(id: String): Album? = albumByAnyId[id]
    fun artist(id: String): Artist? = artistById[id]
    fun artistByName(name: String): Artist? = artistByName[normalizeKey(name)]
    fun songsOf(album: Album): List<Song> = songsByAlbum[album.id].orEmpty()

    fun albumsOf(artist: Artist): List<Album> {
        val byId = albumsByArtist[artist.id].orEmpty()
        if (byId.isNotEmpty()) return byId
        val key = normalizeKey(artist.name)
        return albums.filter { normalizeKey(it.artist) == key }
    }

    /** Songs the artist performs, on their own albums or elsewhere. */
    fun songsOf(artist: Artist): List<Song> {
        val key = normalizeKey(artist.name)
        return cache.songs.filter { it.artistId == artist.id || normalizeKey(it.artist) == key }
    }

    fun sortedAlbums(sort: String): List<Album> = when (sort) {
        "newest" -> albums.sortedByDescending { it.created ?: "" }
        "frequent" -> albums.sortedByDescending { it.playCount }
        "year" -> albums.sortedByDescending { it.year ?: 0 }
        "random" -> albums.shuffled()
        else -> albums.sortedBy { normalizeKey(it.name) }
    }

    fun search(query: String): SearchResults {
        val terms = normalizeKey(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return SearchResults(emptyList(), emptyList(), emptyList())
        fun matches(text: String): Boolean {
            val key = normalizeKey(text)
            return terms.all { key.contains(it) }
        }
        return SearchResults(
            artists = artists.filter { matches(it.name) }.take(30),
            albums = albums.filter { matches(it.name + " " + it.artist) }.take(40),
            songs = cache.songs.filter { matches(it.title + " " + it.artist + " " + it.album) }.take(100),
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
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().buffered().use { CacheJson.encodeToStream(LibraryCache.serializer(), cache, it) }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            _index.value = LibraryIndex(cache)
            _loaded.value = true
        }
    }
}
