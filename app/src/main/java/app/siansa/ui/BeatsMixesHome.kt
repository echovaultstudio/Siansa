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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
 * needs to be possible"): For now in **For now** (once Your mixes carousel), Made for you and More of you in **Made for you**, so
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
    // Being made again (the part of the day turned, or settings changed): the last ones stay, a little dimmed, with a
    // small spinner, instead of vanishing and popping back (the owner, 17 Nov). Only after a moment, so the quick
    // asks every quarter of an hour don't flicker it; nothing runs while they aren't being made.
    val asking by graph.beatsMixes.refreshing.collectAsStateWithLifecycle()
    var remaking by remember { mutableStateOf(false) }
    LaunchedEffect(asking) {
        if (asking) delay(REMAKING_AFTER_MS)
        remaking = asking
    }
    val dim by androidx.compose.animation.core.animateFloatAsState(if (remaking) REMAKING_ALPHA else 1f, label = "remaking")
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim }.testTag("beatsMixes")) {
        shown.forEachIndexed { i, shelf ->
            val mixes = shelf.mixes.mapNotNull { mix -> homeMix(mix, songs[mix.id].orEmpty(), answer.keepDays, nav, graph) }
            if (mixes.isEmpty()) return@forEachIndexed
            val title = BeatsMixes.title(shelf)
            // Its own look is the carousel; Tiles has four of each mix's covers; every other look as any row has it
            // (#1261, the owner, 19 Nov: every row gets every option).
            when (val shownLook = beatsLook(look)) {
                HomeLook.TILES -> Shelf(title, mixes, key = { it.key }) { m -> MixMosaicTile(m) }
                HomeLook.CAROUSEL -> {
                    // Just the title: the mixes say the rest (the owner, 17 Nov: no line under it).
                    SectionTitle(title, top = if (i == 0) LocalSectionGap.current else Space.Section)
                    RecordCrate(mixes)
                }
                else -> HomeLookRow(shownLook, "beats-${shelf.key}", title, mixes.map(::homeItemOf),
                    play = { graph.player.play(mixes.first().songs) }, songs = mixes.first().songs)
            }
        }
    }
        if (remaking) {
            androidx.compose.material3.CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Space.Section, end = Space.Page).size(REMAKING_SPINNER).testTag("beatsMixesRemaking"),
            )
        }
    }
}

/** A mix as a tile: four of its covers two by two, its name, and what's in it. */
@Composable
internal fun MixMosaicTile(record: CrateRecord) {
    Column(Modifier.width(MOSAIC_TILE).clickable(onClickLabel = "Open ${record.name}", onClick = record.onOpen).padding(LocalTileInset.current).testTag("mixMosaicTile")) {
        val square = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
        val colors = remember(record.key, record.colors) { record.colors ?: tileColors(record.key) }
        CardPicture(record.covers, null, colors, record.name, square, songIds = remember(record.songs) { record.songs.map { it.id } })
        androidx.compose.foundation.layout.Spacer(Modifier.height(Space.Small))
        Text(record.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (record.caption.isNotBlank()) {
            Text(record.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val MOSAIC_TILE = 140.dp // as Home's other tiles
private val REMAKING_SPINNER = 14.dp
private const val REMAKING_ALPHA = 0.6f
private const val REMAKING_AFTER_MS = 600L

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

/** A Siansa Beats mix as an item any of Home's looks can draw: its name, what's in it, its first cover. */
internal fun homeItemOf(record: CrateRecord): HomeItem = HomeItem(
    key = record.key, title = record.name, subtitle = record.caption, coverArt = record.covers.firstOrNull { it != null },
    colors = record.colors ?: tileColors(record.key), open = record.onOpen, mosaic = record.covers,
    meta = app.siansa.mix.CoverRows.songCount(record.songs.size).orEmpty(),
    songs = record.songs,
)

/** The id a Siansa Beats mix is kept by here: Siansa Beats' own key, which no other mix has (0.55), else the day's. */
internal fun beatsMixId(mix: BeatsMixes.BeatsMix, today: String): String =
    "beats-" + mix.key.ifBlank { "$today-${mix.id}" }

/** A Siansa Beats mix's page says what's in it and the artists it's like. */
internal fun beatsMixDescription(mix: BeatsMixes.BeatsMix): String =
    listOf(mix.about, mix.why).filter { it.isNotBlank() }.joinToString(". ").ifBlank { "Made by Siansa Beats from your listening" }
