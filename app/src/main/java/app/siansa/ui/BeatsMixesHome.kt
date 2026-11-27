package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import app.siansa.data.picture
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
    val artistsFrom = app.siansa.mix.ArtistsFrom.of(graph.settings.state.collectAsStateWithLifecycle().value.artistRadiosFrom)
    val shown = answer.shelves.filter { it.key in shelves }
    if (shown.isEmpty()) return
    // Each mix's songs, from the phone's copy of the library (the ids are the music server's own); and Forgotten
    // artists' artists, found by a song of theirs (the owner, 26 Nov: "show actual artists instead of tracks").
    val found by produceState(Found(emptyMap(), emptyMap()), shown) {
        val songs = shown.flatMap { it.mixes }.associate { it.id to graph.library.songs(it.songIds) }
        val artists = shown.flatMap { it.mixes }.filter { it.people.isNotEmpty() }.associate { mix ->
            mix.id to forgottenArtists(mix, songs[mix.id].orEmpty()) { id -> graph.library.artist(id) }
        }.filterValues { it.isNotEmpty() }
        value = Found(songs, artists)
    }
    val songs = found.songs
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
            // Forgotten artists' artists are in Artist radios (the owner, 26 Nov), unless that's your listening only.
            val mixes = shelf.mixes.filter { it.id !in found.artists || artistsFrom == app.siansa.mix.ArtistsFrom.MINE }.mapNotNull { mix -> homeMix(mix, songs[mix.id].orEmpty(), answer.keepDays, nav, graph) }
            val title = BeatsMixes.title(shelf)
            // Its own look is the carousel; Tiles has four of each mix's covers; every other look as any row has it
            // (#1261, the owner, 19 Nov: every row gets every option).
            if (mixes.isNotEmpty()) BeatsMixesRow("beats-${shelf.key}", title, mixes, beatsLook(look), top = if (i == 0) LocalSectionGap.current else Space.Section)
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

/** Siansa Beats' [mixes] as a row in [look]: its carousel, tiles of four covers, or as any row has that look. */
@Composable
private fun BeatsMixesRow(key: String, title: String, mixes: List<CrateRecord>, look: HomeLook, top: androidx.compose.ui.unit.Dp = LocalSectionGap.current) {
    val graph = LocalGraph.current
    when (look) {
        HomeLook.TILES -> Shelf(title, mixes, key = { it.key }) { m -> MixMosaicTile(m) }
        HomeLook.CAROUSEL -> {
            // Just the title: the mixes say the rest (the owner, 17 Nov: no line under it).
            SectionTitle(title, top = top)
            RecordCrate(mixes)
        }
        else -> HomeLookRow(look, key, title, mixes.map(::homeItemOf), play = { graph.player.play(mixes.first().songs) }, songs = mixes.first().songs)
    }
}

/** A Siansa Beats card's look on Home as a row's: its carousel, or the look of the same name. */
internal fun beatsCardLook(look: app.siansa.mix.CardLook): HomeLook = when (look) {
    app.siansa.mix.CardLook.CAROUSEL -> HomeLook.CAROUSEL
    app.siansa.mix.CardLook.TILES, app.siansa.mix.CardLook.ALBUMS -> HomeLook.TILES
    app.siansa.mix.CardLook.BANNER -> HomeLook.BANNER
    app.siansa.mix.CardLook.LIST -> HomeLook.LIST
    app.siansa.mix.CardLook.BUTTON -> HomeLook.BUTTON
    app.siansa.mix.CardLook.COVER_ROWS -> HomeLook.COVER_ROWS
    app.siansa.mix.CardLook.GRID -> HomeLook.GRID
}

/**
 * A Siansa Beats card (the owner, 26 Nov): the mixes of the kinds it has switched on, from every part of Siansa Beats'
 * answer, as one row in the card's look and name. Nothing shows until Siansa Beats has made them.
 */
@Composable
fun BeatsCardRow(nav: NavController, card: app.siansa.mix.PinnedCard) {
    val graph = LocalGraph.current
    val made by graph.beatsMixes.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.beatsMixes.refresh() }
    val answer = made ?: return
    val chosen = remember(answer, card.beats) { app.siansa.mix.BeatsKind.mixes(answer, card.beats) }
    val songs by produceState(emptyMap<String, List<Song>>(), chosen) { value = chosen.associate { it.id to graph.library.songs(it.songIds) } }
    val mixes = chosen.mapNotNull { mix -> homeMix(mix, songs[mix.id].orEmpty(), answer.keepDays, nav, graph) }
    if (mixes.isEmpty()) return
    BeatsMixesRow("pinned-${card.id}", card.title, mixes, beatsCardLook(card.look))
}

/** A shelf's mixes' songs, and the artists of a mix that has them (Forgotten artists), by mix id. */
private class Found(val songs: Map<String, List<Song>>, val artists: Map<String, List<ForgottenArtist>>)

/** A forgotten artist as Artist radios shows it: the library's [artist] (for its radio, page and photo), and how long since. */
internal data class ForgottenArtist(val artist: app.siansa.data.Artist, val person: BeatsMixes.Person)

/**
 * Siansa Beats' artists for Artist radios (the owner, 26 Nov): every mix's in [answer] that names them (Forgotten
 * artists), found on the phone, each once.
 */
internal suspend fun beatsArtists(answer: BeatsMixes.Shelves?, songsOf: suspend (List<String>) -> List<Song>, artistOf: suspend (String) -> app.siansa.data.Artist?): List<ForgottenArtist> =
    answer?.shelves.orEmpty().flatMap { it.mixes }.filter { it.people.isNotEmpty() }
        .flatMap { mix -> forgottenArtists(mix, songsOf(mix.songIds), artistOf) }.distinctBy { it.artist.id }

/**
 * [mix]'s artists ([BeatsMixes.BeatsMix.people]) found in the library by a song of theirs in [songs], in Siansa
 * Beats' order; one whose song isn't on the phone, or has no artist, is left out.
 */
internal suspend fun forgottenArtists(mix: BeatsMixes.BeatsMix, songs: List<Song>, artistOf: suspend (String) -> app.siansa.data.Artist?): List<ForgottenArtist> {
    val byId = songs.associateBy { it.id }
    return mix.people.mapNotNull { person ->
        val song = byId[person.song] ?: return@mapNotNull null
        val id = song.artistId ?: return@mapNotNull null
        val artist = artistOf(id) ?: app.siansa.data.Artist(id, song.artist.ifBlank { person.name })
        ForgottenArtist(artist, person)
    }.distinctBy { it.artist.id }
}

/** A mix as a tile: four of its covers two by two, its name, and what's in it. */
@Composable
internal fun MixMosaicTile(given: CrateRecord) {
    val record = given.tapped(LocalTapPlays.current)
    Column(Modifier.width(tileWidth(LocalCrateSize.current)).combinedClickable(onClickLabel = "Open ${record.name}", onLongClick = record.onHold, onClick = record.onOpen).padding(LocalTileInset.current).testTag("mixMosaicTile")) {
        val square = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
        val colors = remember(record.key, record.colors) { record.colors ?: tileColors(record.key) }
        val names = LocalCardNames.current
        CardPicture(record.covers, null, colors, record.name, square, songIds = remember(record.songs) { record.songs.map { it.id } },
            artists = remember(record.songs) { artistLine(record.songs) },
            names = when (names) { app.siansa.data.CardNames.UNDER -> false; app.siansa.data.CardNames.ON -> true; else -> null }, line = if (LocalCardLines.current) record.caption else "")
        // Under it, two lines each (the owner, 21 Nov: genres were cut off); on it, when the row's Names says so.
        // On Auto, a picture with its name on it (Aura, Frost, Poster) has just its line under it.
        val style = LocalPictureStyle.current
        val namesItself = remember(style, record.covers) { pictureNamesItself(style, record.covers, null) }
        val named = names == app.siansa.data.CardNames.AUTO && namesItself
        if (names != app.siansa.data.CardNames.ON) TileNames(record.name, record.caption, showTitle = !named)
    }
}

private val REMAKING_SPINNER = 14.dp
private const val REMAKING_ALPHA = 0.6f
private const val REMAKING_AFTER_MS = 600L

/**
 * A mix as a sleeve or a tile: what's in it under its name; it opens as a mix page, kept as the app's own mixes are, for
 * as many days as Siansa Beats keeps it (the owner, 17 Nov); played from there it becomes a shortcut in the recent plays.
 */
private fun homeMix(mix: BeatsMixes.BeatsMix, songs: List<Song>, keepDays: Int, nav: NavController, graph: AppGraph): CrateRecord? {
    if (songs.isEmpty()) return null
    // Its shape, for its picture (issue #153): any card of these songs finds it.
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
    // Played straight away, with Play on tap on (the owner, 27 Nov): kept first, as when it's opened, so Now playing
    // names it.
    val play = {
        graph.scope.launch {
            runCatching { graph.mixes.save(saved()) }.onFailure { AppLog.w("Home", "Siansa Beats' mix ${mix.name} wasn't kept: ${it.message}") }
            withContext(Dispatchers.Main) {
                graph.player.play(songs)
                graph.playingFrom.note("mix/$id", mix.name)
            }
        }
        Unit
    }
    // Each album's cover once: four different ones for the tile's square.
    val covers = songs.mapNotNull { it.cover }.distinct().take(4)
    return CrateRecord(key = id, name = mix.name, caption = mix.caption, covers = covers, songs = songs, onOpen = open, onPlay = play)
}

/** A Siansa Beats mix as an item any of Home's looks can draw: its name, what's in it, its first cover. */
internal fun homeItemOf(record: CrateRecord): HomeItem = HomeItem(
    key = record.key, title = record.name, subtitle = record.caption, coverArt = record.covers.firstOrNull { it != null },
    colors = record.colors ?: tileColors(record.key), open = record.onOpen, play = record.onPlay, mosaic = record.covers,
    meta = app.siansa.mix.CoverRows.songCount(record.songs.size).orEmpty(),
    songs = record.songs,
)

/** The id a Siansa Beats mix is kept by here: Siansa Beats' own key, which no other mix has (0.55), else the day's. */
internal fun beatsMixId(mix: BeatsMixes.BeatsMix, today: String): String =
    "beats-" + mix.key.ifBlank { "$today-${mix.id}" }

/** A Siansa Beats mix's page says what's in it and the artists it's like. */
internal fun beatsMixDescription(mix: BeatsMixes.BeatsMix): String =
    listOf(mix.about, mix.why).filter { it.isNotBlank() }.joinToString(". ").ifBlank { "Made by Siansa Beats from your listening" }
