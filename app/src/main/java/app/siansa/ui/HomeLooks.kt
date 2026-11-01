package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.HomeLook
import app.siansa.data.Song

/**
 * One thing on a row of Home (an album, a mix, a radio, a book…), in a form every look can draw (issue #655):
 * its title and a line under it, a cover or else a colour gradient, and what a tap opens.
 */
data class HomeItem(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val coverArt: String? = null,
    val colors: Pair<Color, Color> = tileColors(key),
    /** A round cover (an artist). */
    val round: Boolean = false,
    val open: () -> Unit,
    /** Plays this item (an album, a mix): Cover rows' cover does it. Without one, the cover opens the item. */
    val play: (() -> Unit)? = null,
    /** A third line for Cover rows ("11 songs · 2024"); the subtitle is the small line above the title. */
    val meta: String = "",
    /** How far through, 0..1, for a book or an episode: a bar along the cover in Cover rows. */
    val progress: Float? = null,
)

/**
 * A row of Home in one of the custom cards' looks (issue #655), for rows that aren't [HomeLook.AS_IS]:
 * - Tiles: a row of covers to swipe through, [openAll] behind the title;
 * - Banner: one wide card with the first covers fanned at the right, and Play (or Open);
 * - List: the first [LIST] listed, [openAll] behind the title;
 * - Play button: a slim card that opens the row; its ▶ plays it ([play]). Without [play] it's an arrow (issue #730).
 * [play] plays the row's music; [songs] (when known) lets the button show ⏸ while they play. Without [openAll]
 * the first item stands in for the row.
 */
@Composable
fun HomeLookRow(
    look: HomeLook,
    id: String,
    title: String,
    items: List<HomeItem>,
    openAll: (() -> Unit)? = null,
    play: (() -> Unit)? = null,
    songs: List<Song> = emptyList(),
) {
    if (items.isEmpty()) return
    val open = openAll ?: items.first().open
    when (look) {
        HomeLook.AS_IS, HomeLook.TILES -> Shelf(title, items, key = { it.key }, onOpenAll = openAll) { item -> HomeItemTile(item) }
        HomeLook.BANNER -> HomeBanner(id, title, items, open, play, songs)
        HomeLook.LIST -> HomeList(title, items, open)
        HomeLook.BUTTON -> HomeButton(id, title, items, open, play, songs)
        HomeLook.COVER_ROWS -> HomeCoverRows(title, items, open)
    }
}

/**
 * Cover rows for a row of Home (issue #789): the same shelf as a custom card's, three rows to a column. Each item's
 * cover plays it when it has a play, else opens it; the row opens it. The first [CoverRows.ON_HOME] items.
 */
@Composable
private fun HomeCoverRows(title: String, items: List<HomeItem>, open: () -> Unit) {
    val actions = items.take(app.siansa.mix.CoverRows.ON_HOME).map { item -> CoverRowAction(coverRowOf(item), playing = false, open = item.open, play = item.play ?: item.open, menu = emptyList()) }
    CoverRowsShelf(title, actions, open)
}

/** A Home item as a cover row: subtitle above, title, then its third line; colours where there's no cover. */
internal fun coverRowOf(item: HomeItem): app.siansa.mix.CoverRow = app.siansa.mix.CoverRow(
    key = item.key,
    kind = app.siansa.mix.CoverRow.Kind.OTHER,
    cover = item.coverArt,
    small = item.subtitle,
    title = item.title,
    meta = item.meta,
    progress = item.progress,
    colors = if (item.coverArt == null) argb(item.colors.first) to argb(item.colors.second) else null,
    round = item.round,
)

private fun argb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun HomeItemTile(item: HomeItem) {
    if (item.coverArt != null) {
        AlbumTile(item.title, item.subtitle, item.coverArt, item.open, Modifier.width(TILE).testTag("homeLookTile"), round = item.round)
    } else {
        GradientTile(title = item.title, colors = item.colors, onClick = item.open, size = TILE, modifier = Modifier.testTag("homeLookTile"))
    }
}

/** The covers of the first items, or a colour block for an item without one. */
@Composable
private fun ItemArt(item: HomeItem, modifier: Modifier) {
    val shape = if (item.round) CircleShape else RoundedCornerShape(8.dp)
    if (item.coverArt != null) Cover(item.coverArt, 300, modifier, shape)
    else Box(modifier.clip(shape).background(Brush.linearGradient(listOf(item.colors.first, item.colors.second))))
}

@Composable
private fun HomeBanner(id: String, title: String, items: List<HomeItem>, open: () -> Unit, play: (() -> Unit)?, songs: List<Song>) {
    val colors = tileColors(id)
    val playback = if (play != null && songs.isNotEmpty()) rememberListPlayback(songs) else ListPlayback.IDLE
    Column(Modifier.padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(colors.first, lerp(colors.second, Color.Black, 0.2f))))
                .clickable(onClick = open)
                .testTag("homeLookBanner"),
        ) {
            val fan = items.take(3)
            Box(Modifier.align(Alignment.TopEnd).padding(Space.Medium)) {
                fan.forEachIndexed { i, item ->
                    ItemArt(item, Modifier.offset(x = (-(fan.size - 1 - i) * 36).dp, y = (i * 8).dp).size(80.dp))  // spacing: exact (the fan)
                }
            }
            Column(Modifier.padding(Space.Page).fillMaxWidth(0.55f)) {
                WholeWordsText(title, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
                Spacer(Modifier.height(Space.Tight))
                Text(items.take(3).joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.Page))
                FilledTonalButton(onClick = { if (play != null) playback.press(play) else open() }, modifier = Modifier.testTag("homeLookBannerButton")) {
                    Icon(if (play == null) Icons.Rounded.ChevronRight else if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                    Text(if (play == null) "Open" else if (playback.playing) "Pause" else "Play", Modifier.padding(start = Space.Tight))
                }
            }
        }
    }
}

@Composable
private fun HomeList(title: String, items: List<HomeItem>, open: () -> Unit) {
    Column(Modifier.testTag("homeLookList")) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Show all", onClick = open)
                .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current, bottom = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.take(LIST).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable(onClick = item.open).padding(horizontal = Space.Page, vertical = Space.Small).testTag("homeLookListRow"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemArt(item, Modifier.size(48.dp))
                Spacer(Modifier.width(Space.Medium))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeButton(id: String, title: String, items: List<HomeItem>, open: () -> Unit, play: (() -> Unit)?, songs: List<Song>) {
    val colors = tileColors(id)
    val playback = if (play != null && songs.isNotEmpty()) rememberListPlayback(songs) else ListPlayback.IDLE
    // The card opens the section; only the play badge plays (or pauses) it, and there's no arrow (issue #730).
    Row(
        Modifier
            .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Open $title", onClick = open)
            .testTag("homeLookButton"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayBadge(colors, playing = playback.playing, title = title, onPlay = play?.let { p -> { playback.press(p) } }, tag = "homeLookButtonPlay")
        Column(Modifier.weight(1f).padding(horizontal = Space.Medium)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(homeButtonCaption(play != null, playback.playing, items.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The coloured square at the start of a play button card (issue #730): ▶ (⏸ while its songs play) that plays or pauses
 * them when tapped. Without [onPlay] it's only an arrow, and a tap goes to the card, which opens.
 */
@Composable
internal fun PlayBadge(colors: Pair<Color, Color>, playing: Boolean, title: String, onPlay: (() -> Unit)?, tag: String) {
    Box(
        Modifier
            .size(64.dp)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .then(if (onPlay != null) Modifier.clickable(onClickLabel = if (playing) "Pause $title" else "Play $title", role = Role.Button, onClick = onPlay).testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                onPlay == null -> Icons.Rounded.ChevronRight
                playing -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            when {
                onPlay == null -> null
                playing -> "Pause $title"
                else -> "Play $title"
            },
            tint = Color.White, modifier = Modifier.size(32.dp),
        )
    }
}

/** What the slim card says under its title: the card opens, the ▶ plays. */
internal fun homeButtonCaption(plays: Boolean, playing: Boolean, count: Int): String = when {
    playing -> "Playing"
    plays -> "Press play, or tap to open"
    else -> "Tap to open · $count"
}

private val TILE = 140.dp
