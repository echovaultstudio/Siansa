package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * What happens when the queue is about to run out (issue #337), set in Settings › Playback › Keep playing.
 */
enum class ContinuousMode(val label: String, val hint: String) {
    OFF("Off", "The music stops when the queue ends."),
    STEADY("Steady", "Adds songs like the last one in the queue, so the music carries on in the same style."),
    DJ("DJ", "Adds songs like the last one and orders them for smooth changes: close tempo and keys that go together. Needs AudioMuse.");

    companion object {
        fun parse(name: String?): ContinuousMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * Whether the queue should be topped up now: keep playing is on, only [left] songs or fewer come after the one
 * playing, and nothing else decides what plays next. A song radio tops itself up, repeat plays the queue again,
 * and the sleep timer's wind down (or its last song) is meant to end the music.
 */
fun continuousTopUpDue(
    mode: ContinuousMode,
    left: Int,
    hasMedia: Boolean,
    repeatOff: Boolean,
    radioOn: Boolean,
    windingDown: Boolean,
    sleepFinishing: Boolean,
): Boolean = mode != ContinuousMode.OFF && hasMedia && left <= ContinuousPlay.WHEN_LEFT && repeatOff &&
    !radioOn && !windingDown && !sleepFinishing

/** A song DJ mode may pick, with what it knows of its sound (null when AudioMuse hasn't analysed it). */
data class DjTrack(
    val id: String,
    val artist: String = "",
    /** How alike it is to the seed, 0..1 (1 the most alike). */
    val likeness: Double = 1.0,
    val tempo: Double? = null,
    val key: String? = null,
    val scale: String? = null,
)

/**
 * A key on the Camelot wheel DJs mix by: 1..12 with A for minor and B for major ("8A" is A minor, "8B" C major).
 * Neighbours on the wheel go together. Null for a key that isn't known.
 */
data class Camelot(val number: Int, val minor: Boolean)

private val PITCHES = mapOf(
    "C" to 0, "B#" to 0, "C#" to 1, "DB" to 1, "D" to 2, "D#" to 3, "EB" to 3, "E" to 4, "FB" to 4,
    "F" to 5, "E#" to 5, "F#" to 6, "GB" to 6, "G" to 7, "G#" to 8, "AB" to 8, "A" to 9, "A#" to 10, "BB" to 10,
    "B" to 11, "CB" to 11,
)

fun camelot(key: String?, scale: String?): Camelot? {
    val raw = key?.trim()?.replace("♯", "#")?.replace("♭", "b")?.takeIf { it.isNotEmpty() } ?: return null
    val letter = raw[0].uppercaseChar().takeIf { it in 'A'..'G' } ?: return null
    val accidental = raw.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
    val pitch = PITCHES["$letter${accidental?.uppercaseChar() ?: ""}"] ?: return null
    // Without a scale, "Am" or "A minor" in the key itself, as some taggers write it.
    val rest = raw.drop(if (accidental != null) 2 else 1).trim().lowercase()
    val minor = scale?.trim()?.lowercase()?.startsWith("min") ?: (rest == "m" || rest.startsWith("min"))
    // A minor key sits at the number of its relative major, three semitones up.
    val major = if (minor) (pitch + 3) % 12 else pitch
    return Camelot(((major * 7) % 12 + 7) % 12 + 1, minor)
}

/** How well two keys mix, 0..1: the same key best, then wheel neighbours; 0.5 when either key is unknown. */
fun keyMatch(a: Camelot?, b: Camelot?): Double {
    if (a == null || b == null) return 0.5
    val steps = abs(a.number - b.number).let { minOf(it, 12 - it) }
    return when {
        steps == 0 && a.minor == b.minor -> 1.0
        steps == 1 && a.minor == b.minor -> 0.9
        steps == 0 -> 0.85 // relative major or minor
        steps == 2 && a.minor == b.minor -> 0.5
        steps == 1 -> 0.4
        else -> 0.1
    }
}

/**
 * How close two tempos are, 0..1: 1 for the same, falling to 0 at [TEMPO_SPREAD] apart. Half and double time count
 * as the same tempo (a 70 BPM song mixes into a 140 BPM one). 0.5 when either is unknown.
 */
fun tempoMatch(a: Double?, b: Double?): Double {
    if (a == null || b == null || a <= 0 || b <= 0) return 0.5
    val off = listOf(b, b * 2, b / 2).minOf { abs(it / a - 1) }
    return (1 - off / TEMPO_SPREAD).coerceIn(0.0, 1.0)
}

private const val TEMPO_SPREAD = 0.08

/** How good a change from [from] to [to] is: how alike [to] is to the seed, then tempo and key; a new artist is better. */
fun transition(from: DjTrack, to: DjTrack): Double {
    val sameArtist = from.artist.isNotBlank() && from.artist.equals(to.artist, ignoreCase = true)
    return 0.45 * to.likeness +
        0.35 * tempoMatch(from.tempo, to.tempo) +
        0.20 * keyMatch(camelot(from.key, from.scale), camelot(to.key, to.scale)) -
        (if (sameArtist) 0.15 else 0.0)
}

/**
 * DJ mode's order (issue #337): from [seed], each next song is the best change from the one before it, so the
 * set moves smoothly instead of jumping in tempo or key. At most [count] of [pool].
 */
fun djOrder(seed: DjTrack, pool: List<DjTrack>, count: Int): List<DjTrack> {
    val left = pool.filter { it.id != seed.id }.distinctBy { it.id }.toMutableList()
    val picked = ArrayList<DjTrack>(count)
    var tail = seed
    while (picked.size < count && left.isNotEmpty()) {
        val next = left.maxBy { transition(tail, it) }
        picked += next
        left -= next
        tail = next
    }
    return picked
}

/** 1 for the most alike of [size] songs in AudioMuse's order down to near 0 for the last: its scores differ by route. */
fun likenessByRank(index: Int, size: Int): Double = if (size <= 1) 1.0 else 1.0 - index.toDouble() / size

/**
 * Keep playing (issue #337): when only a song or so is left in the queue, songs like the last one are added so the
 * music doesn't stop. Steady adds them in the order they're alike; DJ orders them for smooth changes with
 * AudioMuse's tempo and key. Songs in the queue or played lately aren't added again.
 */
class ContinuousPlay(private val graph: AppGraph) {
    private var job: Job? = null
    /** Songs this has added since the app started, so a long session doesn't loop back to them. */
    private val added = LinkedHashSet<String>()

    private data class Snapshot(val left: Int, val last: Song?, val hasMedia: Boolean, val repeatOff: Boolean)

    init {
        graph.scope.launch {
            combine(
                graph.player.state
                    .map {
                        // Shuffled, the last song in the list isn't the last to play: the songs follow the one playing.
                        val last = if (it.shuffle) it.current else it.queue.lastOrNull()
                        Snapshot(it.left, last, it.hasMedia, it.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF)
                    }
                    .distinctUntilChanged(),
                graph.settings.state.map { it.continuousPlay }.distinctUntilChanged(),
            ) { snapshot, mode -> snapshot to mode }
                .collect { (snapshot, mode) ->
                    val due = continuousTopUpDue(
                        mode = mode,
                        left = snapshot.left,
                        hasMedia = snapshot.hasMedia,
                        repeatOff = snapshot.repeatOff,
                        radioOn = graph.songRadio.seed.value != null,
                        windingDown = graph.sleepTimer.windingDown(),
                        sleepFinishing = graph.sleepTimer.state.value?.finishing == true,
                    )
                    val last = snapshot.last
                    if (!due || last == null || job?.isActive == true) return@collect
                    job = graph.scope.launch { topUp(mode, last) }
                }
        }
    }

    private suspend fun topUp(mode: ContinuousMode, last: Song) {
        val started = System.currentTimeMillis()
        try {
            val queue = graph.player.state.value.queue.map { it.id }
            val lately = runCatching { graph.history.recent(RECENT) }.getOrDefault(emptyList()).map { it.id }
            val skip = HashSet<String>().apply { addAll(queue); addAll(lately); addAll(added); add(last.id) }
            val hits = graph.sonic.similar(last.id, (POOL + skip.size).coerceAtMost(MAX_POOL))
                .filter { it.id !in skip }
                .distinctBy { it.id }
                .take(POOL)
            if (hits.isEmpty()) {
                AppLog.i("KeepPlaying", "Nothing new like \"${last.title}\" to add")
                return
            }
            val ids = when (mode) {
                ContinuousMode.DJ -> djIds(last, hits) ?: hits.take(TOP_UP).map { it.id }
                else -> hits.take(TOP_UP).map { it.id }
            }
            val byId = hits.associateBy { it.id }
            var songs = graph.generator.resolveHits(ids.mapNotNull { byId[it] })
            // Offline, or with "Only downloaded music" on, only songs on the phone can play.
            if (graph.connection.current.offline || graph.settings.current.downloadedOnly) {
                songs = songs.filter { graph.downloads.isDownloaded(it.id) }
            }
            if (songs.isEmpty()) return
            // Something else may have taken over while AudioMuse was answering: a new list, a radio, the wind down.
            val now = graph.player.state.value
            val stillLast = if (now.shuffle) now.queue.any { it.id == last.id } else now.queue.lastOrNull()?.id == last.id
            if (!stillLast || graph.songRadio.seed.value != null || graph.sleepTimer.windingDown()) {
                AppLog.d("KeepPlaying", "Queue changed while finding songs; not adding")
                return
            }
            added += songs.map { it.id }
            while (added.size > MAX_REMEMBERED) added.remove(added.first())
            AppLog.i(
                "KeepPlaying",
                "${mode.label}: added ${songs.size} songs like \"${last.title}\" in ${System.currentTimeMillis() - started}ms",
            )
            withContext(Dispatchers.Main) { graph.player.continueWith(songs) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("KeepPlaying", "Couldn't add songs like \"${last.title}\": ${e.message}")
        }
    }

    /**
     * [songs] in DJ order after [last] (issue #337): smooth changes in tempo and key, the most alike first. A song
     * radio in DJ mode uses this too. [songs] as they are when AudioMuse's analysis isn't available.
     */
    suspend fun djArrange(last: Song, songs: List<Song>): List<Song> {
        if (songs.size < 2) return songs
        val byId = songs.associateBy { it.id }
        val hits = songs.map { app.siansa.net.ClapHit(it.id, it.title, it.artist, it.album, 0.0) }
        val ids = djIds(last, hits, songs.size) ?: return songs
        return ids.mapNotNull { byId[it] }
    }

    /** DJ mode's order of [hits] after [last]; null when AudioMuse's analysis isn't available (Steady order then). */
    private suspend fun djIds(last: Song, hits: List<app.siansa.net.ClapHit>, count: Int = TOP_UP): List<String>? {
        if (!graph.settings.current.audioMuseReady) {
            AppLog.i("KeepPlaying", "DJ needs AudioMuse; adding in Steady order")
            return null
        }
        val features = runCatching { graph.audioMuse.features(listOf(last.id) + hits.map { it.id }) }
            .onFailure { AppLog.w("KeepPlaying", "DJ couldn't read tempo and key: ${it.message}") }
            .getOrNull()
            ?.associateBy { it.id }
            ?: return null
        val seedFeatures = features[last.id]
        val seed = DjTrack(last.id, last.artist, 1.0, seedFeatures?.tempo, seedFeatures?.key, seedFeatures?.scale)
        val pool = hits.mapIndexed { i, hit ->
            val f = features[hit.id]
            DjTrack(hit.id, hit.artist, likenessByRank(i, hits.size), f?.tempo, f?.key, f?.scale)
        }
        val order = djOrder(seed, pool, count)
        AppLog.d(
            "KeepPlaying",
            "DJ from ${seed.tempo?.let { "%.0f BPM".format(it) } ?: "unknown tempo"} ${seed.key.orEmpty()} ${seed.scale.orEmpty()}: " +
                order.joinToString { t -> "${t.tempo?.let { "%.0f".format(it) } ?: "?"} ${t.key.orEmpty()}" },
        )
        return order.map { it.id }
    }

    companion object {
        /** Topped up when this many songs or fewer come after the one playing. */
        const val WHEN_LEFT = 1
        const val TOP_UP = 20
        private const val POOL = 60
        private const val MAX_POOL = 500
        private const val RECENT = 200
        private const val MAX_REMEMBERED = 1000
    }
}
