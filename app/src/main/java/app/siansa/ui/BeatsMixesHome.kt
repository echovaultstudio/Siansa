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
import app.siansa.data.HomeLook
import app.siansa.data.Song
import app.siansa.data.cover
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
 * **Made for you** (the kinds of music you play most) and **More of you** (On repeat, Rediscover, New for you).
 *
 * Each stands in a row of Arrange Home and follows it (the owner, 17 Nov: "you cannot change the mixes card type, it
 * needs to be possible"): For now in **Your mixes carousel**, Made for you and More of you in **Made for you**, so
 * each row's place, whether it shows, its cards' colours and its look are the row's. "As it is" is the row of sleeves
 * to flick through ([RecordCrate]); any other look draws the mixes as that look does.
 */
@Composable
fun BeatsMixesHome(nav: NavController, shelves: Set<String>, look: HomeLook) {
    val graph = LocalGraph.current
    val made by graph.beatsMixes.state.collectAsStateWithLifecycle()
    // Asked when Home shows, again at the next part of the day (Siansa Beats has made them ahead) and now and then;
    // asked once however many rows show them (BeatsMixes.refresh waits for an ask under way).
    LaunchedEffect(Unit) {
        while (true) {
            graph.beatsMixes.refresh()
            delay(60_000)
        }
    }
    val answer = made ?: return
    val shown = answer.shelves.filter { it.key in shelves }
    if (shown.isEmpty()) return
    // Each mix's songs, from the phone's copy of the library (the ids are the music server's own).
    val songs by produceState<Map<String, List<Song>>>(emptyMap(), shown) {
        value = shown.flatMap { it.mixes }.associate { it.id to graph.library.songs(it.songIds) }
    }
    Column(Modifier.fillMaxWidth().testTag("beatsMixes")) {
        shown.forEachIndexed { i, shelf ->
            val mixes = shelf.mixes.mapNotNull { mix -> homeMix(mix, songs[mix.id].orEmpty(), answer.keepDays, nav, graph) }
            if (mixes.isEmpty()) return@forEachIndexed
            val title = BeatsMixes.title(shelf)
            // Its own look and Carousel are the same row of sleeves, with four covers on each.
            if (look == HomeLook.AS_IS || look == HomeLook.CAROUSEL) {
                // Just the title: the mixes say the rest (the owner, 17 Nov: no line under it).
                SectionTitle(title, top = if (i == 0) LocalSectionGap.current else Space.Section)
                RecordCrate(mixes.map { it.record })
            } else {
                HomeLookRow(look, "beats-${shelf.key}", title, mixes.map { it.item }, songs = mixes.first().songs)
            }
        }
    }
}

/** A Siansa Beats mix as Home draws it: a sleeve for the crate, an item for the other looks. */
private class HomeMix(val record: CrateRecord, val item: HomeItem, val songs: List<Song>)

/**
 * A mix: shows what's in it under its name and opens as a mix page. Opened, or played from a look whose cover plays it,
 * it's kept as the app's own mixes are, for as many days as Siansa Beats keeps it (the owner, 17 Nov); played it
 * becomes a shortcut in the recent plays, so it still plays days later.
 */
private fun homeMix(mix: BeatsMixes.BeatsMix, songs: List<Song>, keepDays: Int, nav: NavController, graph: AppGraph): HomeMix? {
    if (songs.isEmpty()) return null
    val today = LocalDate.now().toString()
    val id = beatsMixId(mix, today)
    fun saved() = Mix(id, "beats-${mix.id}", today, mix.name, beatsMixDescription(mix), "", songs,
        System.currentTimeMillis(), aiNamed = mix.aiNamed, keepDays = keepDays)
    suspend fun keep() = runCatching { saved().also { graph.mixes.save(it) } }
        .onFailure { AppLog.w("Home", "Siansa Beats' mix ${mix.name} wasn't kept: ${it.message}") }.getOrNull()
    val open = {
        graph.scope.launch {
            keep()
            withContext(Dispatchers.Main) { nav.navigate("mix/$id") }
        }
        Unit
    }
    val play = {
        graph.player.play(songs)
        graph.scope.launch { keep()?.let { graph.recentPlays.note("mix/$id", it.title, songs.firstOrNull()?.cover) } }
        Unit
    }
    val covers = songs.map { it.coverArt }.distinct().take(4)
    return HomeMix(
        record = CrateRecord(key = id, name = mix.name, caption = mix.caption, covers = covers, songs = songs, onOpen = open),
        item = HomeItem(key = id, title = mix.name, subtitle = mix.caption, coverArt = covers.firstOrNull { it != null },
            open = open, play = play, meta = "${songs.size} songs"),
        songs = songs,
    )
}

/** The id a Siansa Beats mix is kept by here: Siansa Beats' own key, which no other mix has (0.55), else the day's. */
internal fun beatsMixId(mix: BeatsMixes.BeatsMix, today: String): String =
    "beats-" + mix.key.ifBlank { "$today-${mix.id}" }

/** A Siansa Beats mix's page says what's in it and the artists it's like. */
internal fun beatsMixDescription(mix: BeatsMixes.BeatsMix): String =
    listOf(mix.about, mix.why).filter { it.isNotBlank() }.joinToString(". ").ifBlank { "Made by Siansa Beats from your listening" }
