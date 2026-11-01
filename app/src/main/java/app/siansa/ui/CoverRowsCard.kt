package app.siansa.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.mix.CardContent
import app.siansa.mix.CardContents
import app.siansa.mix.CoverRow
import app.siansa.mix.CoverRows
import app.siansa.mix.PinnedCard
import app.siansa.net.Episode
import kotlinx.coroutines.launch

/** A row with what it does: open its page, play it (or carry on), and the extras in its ⋮ menu. */
internal class CoverRowAction(
    val row: CoverRow,
    val playing: Boolean,
    val open: () -> Unit,
    val play: () -> Unit,
    val menu: List<Pair<String, () -> Unit>>,
)

/**
 * The rows a card shows as Cover rows (issue #782): its albums, its songs, or its books and episodes (Keep listening:
 * the most recently listened first). [progress] is Audiobookshelf's, by book.
 */
internal fun coverRowsOf(card: PinnedCard, content: CardContent, progress: Map<String, BookProgress>): List<CoverRow> = when {
    card.contents == CardContents.ALBUMS -> content.albums.map(CoverRows::album)
    !card.contents.fromBooks -> content.songs.map(CoverRows::song)
    else -> {
        val rows = content.books.map { it to CoverRows.book(it, progress[it.id]) } +
            content.episodes.map { (podcast, ep) -> podcast to CoverRows.episode(podcast, ep, progress[podcast.id]?.takeIf { it.episodeId == ep.id }) }
        if (card.contents == CardContents.LISTENING) rows.sortedByDescending { progress[it.first.id]?.updatedAt ?: 0L }.map { it.second }
        else rows.map { it.second }
    }
}

/** What each row of a card does, with the player's state for the one playing. */
@Composable
private fun rememberCoverRowActions(nav: NavController, card: PinnedCard, content: CardContent): List<CoverRowAction> {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val books by graph.bookPlayer.state.collectAsStateWithLifecycle()
    val progress by graph.books.progress.collectAsStateWithLifecycle()
    val rows = remember(card, content, progress) { coverRowsOf(card, content, progress) }
    val albums = content.albums.associateBy { "album-${it.id}" }
    val songs = content.songs.associateBy { "song-${it.id}" }
    val bookOf = content.books.associateBy { "book-${it.id}" }
    val episodeOf = content.episodes.associateBy { "episode-${it.second.id}" }
    return rows.map { row ->
        val album = albums[row.key]
        val song = songs[row.key]
        val book = bookOf[row.key]
        val episode = episodeOf[row.key]
        when {
            album != null -> CoverRowAction(
                row,
                playing = player.isPlaying && player.current?.albumId in album.allIds,
                open = { nav.navigate("album/${album.id}") },
                play = {
                    if (player.current?.albumId in album.allIds) graph.player.togglePlay()
                    else scope.launch { graph.player.play(graph.library.songsOf(album)) }
                },
                menu = listOfNotNull(
                    "Add to queue" to { scope.launch { graph.player.addAll(graph.library.songsOf(album)) }; Unit },
                    album.artistId?.let { id -> "Go to artist" to { nav.navigate("artist/$id") } },
                ),
            )
            song != null -> CoverRowAction(
                row,
                playing = player.isPlaying && player.current?.id == song.id,
                open = { song.albumId?.let { nav.navigate("album/$it") } ?: playSongs(graph, content.songs, song) },
                play = { if (player.current?.id == song.id) graph.player.togglePlay() else playSongs(graph, content.songs, song) },
                menu = listOfNotNull(
                    "Play next" to { graph.player.playNext(song) },
                    "Add to queue" to { graph.player.addToQueue(song) },
                    song.albumId?.let { id -> "Go to album" to { nav.navigate("album/$id") } },
                ),
            )
            book != null -> CoverRowAction(
                row,
                playing = books.playing && books.book?.id == book.id,
                open = { nav.navigate("book/${book.id}") },
                play = { graph.bookPlayer.playOrToggle(book, progress[book.id]?.takeIf { !it.finished }?.currentSec ?: 0.0) },
                menu = listOf(
                    "Open" to { nav.navigate("book/${book.id}") },
                    "Play from the start" to { graph.bookPlayer.play(book, 0.0) },
                ),
            )
            episode != null -> {
                val (podcast, ep) = episode
                val isThis = books.book?.id == podcast.id && books.book?.title == ep.title
                CoverRowAction(
                    row,
                    playing = books.playing && isThis,
                    open = { nav.navigate("book/${podcast.id}") },
                    play = { if (isThis) graph.bookPlayer.togglePlay() else graph.bookPlayer.playEpisode(podcast, ep) },
                    menu = listOf("Open the podcast" to { nav.navigate("book/${podcast.id}") }),
                )
            }
            else -> CoverRowAction(row, false, {}, {}, emptyList())
        }
    }
}

private fun playSongs(graph: app.siansa.AppGraph, songs: List<Song>, song: Song) {
    graph.player.play(songs, startIndex = songs.indexOf(song).coerceAtLeast(0))
}

/**
 * A custom card in the Cover rows look on Home (issue #782): its title (which opens its page) over columns of
 * three rows, the next column peeking in at the right. Each row: the cover (tap to play or carry on; ⏸ over it while
 * it plays, progress along its bottom), who it's by, what it is and how long, and ⋮ for more.
 */
@Composable
internal fun CoverRowsCard(nav: NavController, card: PinnedCard, content: CardContent, open: () -> Unit) {
    val actions = rememberCoverRowActions(nav, card, content).take(CoverRows.ON_HOME)
    CoverRowsShelf(card.title, actions, open)
}

/** The shelf itself, apart from the player and the server, so pictures and spacing tests can draw it. */
@Composable
internal fun CoverRowsShelf(title: String, actions: List<CoverRowAction>, open: () -> Unit) {
    Column(Modifier.testTag("coverRows")) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Show all", onClick = open)
                .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current, bottom = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val columns = actions.chunked(CoverRows.PER_COLUMN)
        val list = rememberLazyListState()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // One column fills the page but for the next one's edge, so it's plain there are more.
            val columnWidth = if (columns.size > 1) maxWidth - COLUMN_PEEK else maxWidth - Space.Page * 2
            LazyRow(
                state = list,
                contentPadding = PaddingValues(horizontal = Space.Page),
                horizontalArrangement = Arrangement.spacedBy(Space.Medium),
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(list),
            ) {
                items(columns.size) { i ->
                    Column(Modifier.width(columnWidth).testTag("coverRowsColumn")) {
                        columns[i].forEachIndexed { j, action -> CoverRowItem(action, divider = j < columns[i].lastIndex) }
                    }
                }
            }
        }
        if (columns.size > 1) {
            PageDots(columns.size, list.firstVisibleItemIndex, Modifier.align(Alignment.CenterHorizontally).padding(top = Space.Small))
        }
    }
}

/** Every row of a card, one under the other: the page of a card of books or episodes (issue #782). */
@Composable
internal fun CoverRowsPage(nav: NavController, card: PinnedCard, content: CardContent, modifier: Modifier = Modifier) {
    val actions = rememberCoverRowActions(nav, card, content)
    LazyColumn(modifier, contentPadding = PaddingValues(start = Space.Page, end = Space.Page, bottom = bottomBarPadding(Space.Section))) {
        items(actions.size, key = { actions[it].row.key }) { i -> CoverRowItem(actions[i], divider = i < actions.lastIndex) }
    }
}

/** One row: the cover, three lines and ⋮; a hairline under it from the text on, except under a column's last. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CoverRowItem(action: CoverRowAction, divider: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val row = action.row
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .combinedClickable(onClickLabel = "Open ${row.title}", onLongClickLabel = "More", onLongClick = { menu = true }, onClick = action.open)
            .testTag("coverRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(COVER)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = if (action.playing) "Pause ${row.title}" else "Play ${row.title}") {
                    AppLog.i("Cards", "${if (action.playing) "Pause" else "Play"} ${row.kind.name.lowercase()} from a cover row")
                    action.play()
                }
                .testTag("coverRowCover"),
        ) {
            // No cover: the same grey square Cover draws, without asking for one.
            if (row.cover != null) Cover(row.cover, 150, Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            if (action.playing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, null, tint = Accent, modifier = Modifier.size(32.dp))
                }
            }
            row.progress?.let { p ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(PROGRESS).background(Color.Black.copy(alpha = 0.45f))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Accent).testTag("coverRowProgress"))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = Space.Medium)) {
            Column(Modifier.align(Alignment.CenterStart).padding(end = Space.Tight)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (action.playing) Icon(Icons.Rounded.MusicNote, null, tint = Accent, modifier = Modifier.size(14.dp).padding(end = Space.Tight))
                    Text(row.small, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.meta.isNotBlank()) {
                    Text(row.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (divider) HorizontalDivider(Modifier.align(Alignment.BottomStart).testTag("coverRowDivider"), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (action.menu.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("coverRowMore")) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    action.menu.forEach { (label, run) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; run() })
                    }
                }
            }
        }
    }
}

/** Small dots under a shelf of columns, the one in view wider. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.testTag("coverRowsDots"), horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
        repeat(count) { i ->
            val on = i == current.coerceIn(0, count - 1)
            Box(
                Modifier.height(DOT).width(if (on) DOT * 2.5f else DOT).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (on) 0.8f else 0.25f)),
            )
        }
    }
}

private val ROW_HEIGHT = 96.dp
private val COVER = 80.dp
private val PROGRESS = 4.dp  // spacing: exact
private val COLUMN_PEEK = 56.dp  // spacing: exact
private val DOT = 6.dp  // spacing: exact
