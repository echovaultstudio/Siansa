package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.mix.CardContent
import app.siansa.mix.CardContents
import app.siansa.mix.CardLook
import app.siansa.mix.CardSource
import app.siansa.mix.PinnedCard

/**
 * A custom card on Home (issue #622), in the look chosen for it: a row of song tiles, a row of albums, a wide
 * banner, a short song list, or a slim play button. Its title opens the card's page with every song.
 */
@Composable
fun PinnedCardSection(nav: NavController, card: PinnedCard, aiDown: String?) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    // A card by sound needs AudioMuse; while it can't be reached the card says so instead of trying.
    val blocked = if (card.source == CardSource.SOUND) aiDown else null
    val content by loadState("pinned-${card.id}-${card.hashCode()}-$version-$blocked") {
        if (blocked != null) throw IllegalStateException(blocked) else graph.pinnedCards.content(card)
    }
    val open = { nav.navigate("pinned/${card.id}") }
    fun play(songs: List<Song>, from: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        graph.player.play(songs, startIndex = from, shuffle = shuffle)
    }

    when (val load = content) {
        is Load.Loading -> Unit  // The card appears once its songs are in, like Home's other rows.
        is Load.Err -> {
            // Your own card: when it can't show, say why, so it isn't a mystery (AudioMuse down, library not synced).
            SectionTitle(card.title)
            Text(
                load.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.Page).testTag("pinnedProblem"),
            )
        }
        is Load.Ok -> if (!load.value.isEmpty) PinnedCardLook(card, load.value, open, ::play, nav)
        else {
            SectionTitle(card.title)
            Text(
                if (card.contents.fromBooks) "Nothing here yet." else "No songs match yet. Change the card in Settings › Home › Cards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.Page).testTag("pinnedProblem"),
            )
        }
    }
}

@Composable
private fun PinnedCardLook(card: PinnedCard, content: CardContent, open: () -> Unit, play: (List<Song>, Int, Boolean) -> Unit, nav: NavController) {
    // Cover rows (issue #782): albums, songs, books or episodes, a cover with its details beside it. Books and
    // episodes only come in this look.
    if (card.look == CardLook.COVER_ROWS || card.contents.fromBooks) {
        CoverRowsCard(nav, card, content, open)
        return
    }
    val songs = content.songs
    // An album card (issue #663): its tiles and list are its albums; its banner and button play them in turn.
    if (card.contents == CardContents.ALBUMS && card.look in setOf(CardLook.TILES, CardLook.ALBUMS, CardLook.LIST)) {
        if (card.look == CardLook.LIST) {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Show all", onClick = open)
                        .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current, bottom = Space.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(card.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                content.albums.take(LIST).forEach { album ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate("album/${album.id}") }.padding(horizontal = Space.Page, vertical = Space.Small).testTag("pinnedAlbumRow"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(album.coverArt, 150, Modifier.size(48.dp))
                        Column(Modifier.weight(1f).padding(start = Space.Medium)) {
                            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(album.artist.ifBlank { null }, album.year?.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            Shelf(card.title, content.albums.take(TILES), key = { it.id }, onOpenAll = open) { album ->
                AlbumTile(album.name, album.artist, album.coverArt, onClick = { nav.navigate("album/${album.id}") }, modifier = Modifier.width(140.dp))
            }
        }
        return
    }
    when (card.look) {
        CardLook.TILES -> Shelf(card.title, songs.take(TILES), key = { it.id }, onOpenAll = open) { song ->
            AlbumTile(song.title, song.artist, song.cover, onClick = { play(songs, songs.indexOf(song), false) }, modifier = Modifier.width(140.dp))
        }
        CardLook.ALBUMS -> Shelf(card.title, content.albums, key = { it.id }, onOpenAll = open) { album ->
            AlbumTile(album.name, album.artist, album.coverArt, onClick = { nav.navigate("album/${album.id}") }, modifier = Modifier.width(140.dp))
        }
        CardLook.BANNER -> PinnedBanner(card, songs, open, play, rememberListPlayback(songs))
        CardLook.LIST -> Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Show all", onClick = open)
                    .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current, bottom = Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(card.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            songs.take(LIST).forEachIndexed { i, song ->
                SongRow(song, onClick = { play(songs, i, false) })
            }
        }
        CardLook.BUTTON -> PinnedPlayButton(card, songs, open, play, rememberListPlayback(songs))
        CardLook.COVER_ROWS -> CoverRowsCard(nav, card, content, open)
    }
}

/** One wide card: the title, a few covers fanned at the right, and Play and Shuffle. Tapping it opens the page. */
@Composable
internal fun PinnedBanner(
    card: PinnedCard,
    songs: List<Song>,
    open: () -> Unit,
    play: (List<Song>, Int, Boolean) -> Unit,
    // Whether the card's songs are playing (issue #644); passed in, so the card draws without the player too.
    playback: ListPlayback = ListPlayback.IDLE,
) {
    val colors = tileColors(card.id)
    Column(Modifier.padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(colors.first, lerp(colors.second, Color.Black, 0.2f))))
                .clickable(onClick = open)
                .testTag("pinnedBanner"),
        ) {
            // The first three covers, each a little over the one before, like a fanned stack.
            val covers = songs.mapNotNull { it.cover }.distinct().take(3)
            Box(Modifier.align(Alignment.TopEnd).padding(Space.Medium)) {
                covers.forEachIndexed { i, c ->
                    Cover(c, 300, Modifier.offset(x = (-(covers.size - 1 - i) * 36).dp, y = (i * 8).dp).size(80.dp), RoundedCornerShape(8.dp))  // spacing: exact (the fan)
                }
            }
            Column(Modifier.padding(Space.Page).fillMaxWidth(0.55f)) {
                WholeWordsText(card.title, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
                Spacer(Modifier.height(Space.Tight))
                Text(listOfNotNull(countText(card, songs), card.detail).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.Page))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { playback.press { play(songs, 0, false) } }, modifier = Modifier.testTag("pinnedPlay")) {
                        Icon(if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                        Text(if (playback.playing) "Pause" else "Play", Modifier.padding(start = Space.Tight))
                    }
                    IconButton(onClick = { play(songs, 0, true) }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Color.White) }
                }
            }
        }
    }
}

/** A slim card: a play badge, the title and the song count. The ▶ plays the songs; the rest of the card opens the page (issue #730). */
@Composable
internal fun PinnedPlayButton(
    card: PinnedCard,
    songs: List<Song>,
    open: () -> Unit,
    play: (List<Song>, Int, Boolean) -> Unit,
    // ⏸ while this card's songs play: a tap pauses them (issue #644). Passed in, so the card draws without the player.
    playback: ListPlayback = ListPlayback.IDLE,
) {
    val colors = tileColors(card.id)
    Row(
        Modifier
            .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Open ${card.title}", onClick = open)
            .testTag("pinnedButton"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayBadge(colors, playing = playback.playing, title = card.title, onPlay = { playback.press { play(songs, 0, true) } }, tag = "pinnedButtonPlay")
        Column(Modifier.weight(1f).padding(horizontal = Space.Medium)) {
            Text(card.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pinnedButtonCaption(playback.playing, countText(card, songs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What a custom play button card says under its title: "Playing", else its song count or "Tap to open". */
internal fun pinnedButtonCaption(playing: Boolean, count: String?): String = when {
    playing -> "Playing" + (count?.let { " · $it" } ?: "")
    else -> count ?: "Tap to open"
}

/** "24 songs"; nothing for an album card, whose details already say Albums. */
private fun countText(card: PinnedCard, songs: List<Song>): String? = if (card.contents == CardContents.ALBUMS) null else "${songs.size} songs"

/** A custom card's page (issue #622): every song, ready to play or save as a playlist, with a button for a new set. */
@Composable
fun PinnedCardScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val card by loadState("pinned-$id") { graph.pinnedCards.get(id) ?: error("This card was removed") }
    val c = card.valueOrNull()
    val content by loadStateFresh("pinned-songs-$id-$round-${c?.hashCode()}") { c?.let { graph.pinnedCards.content(it, round) } }
    val title = c?.title ?: "Your card"
    val colors = tileColors(id)
    // A card of books or episodes (issue #782): its page lists every row the way the card shows them.
    if (c?.contents?.fromBooks == true) {
        Screen(title = title, nav = nav) { padding ->
            LoadBox(content, Modifier.padding(top = padding.calculateTopPadding())) { found ->
                if (found == null || found.isEmpty) {
                    Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.Page).testTag("pinnedProblem"))
                } else {
                    CoverRowsPage(nav, c, found, Modifier.fillMaxSize())
                }
            }
        }
        return
    }
    // An album card's page is its albums, like a label's (issue #663).
    if (c?.contents == CardContents.ALBUMS) {
        Screen(title = title, nav = nav, actions = {
            if (c.sort == app.siansa.mix.CardSort.RANDOM) IconButton(onClick = { round++ }) { Icon(Icons.Rounded.Refresh, "New albums") }
        }) { padding ->
            LoadBox(content, Modifier.padding(top = padding.calculateTopPadding())) { found ->
                val albums = found?.albums.orEmpty()
                if (albums.isEmpty()) {
                    Text("No albums match yet. Change the card in Settings › Home › Cards.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.Page).testTag("pinnedProblem"))
                } else {
                    AlbumsGridOrList(albums, page = "pinned-albums", onOpen = { nav.navigate("album/${it.id}") }, modifier = Modifier.fillMaxSize(),
                        title = "${c.detail} · ${albums.size} ${if (albums.size == 1) "album" else "albums"}")
                }
            }
        }
        return
    }
    CollectionPage(
        nav = nav,
        title = title,
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = content.valueOrNull()?.songs,
        error = (card as? Load.Err)?.message ?: (content as? Load.Err)?.message,
        saveAsPlaylist = true,
        cover = { m -> GeneratedCover(title, colors, m) },
        header = {
            CollectionByline(null, null, null, listOfNotNull("Your card", c?.detail, content.valueOrNull()?.songs?.let { "${it.size} songs" }).joinToString(" · "))
        },
        actions = {
            // A new set only for cards from the library: a sound search gives the same answer again.
            if (c?.source == CardSource.LIBRARY) IconButton(onClick = { round++ }) { Icon(Icons.Rounded.Refresh, "New songs") }
        },
    )
}

/** Songs shown as tiles on a card; the page has them all. */
private const val TILES = 20
/** Songs listed on a card; the page has them all. */
internal const val LIST = 5
