package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A DJ mix of one album's own songs (issue #965): all of them, in the order that blends best (tempo, key and energy, as
 * a DJ mix orders), played with DJ blends. Nothing else is added: the mix ends with the album. While a song of it plays,
 * blends are made as in a DJ mix, and the play buttons (now playing's and the album page's) have their grooves. No
 * message pops up when it starts (the owner): the grooves say it.
 */
object AlbumDjMix {
    private val songs = MutableStateFlow<List<String>>(emptyList())

    /** The songs of the album DJ mix last started, in its order; empty when none was. */
    val playing: StateFlow<List<String>> = songs

    /** Whether [songId] plays as part of an album's DJ mix. */
    fun isOn(songId: String?): Boolean = songId != null && songId in songs.value

    /**
     * Orders [album]'s songs for the best blends and plays them. Needs AudioMuse's tempo and key; without them the album
     * plays in its own order.
     */
    suspend fun start(graph: AppGraph, title: String, album: List<Song>) {
        if (album.isEmpty()) return
        val ordered = arrange(graph, album)
        songs.value = ordered.map { it.id }
        graph.player.play(ordered)
        val sorted = ordered.map { it.id } != album.map { it.id }
        AppLog.i("AlbumMix", "DJ mix of \"$title\": ${ordered.size} songs${if (sorted) " in the best order" else " in the album's order"}",
            event = "mix.album_dj", fields = mapOf("songs" to ordered.size, "reordered" to sorted))
    }

    private suspend fun arrange(graph: AppGraph, album: List<Song>): List<Song> {
        if (album.size < 3 || !graph.settings.current.audioMuseReady) return album
        val features = runCatching { graph.audioMuse.features(album.map { it.id }) }
            .onFailure { AppLog.w("AlbumMix", "Couldn't read tempo and key: ${it.message}; the album's own order") }
            .getOrNull()?.associateBy { it.id } ?: return album
        val energy = energyByRank(album.associate { it.id to features[it.id]?.energy })
        val tracks = album.map { s ->
            val f = features[s.id]
            DjTrack(s.id, s.artist, 1.0, f?.tempo, f?.key, f?.scale, energy[s.id])
        }
        val byId = album.associateBy { it.id }
        return bestAlbumOrder(tracks).mapNotNull { byId[it.id] }
    }
}

/**
 * Whether the album page of [songs] has its DJ mix [mix] playing (issue #965): the queue starts with the mix, in the
 * mix's order, and the mix is that album's songs. Then its Play button keeps the record grooves. Playing the album
 * any other way, or anything else, ends it.
 */
fun albumMixPlaying(queue: List<Song>, songs: List<Song>, mix: List<String>): Boolean =
    mix.isNotEmpty() && queue.size >= mix.size && queue.take(mix.size).map { it.id } == mix &&
        songs.map { it.id }.toSet() == mix.toSet()

/**
 * The order of an album's [tracks] that blends best (issue #965): from each song in turn, a DJ order through the rest
 * (each next song the best change from the one before), and of those the one whose changes add up best. All songs,
 * each once. Songs without tempo or key count as neither good nor bad, so an album AudioMuse hasn't measured keeps
 * its first song first.
 */
fun bestAlbumOrder(tracks: List<DjTrack>): List<DjTrack> {
    if (tracks.size < 3) return tracks
    var best: List<DjTrack> = tracks
    var bestScore = flowScore(tracks)
    for (start in tracks) {
        val order = listOf(start) + djOrder(start, tracks, tracks.size - 1)
        val score = flowScore(order)
        if (score > bestScore + 1e-9) {
            best = order
            bestScore = score
        }
    }
    return best
}

/** How well an order blends: its changes' tempo and key, added up (likeness and artists don't count within one album). */
internal fun flowScore(order: List<DjTrack>): Double =
    order.zipWithNext().sumOf { (a, b) -> 0.6 * tempoMatch(a.tempo, b.tempo) + 0.4 * keyMatch(camelot(a.key, a.scale), camelot(b.key, b.scale)) }
