package app.siansa.playback

import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * The file type of each song ("flac", "m4a"…) for casting (issue #114). The library keeps no file types, so songs
 * played from it reached the TV without one, and "Lossless" sent every one of them as MP3: an ALAC album went as MP3
 * instead of FLAC (0.52.0-dev, the owner's log: "0 as their original file, 0 converted to FLAC"). Types are kept
 * here as songs with one pass through the app, and asked from the server for the songs about to be cast: one
 * getAlbum per album, else one getSong per song.
 */
object CastFormats {
    private val known = ConcurrentHashMap<String, String>()
    private val askedSongs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val askedAlbums: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Remembers the file types the songs came with. */
    fun record(songs: Collection<Song>) {
        songs.forEach { s -> s.suffix?.takeIf { it.isNotBlank() }?.let { record(s.id, it) } }
    }

    fun record(id: String, suffix: String) {
        if (known.size > MAX_KNOWN) { known.clear(); askedSongs.clear(); askedAlbums.clear() }
        known[id] = suffix.trim().lowercase()
    }

    /** The song's file type: its own, else one learned before; null when nobody has said. */
    fun suffixOf(song: Song): String? = song.suffix?.takeIf { it.isNotBlank() } ?: known[song.id]

    /** What to ask the server for the songs whose type isn't known: [Ask.albums] first, then [Ask.songs]. */
    data class Ask(val albums: List<String>, val songs: List<String>)

    /**
     * The albums to ask for (each answers for all its songs), at most [maxAlbums], and the songs to ask for one by one
     * (those with no album, or on albums past the limit), at most [maxSongs]: the rest go as MP3.
     */
    fun plan(unknown: List<Song>, maxAlbums: Int = MAX_ALBUMS, maxSongs: Int = MAX_SONGS): Ask {
        val albums = unknown.mapNotNull { it.albumId?.takeIf { a -> a.isNotBlank() } }.distinct().take(maxAlbums)
        val asked = albums.toSet()
        val songs = unknown.filter { it.albumId == null || it.albumId !in asked }.map { it.id }.distinct().take(maxSongs)
        return Ask(albums, songs)
    }

    /** Asks the server for the file types of [songs] it doesn't know yet. Returns how many it learned. */
    /**
     * Songs whose type isn't known and hasn't been asked for yet: a song the server didn't answer for isn't asked again
     * each time the queue moves on (0.52.0-dev: 40 songs asked again on every send of a 497-song queue).
     */
    fun worthAsking(songs: List<Song>): List<Song> =
        songs.filter { suffixOf(it) == null && it.id !in askedSongs && (it.albumId == null || it.albumId !in askedAlbums) }.distinctBy { it.id }

    suspend fun learn(api: MusicSource, songs: List<Song>): Int {
        val unknown = worthAsking(songs)
        if (unknown.isEmpty()) return 0
        val ask = plan(unknown)
        askedAlbums += ask.albums
        askedSongs += ask.songs
        val t0 = System.currentTimeMillis()
        coroutineScope {
            ask.albums.map { id -> async { runCatching { api.album(id).songs }.getOrNull().orEmpty() } }.awaitAll().forEach(::record)
        }
        val still = ask.songs.filter { id -> known[id] == null }
        coroutineScope {
            still.map { id -> async { runCatching { api.song(id) }.getOrNull() } }.awaitAll().filterNotNull().let(::record)
        }
        val learned = unknown.count { suffixOf(it) != null }
        AppLog.i(
            "Cast",
            "File types for the TV: $learned of ${unknown.size} songs found (${ask.albums.size} albums, ${still.size} songs asked) " +
                "in ${System.currentTimeMillis() - t0} ms" + if (learned < unknown.size) "; the rest go as MP3" else "",
        )
        return learned
    }

    /** How a queue goes to the TV, for the log: as they are, converted to FLAC, as MP3, and of no known type. */
    data class Summary(val asIs: Int, val flac: Int, val mp3: Int, val unknownType: Int) {
        fun describe(quality: CastQuality): String =
            "${quality.label}: $asIs as they are, $flac as FLAC from the server, $mp3 as MP3" +
                if (unknownType > 0) " ($unknownType of unknown file type)" else ""
    }

    /** How songs of these [suffixes] go to the TV in [quality], streamed from addresses like [streamUrl]. */
    fun summary(suffixes: List<String?>, quality: CastQuality, streamUrl: String): Summary {
        var asIs = 0
        var flac = 0
        var mp3 = 0
        for (s in suffixes) {
            val source = CastOriginals.source(s, quality, streamUrl)
            when {
                source == null -> mp3++
                source.converted -> flac++
                else -> asIs++
            }
        }
        return Summary(asIs, flac, mp3, suffixes.count { it.isNullOrBlank() })
    }

    private const val MAX_KNOWN = 20_000
    const val MAX_ALBUMS = 12
    const val MAX_SONGS = 40
}
