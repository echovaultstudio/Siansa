package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.Mix
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.BeatsMixes
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val records = shelf.mixes.mapNotNull { mix -> crateRecord(mix, songs[mix.id].orEmpty(), made.keepDays, nav, graph) }
            if (records.isEmpty()) return@forEachIndexed
            // Just the title: the mixes say the rest (the owner, 17 Nov: no line under it).
            SectionTitle(BeatsMixes.title(shelf), top = if (i == 0) LocalSectionGap.current else Space.Section)
            RecordCrate(records)
        }
    }
}

/**
 * A mix as a record: shows what's in it under its name and opens as a mix page. Opened, it's kept as the app's own
 * mixes are, for as many days as Siansa Beats keeps it (the owner, 17 Nov); played from its page it becomes a shortcut
 * in the recent plays, so it still plays days later.
 */
private fun crateRecord(mix: BeatsMixes.BeatsMix, songs: List<Song>, keepDays: Int, nav: NavController, graph: AppGraph): CrateRecord? {
    if (songs.isEmpty()) return null
    val today = LocalDate.now().toString()
    val id = beatsMixId(mix, today)
    fun saved() = Mix(id, "beats-${mix.id}", today, mix.name, beatsMixDescription(mix), "", songs,
        System.currentTimeMillis(), aiNamed = mix.aiNamed, keepDays = keepDays)
    suspend fun keep() = runCatching { graph.mixes.save(saved()) }
        .onFailure { AppLog.w("Home", "Siansa Beats' mix ${mix.name} wasn't kept: ${it.message}") }
    return CrateRecord(
        key = id,
        name = mix.name,
        caption = mix.caption,
        covers = songs.map { it.coverArt }.distinct().take(4),
        songs = songs,
        onOpen = {
            graph.scope.launch {
                keep()
                withContext(Dispatchers.Main) { nav.navigate("mix/$id") }
            }
        },
    )
}

/** The id a Siansa Beats mix is kept by here: Siansa Beats' own key, which no other mix has (0.55), else the day's. */
internal fun beatsMixId(mix: BeatsMixes.BeatsMix, today: String): String =
    "beats-" + mix.key.ifBlank { "$today-${mix.id}" }

/** A Siansa Beats mix's page says what's in it and the artists it's like. */
internal fun beatsMixDescription(mix: BeatsMixes.BeatsMix): String =
    listOf(mix.about, mix.why).filter { it.isNotBlank() }.joinToString(". ").ifBlank { "Made by Siansa Beats from your listening" }
