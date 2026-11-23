package app.siansa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.Artist
import app.siansa.data.ListenColour
import app.siansa.data.Song
import app.siansa.data.picture
import app.siansa.mix.CardArtist
import app.siansa.mix.CardGenre
import app.siansa.mix.ListenRange
import app.siansa.mix.RangeData
import app.siansa.mix.TimeCard
import app.siansa.mix.TimeCardList
import app.siansa.mix.TimeListKind
import kotlinx.coroutines.launch

/** The pages of Your listening's card, in order, each with the name at the card's top. */
enum class ListenPage(val label: String) {
    SONGS("Songs"), ARTISTS("Artists"), GENRES("Genres"), BACK("Look back"),
}

/**
 * The pages [card] has something for, in order. Songs, Artists and Genres are there when any of the card's times has
 * them (a time with none says so on the page, so the time can still be changed there); Look back when it has a list.
 */
internal fun listenPages(card: TimeCard): List<ListenPage> = listOfNotNull(
    ListenPage.SONGS.takeIf { card.ranges.any { it.songs != null } },
    ListenPage.ARTISTS.takeIf { card.ranges.any { it.artists.isNotEmpty() } },
    ListenPage.GENRES.takeIf { card.ranges.any { it.genres.isNotEmpty() } },
    ListenPage.BACK.takeIf { card.lists.isNotEmpty() },
)

/** What the card does when something on it is tapped. */
class ListeningActions(
    /** A list's songs from the library on the phone, in its order. */
    val songsOf: suspend (List<String>) -> List<Song>,
    /** Plays these songs from the one at this place, or shuffled. */
    val play: (List<Song>, Int, Boolean) -> Unit,
    /** Opens one of the card's lists as a page of songs (its kind). */
    val openList: (String) -> Unit,
    val openArtist: (String) -> Unit,
    /** Another time was picked in the drop-down. */
    val setRange: (ListenRange) -> Unit,
)

/**
 * Home's Your listening card (issues #1343, #1347; the owner, 23 Nov: "the listening card needs to have filters… Make
 * it look totally beautiful", as a drop-down where the card said "Last 30 days"): the hero card's shape in a [colour]
 * of your choice with a soft glow in its corner, and a page each for your songs, artists and genres of the time picked
 * ([range]), and the look back lists. Swipe sideways or tap a name at its top. Nothing on it moves when tapped: a list
 * opens its own page of songs. Nothing when [card] has no page.
 */
@Composable
fun ListeningCard(card: TimeCard, range: ListenRange, colour: ListenColour, actions: ListeningActions) {
    val pages = listenPages(card)
    if (pages.isEmpty()) return
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val data = card.range(range) ?: RangeData(range.id)
    val paint = listenPaint(colour, data.songs?.covers?.firstOrNull())
    Column(
        Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)
            .clip(RoundedCornerShape(LISTEN_CORNER))
            .background(Brush.verticalGradient(0f to paint.top, 1f to paint.foot))
            // A soft light in its top right corner, in the colour's own glow.
            .drawBehind {
                val radius = size.width * 0.62f
                drawCircle(Brush.radialGradient(listOf(paint.glow.copy(alpha = 0.34f), Color.Transparent), Offset(size.width * 0.92f, 0f), radius), radius, Offset(size.width * 0.92f, 0f))
            }
            .padding(top = Space.Section, bottom = Space.Section)
            .testTag("listeningCard"),
    ) {
        Text("YOUR LISTENING", Modifier.padding(horizontal = Space.Page),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)),
            fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = Space.Page, end = Space.Page, top = Space.Medium).testTag("listeningTabs"),
            horizontalArrangement = Arrangement.spacedBy(Space.Page),
        ) {
            pages.forEachIndexed { i, page ->
                val chosen = pager.currentPage == i
                Column(
                    Modifier.clickable(role = Role.Tab, onClickLabel = "Show ${page.label}") { scope.launch { pager.animateScrollToPage(i) } }
                        .semantics { selected = chosen }.testTag("listeningTab-${page.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(page.label, Modifier.padding(vertical = Space.Small), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = if (chosen) LISTEN_ACCENT else Color.White.copy(alpha = 0.6f), maxLines = 1)
                    Box(Modifier.width(LISTEN_MARK).height(2.dp).background(if (chosen) LISTEN_ACCENT else Color.Transparent))
                }
            }
        }
        // Every page is laid out, so the card is as tall as its tallest and doesn't change height on a swipe.
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = Space.Page).testTag("listeningPages"), beyondViewportPageCount = pages.size,
            verticalAlignment = Alignment.Top) { i ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.Page).testTag("listeningPage-${pages[i].name}")) {
                when (pages[i]) {
                    ListenPage.SONGS -> SongsPage(range, data.songs, actions)
                    ListenPage.ARTISTS -> ArtistsPage(range, data.artists, actions)
                    ListenPage.GENRES -> GenresPage(range, data.genres, actions)
                    ListenPage.BACK -> BackPage(card.lists, actions)
                }
            }
        }
    }
}

/** The card's colours as drawn. */
internal class ListenPaint(val top: Color, val foot: Color, val glow: Color)

/**
 * [colour] as the card draws it. From its cover: the colour of [cover] (the top song's), deep at the top and nearly
 * black at the foot so the white words read on any cover, its glow the colour itself.
 */
@Composable
private fun listenPaint(colour: ListenColour, cover: String?): ListenPaint {
    if (colour != ListenColour.COVER || cover == null) return ListenPaint(Color(colour.top), Color(colour.foot), Color(colour.glow))
    val tint = rememberHeaderTint(cover, "listening-card")
    return ListenPaint(lerp(tint, Color.Black, 0.55f), lerp(tint, Color.Black, 0.86f), tint)
}

/**
 * A page's first line: the time it shows as a drop-down (the owner, 23 Nov: "a drop down where I circled", the line
 * that said "Last 30 days"), and where the rest is ("See all") at its right.
 */
@Composable
private fun RangeLine(range: ListenRange, actions: ListeningActions, more: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(bottom = Space.Medium), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))
                    .clickable(role = Role.DropdownList, onClickLabel = "Choose the time") { open = true }
                    .padding(start = Space.Medium, end = Space.Tight, top = Space.Tight, bottom = Space.Tight).testTag("listeningRange"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(range.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = LISTEN_ACCENT, maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, null, tint = LISTEN_ACCENT)
            }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                ListenRange.entries.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(r.label, fontWeight = if (r == range) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { open = false; if (r != range) actions.setRange(r) },
                        trailingIcon = { if (r == range) Icon(Icons.Rounded.Check, "Chosen") },
                        modifier = Modifier.testTag("listeningRange-${r.id}"),
                    )
                }
            }
        }
        Box(Modifier.weight(1f))
        if (more != null) Text("See all", Modifier.clip(RoundedCornerShape(50)).clickable(onClick = more).padding(horizontal = Space.Small, vertical = Space.Tight).testTag("listeningMore"),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = LISTEN_ACCENT, maxLines = 1)
    }
}

/** What a page says when its time has nothing. */
@Composable
private fun Nothing(what: String, range: ListenRange) {
    Text("No $what ${range.said}.", Modifier.padding(vertical = Space.Section), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
}

@Composable
private fun Rule() = Box(Modifier.fillMaxWidth().padding(vertical = Space.Page).height(1.dp).background(Color.White.copy(alpha = 0.10f)))

/**
 * Songs: the most played song of the time picked, big, with Play and Shuffle for its list; under it the next
 * [LISTEN_SONGS] with a bar each for their plays against the first's. A tap on a song plays the list from it; the big
 * one and See all open the list.
 */
@Composable
private fun SongsPage(range: ListenRange, list: TimeCardList?, actions: ListeningActions) {
    RangeLine(range, actions, list?.let { { actions.openList(it.kind) } })
    if (list == null) return Nothing("songs played", range)
    val songs by produceState(emptyList<Song>(), list.kind, list.songIds) { value = actions.songsOf(list.songIds) }
    val plays = remember(list) { list.songIds.zip(list.plays).toMap() }
    val scope = rememberCoroutineScope()
    val draw = LocalCoverDraw.current
    fun play(shuffle: Boolean) {
        if (songs.isNotEmpty()) actions.play(songs, 0, shuffle)
        else scope.launch { actions.songsOf(list.songIds).takeIf { it.isNotEmpty() }?.let { actions.play(it, 0, shuffle) } }
    }
    val first = songs.firstOrNull()
    val most = (first?.let { plays[it.id] } ?: list.plays.firstOrNull() ?: 1).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Open your top songs") { actions.openList(list.kind) },
        horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.Bottom) {
        Box {
            draw(first?.coverArt ?: list.covers.firstOrNull(), 300, Modifier.size(LISTEN_COVER), RoundedCornerShape(8.dp), null)
            Text("#1", Modifier.padding(Space.Small).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = Space.Small, vertical = Space.Tight),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = LISTEN_ACCENT)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            WholeWordsText(first?.title.orEmpty(), MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
            Text(first?.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playCount(most), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = LISTEN_ACCENT, maxLines = 1)
        }
    }
    Row(Modifier.padding(top = Space.Page), horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Button(onClick = { play(false) }, modifier = Modifier.height(LISTEN_BUTTON).testTag("listeningPlay")) {
            Icon(Icons.Rounded.PlayArrow, null)
            Text("Play", Modifier.padding(start = Space.Tight), fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.OutlinedIconButton(onClick = { play(true) }, modifier = Modifier.size(LISTEN_BUTTON).testTag("listeningShuffle"),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Color.White) }
        Text("Your ${list.songIds.size} most played", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val next = songs.drop(1).take(LISTEN_SONGS)
    if (next.isEmpty()) return
    Rule()
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        next.forEachIndexed { i, song ->
            val n = plays[song.id] ?: 0
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Play ${song.title}") { actions.play(songs, i + 1, false) }.testTag("listeningSong"),
                horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 2}", Modifier.width(LISTEN_RANK), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                draw(song.coverArt, 150, Modifier.size(LISTEN_SMALL_COVER), RoundedCornerShape(4.dp), null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                        Text(song.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.82f), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                        Text(song.artist, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(LISTEN_SONG_BAR).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))) {
                            Box(Modifier.fillMaxWidth(shareBar(n, most)).height(LISTEN_SONG_BAR).clip(RoundedCornerShape(50)).background(LISTEN_ACCENT.copy(alpha = 0.85f)))
                        }
                    }
                }
            }
        }
    }
}

/** Artists: the most played big, with a ring in the accent, and the next ones listed with their plays. A tap opens the artist. */
@Composable
private fun ArtistsPage(range: ListenRange, artists: List<CardArtist>, actions: ListeningActions) {
    RangeLine(range, actions)
    val first = artists.firstOrNull() ?: return Nothing("artists played", range)
    val draw = LocalCoverDraw.current
    fun picture(a: CardArtist) = Artist(a.id ?: a.name, a.name, coverArt = a.coverArt, imageUrl = a.imageUrl).picture
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = first.id != null, onClickLabel = "Open ${first.name}") { first.id?.let(actions.openArtist) }.testTag("listeningArtist"),
        horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.CenterVertically) {
        draw(picture(first), 300, Modifier.size(LISTEN_TOP_ARTIST).border(3.dp, LISTEN_ACCENT, CircleShape).padding(Space.Tight), CircleShape, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text("#1 ARTIST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = LISTEN_ACCENT, maxLines = 1)
            WholeWordsText(first.name, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
            Text(playCount(first.plays), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), maxLines = 1)
        }
    }
    if (artists.size < 2) return
    Rule()
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        artists.drop(1).forEachIndexed { i, a ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = a.id != null, onClickLabel = "Open ${a.name}") { a.id?.let(actions.openArtist) }.testTag("listeningArtist"),
                horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 2}", Modifier.width(LISTEN_RANK), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                draw(picture(a), 150, Modifier.size(LISTEN_SMALL_COVER), CircleShape, null)
                Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playCount(a.plays), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 1)
            }
        }
    }
}

/** Genres: a ring with each genre's share of the plays, the most played's in its middle, and each named beside it. */
@Composable
private fun GenresPage(range: ListenRange, genres: List<CardGenre>, actions: ListeningActions) {
    RangeLine(range, actions)
    val first = genres.firstOrNull() ?: return Nothing("genres played", range)
    Row(Modifier.fillMaxWidth().padding(top = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Section), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(LISTEN_RING).testTag("listeningRing"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(LISTEN_RING)) {
                val stroke = LISTEN_RING_WIDTH.toPx()
                val inset = stroke / 2
                val box = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
                var from = -90f
                genres.forEachIndexed { i, g ->
                    val sweep = 360f * g.share / 100f
                    drawArc(LISTEN_BARS[i % LISTEN_BARS.size], from, sweep, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Butt))
                    from += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${first.share}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                Text("top genre", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
            genres.forEachIndexed { i, g ->
                Row(Modifier.testTag("listeningGenre"), horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(LISTEN_DOT).clip(CircleShape).background(LISTEN_BARS[i % LISTEN_BARS.size]))
                    Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.share} %", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 1)
                }
            }
        }
    }
}

/** The look back lists side by side: its date on a square in its own colour, its name and how many songs. A tap opens it. */
@Composable
private fun BackPage(lists: List<TimeCardList>, actions: ListeningActions) {
    Text("From your Time Machine", Modifier.padding(top = Space.Tight, bottom = Space.Page), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        lists.forEach { list ->
            val colours = timeListColours(list.kind)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Open ${list.title}") { actions.openList(list.kind) }.testTag("listeningBack"),
                verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(colours.first, colours.second))),
                    contentAlignment = Alignment.Center) {
                    // "NOV 2025" over two lines, so it fits a small square at large text too.
                    Text(list.tag.replace(' ', '\n'), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                }
                // Up to three lines, so "This time last year" is whole at large text too.
                WholeWordsText(list.title, MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), Modifier.padding(top = Space.Tight), color = Color.White, maxLines = 3)
                Text(songCount(list.songIds.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

internal fun songCount(n: Int): String = if (n == 1) "1 song" else "$n songs"

internal fun playCount(n: Int): String = if (n == 1) "1 play" else "${java.text.NumberFormat.getIntegerInstance().format(n)} plays"

/** How much of its track a bar fills: the most played fills it, the others their share of that; never nothing. */
internal fun shareBar(n: Int, most: Int): Float = (n.toFloat() / most.coerceAtLeast(1)).coerceIn(0.04f, 1f)

/** The accent on the card: the dark theme's amber in both themes, as the card is dark in both. */
private val LISTEN_ACCENT = Color(0xFFFFB547)
private val LISTEN_BARS = listOf(Color(0xFFB05AA8), Color(0xFF4F8FD0), Color(0xFFD89A3C), Color(0xFF3FA39A))
private val LISTEN_CORNER = 16.dp
private val LISTEN_COVER = 120.dp
private val LISTEN_SMALL_COVER = 40.dp
private val LISTEN_TOP_ARTIST = 104.dp
private val LISTEN_BUTTON = 48.dp
private val LISTEN_SONG_BAR = 3.dp
private val LISTEN_RANK = 16.dp
private val LISTEN_MARK = 24.dp
private val LISTEN_RING = 112.dp
private val LISTEN_RING_WIDTH = 18.dp
private val LISTEN_DOT = 10.dp
private const val LISTEN_SONGS = 3

/** A colour for each kind of list, the same every day. */
internal fun timeListColours(kind: String): Pair<Color, Color> = when (kind) {
    TimeListKind.DAY.id -> Color(0xFFD89A3C) to Color(0xFF7A3A1E)
    TimeListKind.LAST_MONTH.id -> Color(0xFF4F8FD0) to Color(0xFF3A4E8A)
    TimeListKind.LAST_YEAR.id -> Color(0xFFB05AA8) to Color(0xFF5A2E7A)
    TimeListKind.TWO_YEARS.id -> Color(0xFF5FA86A) to Color(0xFF2E5A4A)
    TimeListKind.FIVE_YEARS.id -> Color(0xFFC8605A) to Color(0xFF6A2A3A)
    else -> Color(0xFF3FA39A) to Color(0xFF1C4A50)
}
