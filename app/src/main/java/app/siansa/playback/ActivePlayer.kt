package app.siansa.playback

import app.siansa.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Which mini player shows above the tabs. */
enum class MiniFor { MUSIC, BOOK, NONE }

/** What the two players are doing, as far as the mini player cares. */
data class Players(val musicLoaded: Boolean, val musicPlaying: Boolean, val bookLoaded: Boolean, val bookPlaying: Boolean)

/**
 * Who owns the mini player after [before] became [now] (beta #45, again): the player last *started*.
 * Starting one (in the app, from the notification or headphones) hands it over; pausing never does; stopping
 * the owner leaves no mini player at all, so the other one never comes back from behind. Only a queue that
 * appears on its own, as when the app opens with yesterday's songs, shows when nothing owns the bar.
 */
internal fun activeAfter(active: MiniFor, before: Players, now: Players): MiniFor = when {
    now.bookPlaying && !before.bookPlaying -> MiniFor.BOOK
    now.musicPlaying && !before.musicPlaying -> MiniFor.MUSIC
    active == MiniFor.BOOK && !now.bookLoaded -> MiniFor.NONE
    active == MiniFor.MUSIC && !now.musicLoaded -> MiniFor.NONE
    active == MiniFor.NONE && now.musicLoaded && !before.musicLoaded && !now.bookLoaded -> MiniFor.MUSIC
    else -> active
}

/** The mini player to draw: the owner's, while it has something loaded. */
fun miniShown(active: MiniFor, musicLoaded: Boolean, bookLoaded: Boolean): MiniFor = when (active) {
    MiniFor.MUSIC -> if (musicLoaded) MiniFor.MUSIC else MiniFor.NONE
    MiniFor.BOOK -> if (bookLoaded) MiniFor.BOOK else MiniFor.NONE
    MiniFor.NONE -> MiniFor.NONE
}

/**
 * Keeps [state], the owner of the mini player, for the whole app rather than one screen: the bottom bar is
 * drawn again when Now playing opens and closes, and a choice kept there was lost (why the book's mini player
 * came back over a paused song). Every change is logged as "Mini player: …".
 */
class ActivePlayer(scope: CoroutineScope, music: StateFlow<PlayerState>, book: StateFlow<BookPlayerState>) {
    private val _state = MutableStateFlow(MiniFor.NONE)
    val state: StateFlow<MiniFor> = _state.asStateFlow()
    private var previous = MiniFor.NONE
    private var last = Players(false, false, false, false)
    private val lock = Any()

    init {
        scope.launch {
            combine(music, book) { m, b -> Players(m.hasMedia, m.isPlaying, b.book != null, b.playing) }
                .distinctUntilChanged()
                .collect { now ->
                    synchronized(lock) {
                        set(activeAfter(_state.value, last, now), "players: $now")
                        last = now
                    }
                }
        }
    }

    /** [which] is starting now: it owns the mini player straight away, before its sound begins. */
    fun choose(which: MiniFor) = synchronized(lock) { set(which, "started") }

    /** [which] couldn't start after all: the mini player goes back to its owner before. */
    fun failed(which: MiniFor) = synchronized(lock) { if (_state.value == which) set(previous, "couldn't start") }

    private fun set(next: MiniFor, why: String) {
        val now = _state.value
        if (next == now) return
        previous = now
        _state.value = next
        AppLog.i("Player", "Mini player: ${now.name.lowercase()} → ${next.name.lowercase()} ($why)")
    }
}
