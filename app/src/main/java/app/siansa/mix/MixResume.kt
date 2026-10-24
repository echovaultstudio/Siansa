package app.siansa.mix

import android.content.Context
import app.siansa.AppGraph
import app.siansa.data.Mix
import app.siansa.data.Song
import app.siansa.data.Users
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a mix was left: the song, how far into it, and how long it is (issue #269). */
data class ResumePoint(val songId: String, val positionMs: Long, val durationMs: Long, val at: Long)

/**
 * Where to carry a mix on from [point]: the song's position in [songs] and where in it to start, or null to
 * start from the top (nothing saved, the song isn't in the mix any more, or the last song had ended).
 *
 * The exact spot when it's known: seeking costs nothing. A song that had (almost) ended carries on with the
 * next. Without a known length: the same song from its start when under half was heard, otherwise the next.
 */
fun resumeStart(songs: List<Song>, point: ResumePoint?): Pair<Int, Long>? {
    point ?: return null
    val index = songs.indexOfFirst { it.id == point.songId }.takeIf { it >= 0 } ?: return null
    val next = (index + 1).takeIf { it < songs.size }?.let { it to 0L }
    val duration = point.durationMs.takeIf { it > 0 } ?: (songs[index].durationSec * 1000L).takeIf { it > 0 }
    if (duration == null) return if (point.positionMs < 30_000) index to 0L else next
    val left = duration - point.positionMs
    return when {
        left < ENDED_MS || point.positionMs >= duration * 98 / 100 -> next
        point.positionMs < START_MS -> index to 0L
        else -> index to point.positionMs
    }
}

/** Under this much left, a song counts as heard, so the mix carries on with the next one. */
private const val ENDED_MS = 10_000L
/** Under this far in, the song starts from its beginning. */
private const val START_MS = 5_000L

/**
 * Today's mixes carry on where you left them (issue #269). While a mix's songs are the queue, however it was
 * started, where it is gets noted every few seconds; playing the mix again from Home starts there.
 */
class MixResume(private val graph: AppGraph, context: Context) {
    private val prefs = context.getSharedPreferences(Users.scoped("mix_resume"), Context.MODE_PRIVATE)
    private val _points = MutableStateFlow(load())
    /** Where each mix was left, by mix id. */
    val points: StateFlow<Map<String, ResumePoint>> = _points.asStateFlow()

    /** Plays [mix] from where it was left, or from the top. */
    fun play(mix: Mix) {
        val start = resumeStart(mix.songs, _points.value[mix.id])
        if (start != null) {
            AppLog.i("Mix", "Carrying on \"${mix.title}\" at song ${start.first + 1} of ${mix.songs.size}, ${start.second / 1000}s in")
        }
        graph.player.play(mix.songs, start?.first ?: 0, startMs = start?.second ?: 0L)
    }

    /** Starts noting where a playing mix is; once, when the app starts. */
    fun watch() {
        graph.scope.launch(Dispatchers.Main) {
            while (true) {
                delay(POLL_MS)
                runCatching { note() }.onFailure { AppLog.d("Mix", "Couldn't note where the mix is: ${it.message}") }
            }
        }
    }

    private suspend fun note() {
        val state = graph.player.state.value
        val current = state.current ?: return
        if (state.queue.isEmpty()) return
        val queueIds = state.queue.mapTo(HashSet()) { it.id }
        // The mix whose songs are the queue (in any order: shuffled counts too).
        val mix = graph.mixes.state.value.orEmpty().firstOrNull { m ->
            m.songs.size == state.queue.size && m.songs.all { it.id in queueIds }
        } ?: return
        val progress = graph.player.progress()
        if (progress.mediaId != current.id) return
        val point = ResumePoint(current.id, progress.positionMs, progress.durationMs, System.currentTimeMillis())
        val before = _points.value[mix.id]
        if (before != null && before.songId == point.songId && kotlin.math.abs(before.positionMs - point.positionMs) < 2_000) return
        val kept = (_points.value + (mix.id to point)).entries.sortedByDescending { it.value.at }.take(KEEP).associate { it.key to it.value }
        _points.value = kept
        withContext(Dispatchers.IO) { save(kept) }
    }

    private fun load(): Map<String, ResumePoint> = prefs.all.mapNotNull { (id, value) ->
        val parts = (value as? String)?.split('|') ?: return@mapNotNull null
        if (parts.size != 4) return@mapNotNull null
        val point = ResumePoint(parts[0], parts[1].toLongOrNull() ?: 0, parts[2].toLongOrNull() ?: 0, parts[3].toLongOrNull() ?: 0)
        id to point
    }.toMap()

    private fun save(points: Map<String, ResumePoint>) {
        prefs.edit().clear().apply {
            points.forEach { (id, p) -> putString(id, "${p.songId}|${p.positionMs}|${p.durationMs}|${p.at}") }
        }.apply()
    }

    private companion object {
        const val POLL_MS = 5_000L
        /** Mixes remembered (today's and a few days back). */
        const val KEEP = 30
    }
}
