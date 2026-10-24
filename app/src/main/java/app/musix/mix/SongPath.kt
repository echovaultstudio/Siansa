package app.musix.mix

import app.musix.AppGraph
import app.musix.net.SourceKind
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.net.PathMood

/**
 * How many songs of a path to queue after the song playing now (#212): all but the first, when the path starts
 * with the song playing (it keeps playing, nothing starts again). Null when it doesn't, e.g. a path that ends
 * with the song playing: that one is left for the listener to play.
 */
fun songPathUpNext(pathIds: List<String>, playingId: String?): Int? =
    if (playingId != null && pathIds.size > 1 && pathIds.first() == playingId) pathIds.size - 1 else null

/** The lengths offered for a song path. */
val songPathLengths = listOf(10, 20, 30)

/**
 * The song ids of a path in play order: [fromId] first, [toId] last and AudioMuse's steps between them,
 * each once. When AudioMuse returns more steps than fit in [length], an even spread of them is kept.
 */
fun songPathIds(fromId: String, toId: String, steps: List<String>, length: Int): List<String> {
    val middle = steps.distinct().filter { it != fromId && it != toId }
    val room = (length - 2).coerceAtLeast(0)
    val kept = if (middle.size <= room) middle else List(room) { i -> middle[i * middle.size / room] }
    return listOf(fromId) + kept + toId
}

/** A song path's end in the navigation route: a song id, or "mood:<id>" for one of AudioMuse's moods. */
fun pathEnd(id: String): Pair<String?, PathMood?> {
    if (!id.startsWith(MOOD_END)) return id to null
    val mood = PathMood.entries.firstOrNull { it.id == id.removePrefix(MOOD_END) }
    return null to mood
}

const val MOOD_END = "mood:"

/**
 * Song paths: a queue that moves smoothly from the sound of one song to the sound of another (or to a mood),
 * played straight away or saved as a Navidrome playlist. AudioMuse directly when it's set up in Musix: the path
 * is then exactly the length picked, and can end on a mood or follow the lyrics (issue #208). Without it,
 * Navidrome's own `findSonicPath` (the AudioMuse-AI Navidrome plugin) between two songs.
 */
class SongPath(private val graph: AppGraph) {
    suspend fun build(fromId: String, toId: String, length: Int, lyrics: Boolean = false): List<Song> {
        if (fromId == toId) throw MixException("Pick two different songs")
        val (fromSong, fromMood) = pathEnd(fromId)
        val (toSong, toMood) = pathEnd(toId)
        val started = System.currentTimeMillis()
        val ids: List<String> = when {
            graph.settings.current.audioMuseReady -> {
                val steps = graph.audioMuse.findPath(fromSong, toSong, length, fromMood, toMood, lyrics).map { it.id }
                if (steps.isEmpty()) throw MixException("AudioMuse found no path between these")
                // A mood end is whichever song AudioMuse picked for it: the path is used as it comes.
                if (fromMood != null || toMood != null) steps.distinct() else songPathIds(fromId, toId, steps, length)
            }
            fromMood != null || toMood != null || lyrics ->
                throw MixException("Set up AudioMuse in Settings to end a path on a mood or follow the lyrics")
            graph.source.hasSonicMatch() -> {
                val steps = graph.source.sonicPath(fromId, toId, length).map { it.id }
                if (steps.isEmpty()) throw MixException("Navidrome found no path between these songs")
                songPathIds(fromId, toId, steps, length)
            }
            // Emby has no sonic plugin (issue #113): its paths come from AudioMuse alone.
            graph.settings.current.sourceKind != SourceKind.OPENSUBSONIC -> throw MixException("Set up AudioMuse in Settings for song paths")
            else -> throw MixException("Set up AudioMuse in Settings, or the AudioMuse-AI plugin in Navidrome, for song paths")
        }
        val songs = graph.generator.resolveSongs(ids)
        AppLog.i("SongPath", "$fromId → $toId (lyrics=$lyrics): ${ids.size} steps, ${songs.size} songs in ${System.currentTimeMillis() - started}ms")
        if (songs.size < 3) throw MixException("No songs found between these two")
        return songs
    }

    /** A playlist name like "Song A → Song B". */
    fun name(from: Song, to: Song): String = "${from.title} → ${to.title}"

    suspend fun save(from: Song, to: Song, songs: List<Song>): String {
        val name = name(from, to)
        graph.source.createPlaylist(name, songs.map { it.id })
        AppLog.i("SongPath", "Saved \"$name\" with ${songs.size} songs as a playlist")
        return name
    }
}
