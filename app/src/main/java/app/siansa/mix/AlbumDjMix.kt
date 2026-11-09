package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

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
    suspend fun start(graph: AppGraph, title: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        // A long playlist or a liked list mixes a random [MAX_MIX_SONGS] of its songs: hours of music, ordered quickly.
        val album = mixPortion(songs, Random.Default)
        if (album.size < songs.size) AppLog.i("AlbumMix", "\"$title\" has ${songs.size} songs: the mix takes ${album.size} of them")
        val ordered = withContext(Dispatchers.Default) { arrange(graph, album) }
        songs.value = ordered.map { it.id }
        // The player takes commands on the main thread only: started from the background it refused, and nothing played
        // (the owner's log, 7 Nov).
        withContext(Dispatchers.Main) { graph.player.play(ordered) }
        val sorted = ordered.map { it.id } != album.map { it.id }
        AppLog.d("AlbumMix", "Starts with \"${ordered.first().title}\"")
        val how = when {
            !lastArrangedByFlow -> " shuffled (no tempo and key to order them by)"
            sorted -> " in the best order"
            else -> " in the album's order"
        }
        AppLog.i("AlbumMix", "DJ mix of \"$title\": ${ordered.size} songs$how",
            event = "mix.album_dj", fields = mapOf("songs" to ordered.size, "reordered" to sorted))
    }

    /** Whether the last mix was ordered by how its songs blend, or only shuffled: for the log. */
    private var lastArrangedByFlow = true

    private suspend fun arrange(graph: AppGraph, album: List<Song>): List<Song> {
        // Never the same start twice running for one album (the owner): last time's first song, if it was this album.
        val lastFirst = songs.value.firstOrNull()?.takeIf { first -> album.any { it.id == first } }
        lastArrangedByFlow = false
        if (album.size < 3 || !graph.settings.current.audioMuseReady) return album.shuffled()
        val features = runCatching { graph.audioMuse.features(album.map { it.id }) }
            .onFailure { AppLog.w("AlbumMix", "Couldn't read tempo and key: ${it.message}; shuffled instead") }
            .getOrNull()?.associateBy { it.id } ?: return album.shuffled()
        lastArrangedByFlow = true
        // Siansa Dev's experiment: the tempo of each song's beat grid (Siansa Beats', through Siansa Beats (dev),
        // counted as a DJ counts: drum and bass at 174 where AudioMuse often reads 87) orders the mix, AudioMuse's
        // for the songs without one.
        val gridTempo = runCatching { graph.profiles.fetch(album.map { it.id }) }.getOrNull().orEmpty()
            .mapValues { (_, p) -> (p.outro ?: p.intro)?.bpm }.filterValues { it != null }
        if (gridTempo.isNotEmpty()) {
            AppLog.i("AlbumMix", "Tempo from the beat grids for ${gridTempo.size} of ${album.size} songs")
        }
        val energy = energyByRank(album.associate { it.id to features[it.id]?.energy })
        val tracks = album.map { s ->
            val f = features[s.id]
            DjTrack(s.id, s.artist, 1.0, gridTempo[s.id] ?: f?.tempo, f?.key, f?.scale, energy[s.id])
        }
        val byId = album.associateBy { it.id }
        return pickAlbumOrder(tracks, Random.Default, lastFirst).mapNotNull { byId[it.id] }
    }
}

/**
 * Whether the album page of [songs] has its DJ mix [mix] playing (issue #965): the queue starts with the mix, in the
 * mix's order, and the mix is that album's songs. Then its Play button keeps the record grooves. Playing the album
 * any other way, or anything else, ends it.
 */
fun albumMixPlaying(queue: List<Song>, songs: List<Song>, mix: List<String>): Boolean =
    mix.isNotEmpty() && queue.size >= mix.size && queue.take(mix.size).map { it.id } == mix &&
        songs.map { it.id }.toSet().let { page -> page.containsAll(mix) && (page.size == mix.size || mix.size == MAX_MIX_SONGS) }

/**
 * Every DJ order of an album's [tracks] (issue #965): from each song in turn, through the rest, each next song the best
 * change from the one before; best blending first ([flowScore]). All songs, each once.
 */
internal fun albumOrders(tracks: List<DjTrack>): List<List<DjTrack>> =
    startsOf(tracks).map { start -> listOf(start) + djOrder(start, tracks, tracks.size - 1) }
        .sortedByDescending { flowScore(it) }

/**
 * The songs an order may start from: every one on an album; on a long playlist [MAX_STARTS] spread evenly through it,
 * as each start costs a pass over all the songs for each song.
 */
internal fun startsOf(tracks: List<DjTrack>): List<DjTrack> =
    if (tracks.size <= MAX_STARTS) tracks
    else List(MAX_STARTS) { i -> tracks[i * tracks.size / MAX_STARTS] }

/** The songs a DJ mix takes from [songs]: all of them, or a random [MAX_MIX_SONGS] from a longer list. */
fun mixPortion(songs: List<Song>, random: Random): List<Song> =
    if (songs.size <= MAX_MIX_SONGS) songs else songs.shuffled(random).take(MAX_MIX_SONGS)

/** At most this many starts are tried for a long playlist's order. */
internal const val MAX_STARTS = 24

/** A DJ mix takes at most this many songs (a day of music), so ordering stays quick on a phone. */
const val MAX_MIX_SONGS = 300

/**
 * The order an album's DJ mix plays (issue #965): one of the orders that blend best, picked at random, so the mix
 * doesn't always start from the same song (the owner). The pick is among the orders whose changes are, on average,
 * nearly as good as the best ([ORDER_SLACK] a change) and always among at least [ORDER_CHOICES] when the album has
 * them; never the one starting with [avoidFirst] (last time's first song) when there's another.
 */
fun pickAlbumOrder(tracks: List<DjTrack>, random: Random, avoidFirst: String? = null): List<DjTrack> {
    if (tracks.size < 3) return tracks.shuffled(random)
    val orders = albumOrders(tracks)
    val best = flowScore(orders.first())
    val near = orders.takeWhile { flowScore(it) >= best - ORDER_SLACK * (tracks.size - 1) - 1e-9 }
    val pool = (if (near.size >= ORDER_CHOICES) near else orders.take(ORDER_CHOICES)).take(ORDER_MAX_CHOICES)
    val fresh = pool.filter { it.first().id != avoidFirst }.ifEmpty { pool }
    return fresh[random.nextInt(fresh.size)]
}

/**
 * The order of [tracks] that blends best, the same each time (the mix itself picks with [pickAlbumOrder]). Songs
 * without tempo or key count as neither good nor bad, so an album AudioMuse hasn't measured keeps its order here.
 */
fun bestAlbumOrder(tracks: List<DjTrack>): List<DjTrack> {
    if (tracks.size < 3) return tracks
    val best = albumOrders(tracks).first()
    return if (flowScore(best) > flowScore(tracks) + 1e-9) best else tracks
}

/** How much worse, a change on average, an order may blend than the best and still be picked. */
private const val ORDER_SLACK = 0.08

/** At least this many orders to pick from (when the album has as many songs), however much better the best is. */
private const val ORDER_CHOICES = 3

/** At most this many: further down, the blends get worse. */
private const val ORDER_MAX_CHOICES = 5

/** How well an order blends: its changes' tempo and key, added up (likeness and artists don't count within one album). */
internal fun flowScore(order: List<DjTrack>): Double =
    order.zipWithNext().sumOf { (a, b) -> 0.6 * tempoMatch(a.tempo, b.tempo) + 0.4 * keyMatch(camelot(a.key, a.scale), camelot(b.key, b.scale)) }
