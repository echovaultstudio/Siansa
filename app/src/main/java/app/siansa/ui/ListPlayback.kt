package app.siansa.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.Song

/**
 * Whether the player has [songs] loaded (issue #644): the queue starts with exactly those songs, in any order (a
 * shuffled start, or a list played shuffled), with songs added after them by keep playing or a radio allowed. Then a
 * page's Play button for those songs shows ⏸ while they play, and pauses or resumes them instead of starting over.
 */
internal fun listLoaded(queue: List<Song>, songs: List<Song>): Boolean {
    if (songs.isEmpty() || queue.size < songs.size) return false
    val wanted = songs.map { it.id }
    return queue.take(songs.size).map { it.id }.sorted() == wanted.sorted()
}

/** What a Play button for a list shows and does: [playing] shows ⏸; [press] pauses or resumes it when loaded, else [start]s it. */
class ListPlayback(val loaded: Boolean, val playing: Boolean, private val toggle: () -> Unit) {
    fun press(start: () -> Unit) = if (loaded) toggle() else start()

    companion object {
        /** Nothing of the list is playing: a tap starts it. */
        val IDLE = ListPlayback(loaded = false, playing = false) {}
    }
}

/** [ListPlayback] for [songs], following the player as it plays and pauses. */
@Composable
fun rememberListPlayback(songs: List<Song>): ListPlayback {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    val loaded = state.hasMedia && listLoaded(state.queue, songs)
    return ListPlayback(loaded, loaded && state.isPlaying) { graph.player.togglePlay() }
}

/** The inside of a "Play" button that follows [playing]: ▶ Play, or ⏸ Pause. */
@Composable
fun PlayPauseLabel(playing: Boolean) {
    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
    Spacer(Modifier.width(Space.Tight))
    Text(if (playing) "Pause" else "Play")
}
