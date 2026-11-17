package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Mix
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.BeatsMixes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Home's mixes from Siansa Beats (0.53, the owner, 17 Nov), in place of your own carousels while they're switched on
 * under Siansa Beats on Connections: **For now** (this part of the day, as you listen in it, and whether it's dark),
 * **Made for you** (the kinds of music you play most) and **More of you** (On repeat, Rediscover, New for you), each a
 * crate of records to flick through ([RecordCrate]).
 */
@Composable
fun BeatsMixesHome(nav: NavController) {
    val graph = LocalGraph.current
    val shelves by graph.beatsMixes.state.collectAsStateWithLifecycle()
    // Asked when Home shows, again at the next part of the day (Siansa Beats has made them ahead) and now and then.
    LaunchedEffect(Unit) {
        while (true) {
            graph.beatsMixes.refresh()
            delay(60_000)
        }
    }
    val made = shelves ?: return
    // Each mix's songs, from the phone's copy of the library (the ids are the music server's own).
    val songs by produceState<Map<String, List<Song>>>(emptyMap(), made) {
        value = made.shelves.flatMap { it.mixes }.associate { it.id to graph.library.songs(it.songIds) }
    }
    Column(Modifier.fillMaxWidth().testTag("beatsMixes")) {
        made.shelves.forEachIndexed { i, shelf ->
            val records = shelf.mixes.mapNotNull { mix -> crateRecord(mix, songs[mix.id].orEmpty(), nav, graph) }
            if (records.isEmpty()) return@forEachIndexed
            SectionTitle(BeatsMixes.title(shelf), top = if (i == 0) LocalSectionGap.current else Space.Section)
            if (shelf.key == "now") {
                // What it's made from and what it's like outside, in a line, as Siansa Beats' page says it.
                val line = listOfNotNull(made.daylight?.said?.takeIf { it.isNotBlank() }, made.based.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        modifier = Modifier.padding(start = Space.Page, end = Space.Page, bottom = Space.Medium).testTag("beatsMixesNote"))
                }
            }
            RecordCrate(records)
        }
    }
}

/** A mix as a record: opens as a mix page (kept for two weeks as the app's own mixes are), plays its songs. */
private fun crateRecord(mix: BeatsMixes.BeatsMix, songs: List<Song>, nav: NavController, graph: app.siansa.AppGraph): CrateRecord? {
    if (songs.isEmpty()) return null
    val today = LocalDate.now().toString()
    val id = "beats-$today-${mix.id}"
    val caption = listOf(mix.why.ifBlank { mix.genres.take(2).joinToString(", ") }, "${songs.size} songs").filter { it.isNotBlank() }.joinToString(" · ")
    fun saved() = Mix(id, "beats-${mix.id}", today, mix.name, mix.why.ifBlank { "Made by Siansa Beats from your listening" }, "", songs,
        System.currentTimeMillis(), aiNamed = mix.aiNamed)
    return CrateRecord(
        key = mix.id + mix.name,
        name = mix.name,
        caption = caption,
        covers = songs.map { it.coverArt }.distinct().take(4),
        songs = songs,
        onOpen = {
            graph.scope.launch {
                runCatching { graph.mixes.save(saved()) }.onFailure { AppLog.w("Home", "Siansa Beats' mix ${mix.name} wasn't kept: ${it.message}") }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.navigate("mix/$id") }
            }
        },
        onPlay = { graph.player.play(songs) },
    )
}
