package app.musix.data

import android.content.Context
import app.musix.log.AppLog
import app.musix.net.MusicSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** An artist's popular songs as last saved on the phone. */
@Serializable
data class TopSongsEntry(
    val artistId: String,
    val savedAt: Long,
    val songs: List<Song>,
    // True when the list came from Navidrome; false for the artist's own songs used in its place.
    val fromServer: Boolean = true,
)

/** How long saved popular songs are used before asking Navidrome again. */
const val TOP_SONGS_FRESH_MS = 3L * 24 * 60 * 60 * 1000
const val TOP_SONGS_COUNT = 10
private const val TOP_SONGS_MAX_ARTISTS = 200

/** True while [entry] is recent enough to show without asking Navidrome again. Saved at 0 means "ask again". */
fun isTopSongsFresh(entry: TopSongsEntry, now: Long, maxAgeMs: Long = TOP_SONGS_FRESH_MS): Boolean =
    entry.savedAt > 0 && entry.savedAt <= now && now - entry.savedAt < maxAgeMs

/** [songs] in their order, each replaced by the synced library's copy when there is one. */
fun preferLocal(songs: List<Song>, local: List<Song>): List<Song> {
    val byId = local.associateBy { it.id }
    return songs.map { byId[it.id] ?: it }
}

/**
 * What to save after asking Navidrome. [remote] is null when the request failed; then the saved list is
 * kept as it is (null result), or, when nothing was saved yet, the artist's own songs are used and marked
 * as old so they're replaced on the next visit. An empty answer keeps the saved songs (Navidrome answers
 * empty when Last.fm doesn't respond) or falls back to the artist's own songs.
 */
fun nextTopSongs(
    previous: TopSongsEntry?,
    remote: List<Song>?,
    artistId: String,
    now: Long,
    fallback: () -> List<Song>,
): TopSongsEntry? = when {
    remote == null -> if (previous == null) TopSongsEntry(artistId, 0L, fallback(), fromServer = false) else null
    remote.isNotEmpty() -> TopSongsEntry(artistId, now, remote, fromServer = true)
    previous != null && previous.songs.isNotEmpty() -> previous.copy(savedAt = now)
    else -> TopSongsEntry(artistId, now, fallback(), fromServer = false)
}

/** [entry] first, replacing the artist's older one, keeping at most [max] artists. */
fun putTopSongs(entries: List<TopSongsEntry>, entry: TopSongsEntry, max: Int = TOP_SONGS_MAX_ARTISTS): List<TopSongsEntry> =
    (listOf(entry) + entries.filterNot { it.artistId == entry.artistId }).take(max)

/**
 * An artist's popular songs. Navidrome gets them from Last.fm on every request, which takes a moment,
 * so they're kept on the phone (in memory and in a small file) and shown straight away, and only asked
 * for again after a few days. A request keeps running when the page is left, so the next visit has it.
 */
class TopSongs(
    context: Context,
    private val source: MusicSource,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val json = JsonFile(
        File(context.cacheDir, Users.scoped("top_songs.json")),
        ListSerializer(TopSongsEntry.serializer()),
    ) { emptyList() }
    private val inFlight = HashMap<String, Deferred<List<Song>>>()

    /** The saved songs for this artist, each as synced in the library when it is; null when none are saved. */
    suspend fun cached(artistId: String): TopSongsEntry? {
        val entry = json.read().firstOrNull { it.artistId == artistId } ?: return null
        val songs = preferLocal(entry.songs, library.songs(entry.songs.map { it.id }))
        return entry.copy(songs = library.keepSyncedSongs(songs, "Popular songs"))
    }

    /** Asks Navidrome for the artist's popular songs and saves them; one request per artist at a time. */
    suspend fun refresh(artist: Artist, ownSongs: List<Song>): List<Song> {
        val job = synchronized(inFlight) {
            inFlight[artist.id] ?: scope.async(start = CoroutineStart.LAZY) { fetch(artist, ownSongs) }
                .also { inFlight[artist.id] = it }
        }
        job.invokeOnCompletion { synchronized(inFlight) { if (inFlight[artist.id] === job) inFlight.remove(artist.id) } }
        job.start()
        return job.await()
    }

    private suspend fun fetch(artist: Artist, ownSongs: List<Song>): List<Song> {
        val started = System.currentTimeMillis()
        val remote = try {
            source.topSongs(artist.name, TOP_SONGS_COUNT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Popular songs request failed: ${e.javaClass.simpleName}")
            null
        }
        // Navidrome's popular songs come from every library, also ones left out in Settings → Library sync.
        val matched = remote?.let { library.keepSyncedSongs(preferLocal(it, library.songs(it.map { s -> s.id })), "Popular songs") }
        AppLog.i(TAG, "Popular songs: ${remote?.size ?: "none"} from Navidrome in ${System.currentTimeMillis() - started}ms")
        val previous = json.read().firstOrNull { it.artistId == artist.id }
        val next = nextTopSongs(previous, matched, artist.id, System.currentTimeMillis()) {
            ownSongs.shuffled().take(TOP_SONGS_COUNT)
        } ?: return previous?.let { library.keepSyncedSongs(preferLocal(it.songs, library.songs(it.songs.map { s -> s.id })), "Popular songs") }.orEmpty()
        json.update { putTopSongs(it, next) }
        return next.songs
    }

    private companion object {
        const val TAG = "TopSongs"
    }
}
