package app.musix.mix

import app.musix.AppGraph
import app.musix.data.Song
import app.musix.log.AppLog

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

/**
 * Song paths: a queue that moves smoothly from the sound of one song to the sound of another, using
 * AudioMuse's song path. Played straight away or saved as a Navidrome playlist.
 */
class SongPath(private val graph: AppGraph) {
    suspend fun build(fromId: String, toId: String, length: Int): List<Song> {
        if (fromId == toId) throw MixException("Pick two different songs")
        if (!graph.settings.current.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        val started = System.currentTimeMillis()
        val steps = graph.audioMuse.findPath(fromId, toId, length)
        if (steps.isEmpty()) throw MixException("AudioMuse found no path between these songs")
        val ids = songPathIds(fromId, toId, steps.map { it.id }, length)
        val songs = graph.generator.resolveSongs(ids)
        AppLog.i("SongPath", "$fromId → $toId: ${steps.size} steps from AudioMuse, ${songs.size} songs in ${System.currentTimeMillis() - started}ms")
        if (songs.size < 3) throw MixException("AudioMuse found no songs between these two")
        return songs
    }

    /** A playlist name like "Song A → Song B". */
    fun name(from: Song, to: Song): String = "${from.title} → ${to.title}"

    suspend fun save(from: Song, to: Song, songs: List<Song>): String {
        val name = name(from, to)
        graph.subsonic.createPlaylist(name, songs.map { it.id })
        AppLog.i("SongPath", "Saved \"$name\" with ${songs.size} songs as a playlist")
        return name
    }
}
