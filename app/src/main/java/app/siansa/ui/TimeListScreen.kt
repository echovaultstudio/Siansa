package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Song
import app.siansa.mix.RankedTrack
import app.siansa.mix.TimeCardList
import app.siansa.mix.TimeMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One of Your listening's lists as a page of songs (issue #1343; the owner, 23 Nov: "clicking on them should open a
 * playlist page with songs for that day or year"): its name and when it's from, Play and Create memory, and its songs,
 * most played first. From the card kept on the phone, so it opens at once and holds exactly what the card showed.
 */
@Composable
fun TimeListScreen(nav: NavController, kind: String) {
    SiansaDarkTheme {
        val graph = LocalGraph.current
        val card by graph.timeCards.card.collectAsStateWithLifecycle(initialValue = null)
        // Opened from a place that hasn't read the card yet (after the app was closed): read it.
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { graph.timeCards.refresh() } } }
        val list = card?.list(kind)
        val tm by produceState<TimeMachine?>(null, list) {
            value = list?.let { timeListAsTimeMachine(it, graph.library.songs(it.songIds)) }
        }
        var creating by rememberSaveable { mutableStateOf(false) }
        val pictures = rememberLoadedPictures(list?.covers.orEmpty().take(4), HEADER_SMALL_PICTURE)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(Modifier.fillMaxSize().testTag("timeList"), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Large))) {
                item {
                    PictureHeader(nav, pictures, timeListColours(kind).second) {
                        HeaderKicker("YOUR LISTENING")
                        Text(list?.title.orEmpty(), Modifier.padding(horizontal = Space.Page).padding(top = Space.Tight), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        list?.let {
                            Text("${it.line} · ${songCount(it.songIds.size)}", Modifier.padding(horizontal = Space.Page).padding(top = Space.Tight),
                                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                tm?.let { machine ->
                    item(key = "songs") {
                        Column {
                            TimeMachineSongs(machine, showMissing = false, onShowMissing = {}, onCreate = { creating = true })
                            TimeMachineSongRows(machine, showMissing = false)
                        }
                    }
                }
            }
        }
        tm?.takeIf { creating }?.let { CreateMemory(it, nav) { creating = false } }
    }
}

/**
 * A kept list as the Time Machine's page draws one: [songs] (the ones still in the library, in the list's order), each
 * with its plays, under the list's own line ("23 November 2025") as its name.
 */
internal fun timeListAsTimeMachine(list: TimeCardList, songs: List<Song>): TimeMachine {
    val plays = list.songIds.zip(list.plays).toMap()
    return TimeMachine(list.line, songs.map { RankedTrack(it.title, it.artist, (plays[it.id] ?: 0).toLong(), it) }, "From your listening history")
}
