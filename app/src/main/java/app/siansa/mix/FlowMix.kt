package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.SavedVibe
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.playback.PlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The mix Flow is playing (the owner, 15 Nov): Play on Flow makes a vibe and plays it without opening its page, and
 * while it plays the Flow page stays in charge of it. Moving the pad's dot, a pick or Artists changes what comes
 * next; the song playing carries on. The mix keeps the name it started with. It ends when playback is stopped or
 * something else is played (pausing keeps it). Now playing and the queue name it, and + saves it as a playlist.
 *
 * What's set on the Flow page lives here, not on the page, so it's still there after looking at another tab.
 */
class FlowMix(private val graph: AppGraph) {
    /** The mix playing: the vibe it's kept as, its name, and the player's list it started ([PlayerConnection.queueGeneration]). */
    data class Live(val vibeId: String, val name: String, val generation: Long)

    /** The words typed on Flow. */
    val text = MutableStateFlow("")

    /** The mood, picks and artists set on Flow. */
    val tuning = MutableStateFlow(VibeTuning())

    /** The mix Flow started last; [live] says whether it's still the one playing. */
    val started = MutableStateFlow<Live?>(null)

    /** A mix is being made. */
    val making = MutableStateFlow(false)

    /** What comes next is being worked out again. */
    val steering = MutableStateFlow(false)

    private var steer: Job? = null

    /** The Flow mix that's playing now, or null: [player] is the player's state as the page sees it. */
    fun live(player: PlayerState): Live? = started.value?.takeIf { isLive(it.generation, graph.player.queueGeneration, player.hasMedia) }

    /**
     * Makes a vibe from the words and what's set, keeps it under Your vibes and plays it. Its page isn't opened: the
     * Flow page goes on steering it. [notify] says what went wrong.
     */
    fun play(notify: (String) -> Unit) {
        if (making.value) return
        making.value = true
        val words = text.value
        val with = tuning.value
        graph.scope.launch {
            try {
                val plan = graph.generator.vibePlan(words, with)
                // A typed vibe starts from the AI's own sounds; the mood and the sounds and genres picked go over them.
                val start = if (words.isBlank()) with else FlowMood.over(VibeTuning.start(plan.steering, emptyList()), with)
                val songs = graph.generator.vibeSongs(plan.query, plan.steering, start)
                val saved = graph.savedVibes.save(
                    SavedVibe(
                        id = "", text = words.trim(), title = plan.title, description = plan.description, songs = songs,
                        madeAt = System.currentTimeMillis(), query = plan.query, steered = plan.steering, tuning = start, ai = plan.ai,
                    ),
                )
                withContext(Dispatchers.Main) {
                    val before = graph.player.queueGeneration
                    graph.player.play(songs)
                    // Offline with none of them downloaded, nothing started: then nothing is Flow's.
                    if (graph.player.queueGeneration != before) {
                        started.value = Live(saved.id, saved.shownTitle, graph.player.queueGeneration)
                        // The AI's own sounds show as picks now, to take off or turn down like any other.
                        tuning.value = start
                        AppLog.i("Vibe", "Flow plays \"${saved.shownTitle}\" (${songs.size} songs); the Flow page steers it")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Vibe", "Couldn't make the vibe: ${e.message}")
                withContext(Dispatchers.Main) { notify(e.message ?: "Couldn't make the vibe") }
            } finally {
                making.value = false
            }
        }
    }

    /**
     * Sets what's on the Flow page to [next]; with [settled] (a finger lifted, a pick or Artists changed) and a mix
     * playing, what comes next is worked out again a moment later, so a few changes in a row ask once.
     */
    fun set(next: VibeTuning, settled: Boolean) {
        tuning.value = next
        if (settled) resteer()
    }

    private fun resteer() {
        val mix = started.value ?: return
        if (!isLive(mix.generation, graph.player.queueGeneration, graph.player.state.value.hasMedia)) return
        steer?.cancel()
        steer = graph.scope.launch {
            delay(STEER_AFTER_MS)
            val with = tuning.value
            val vibe = graph.savedVibes.get(mix.vibeId) ?: return@launch
            if (with.set == vibe.tuning.set) return@launch
            steering.value = true
            try {
                val songs = graph.generator.vibeSongs(vibe.query, vibe.steered, with)
                withContext(Dispatchers.Main) {
                    // Something else started meanwhile, or playback was stopped: the songs are nobody's.
                    if (graph.player.queueGeneration != mix.generation) return@withContext
                    val state = graph.player.state.value
                    val next = upcoming(songs, state.queue.take(state.index + 1))
                    graph.player.replaceUpcoming(next)
                    AppLog.i("Vibe", "Flow steered \"${mix.name}\": ${with.summary().ifEmpty { "nothing set" }} · ${next.size} songs to come")
                    graph.scope.launch {
                        graph.savedVibes.save(vibe.copy(songs = state.queue.take(state.index + 1) + next, tuning = with, madeAt = System.currentTimeMillis()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Vibe", "Couldn't steer \"${mix.name}\": ${e.message}")
                graph.player.notice(e.message ?: "Couldn't change the mix")
            } finally {
                steering.value = false
            }
        }
    }

    /** Everything on the Flow page back as it was; a mix playing is steered back with it. */
    fun reset() {
        text.value = ""
        set(tuning.value.cleared().copy(sounds = emptyList()), settled = true)
    }

    companion object {
        /** How long after the last change the mix is worked out again. */
        const val STEER_AFTER_MS = 500L

        /**
         * Whether the mix Flow started as the player's list number [started] is still the one playing: no other list
         * was started and playback wasn't stopped since ([now] is the player's list number), and there's something in
         * the player ([hasMedia]). Pausing changes neither.
         */
        fun isLive(started: Long, now: Long, hasMedia: Boolean): Boolean = hasMedia && started == now

        /** What comes next of a mix worked out again ([songs]): without what has played and what's playing ([heard]). */
        fun upcoming(songs: List<Song>, heard: List<Song>): List<Song> {
            val ids = heard.mapTo(HashSet()) { it.id }
            return songs.filter { ids.add(it.id) }
        }
    }
}
