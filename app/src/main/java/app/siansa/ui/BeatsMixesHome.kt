package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
            // Two looks only (the owner, 17 Nov): Tiles, each with four of its covers; else the carousel.
            if (beatsLook(look) == HomeLook.TILES) {
                Shelf(title, mixes, key = { it.key }) { m -> MixMosaicTile(m) }
            } else {
                // Just the title: the mixes say the rest (the owner, 17 Nov: no line under it).
                SectionTitle(title, top = if (i == 0) LocalSectionGap.current else Space.Section)
                RecordCrate(mixes)
            }
        }
    }
}

/** A mix as a tile: four of its covers two by two, its name, and what's in it. */
@Composable
internal fun MixMosaicTile(record: CrateRecord) {
    Column(Modifier.width(MOSAIC_TILE).clickable(onClickLabel = "Open ${record.name}", onClick = record.onOpen).padding(LocalTileInset.current).testTag("mixMosaicTile")) {
        val square = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
        if (record.covers.any { it != null }) CoverMosaic(record.covers, square)
        else {
            // No cover known yet: its colours, as the sleeve has.
            val colors = remember(record.key) { tileColors(record.key) }
            androidx.compose.foundation.layout.Box(square.background(Brush.linearGradient(listOf(colors.first, colors.second))))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(Space.Small))
        Text(record.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (record.caption.isNotBlank()) {
            Text(record.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val MOSAIC_TILE = 140.dp // as Home's other tiles

/**
 * A mix as a sleeve or a tile: what's in it under its name; it opens as a mix page, kept as the app's own mixes are, for
 * as many days as Siansa Beats keeps it (the owner, 17 Nov); played from there it becomes a shortcut in the recent plays.
 */
private fun homeMix(mix: BeatsMixes.BeatsMix, songs: List<Song>, keepDays: Int, nav: NavController, graph: AppGraph): CrateRecord? {
    if (songs.isEmpty()) return null
    val today = LocalDate.now().toString()
    val id = beatsMixId(mix, today)
    fun saved() = Mix(id, "beats-${mix.id}", today, mix.name, beatsMixDescription(mix), "", songs,
        System.currentTimeMillis(), aiNamed = mix.aiNamed, keepDays = keepDays)
    val open = {
        graph.scope.launch {
            runCatching { graph.mixes.save(saved()) }.onFailure { AppLog.w("Home", "Siansa Beats' mix ${mix.name} wasn't kept: ${it.message}") }
            withContext(Dispatchers.Main) { nav.navigate("mix/$id") }
        }
        Unit
    }
    // Each album's cover once: four different ones for the tile's square.
    val covers = songs.mapNotNull { it.cover }.distinct().take(4)
    return CrateRecord(key = id, name = mix.name, caption = mix.caption, covers = covers, songs = songs, onOpen = open)
}

/** The id a Siansa Beats mix is kept by here: Siansa Beats' own key, which no other mix has (0.55), else the day's. */
internal fun beatsMixId(mix: BeatsMixes.BeatsMix, today: String): String =
    "beats-" + mix.key.ifBlank { "$today-${mix.id}" }

/** A Siansa Beats mix's page says what's in it and the artists it's like. */
internal fun beatsMixDescription(mix: BeatsMixes.BeatsMix): String =
    listOf(mix.about, mix.why).filter { it.isNotBlank() }.joinToString(". ").ifBlank { "Made by Siansa Beats from your listening" }
