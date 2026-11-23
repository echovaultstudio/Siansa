package app.siansa.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.Artist
import app.siansa.data.picture
import app.siansa.data.Song
import app.siansa.mix.CardArtist
import app.siansa.mix.CardGenre
import app.siansa.mix.TimeCard
import app.siansa.mix.TimeCardList
import app.siansa.mix.TimeListKind
import kotlinx.coroutines.launch

/** The pages of Your listening's card, in order, each with the name at the card's top. */
enum class ListenPage(val label: String) {
    HOT("Hot now"), SONGS("Top songs"), ARTISTS("Artists"), GENRES("Genres"), BACK("Look back"),
}

/** The pages [card] has something for, in order: a page with nothing to show isn't there. */
internal fun listenPages(card: TimeCard): List<ListenPage> = listOfNotNull(
    ListenPage.HOT.takeIf { card.hot != null },
    ListenPage.SONGS.takeIf { card.top != null },
    ListenPage.ARTISTS.takeIf { card.artists.isNotEmpty() },
    ListenPage.GENRES.takeIf { card.genres.isNotEmpty() },
    ListenPage.BACK.takeIf { card.lists.isNotEmpty() },
)

/** What the card does when something on it is tapped. */
class ListeningActions(
    /** A list's songs from the library on the phone, in its order. */
    val songsOf: suspend (List<String>) -> List<Song>,
    /** Plays these songs from the one at this place, or shuffled. */
    val play: (List<Song>, Int, Boolean) -> Unit,
    /** Opens one of the card's lists as a page of songs ([TimeListKind.id]). */
    val openList: (String) -> Unit,
    val openArtist: (String) -> Unit,
    /** Opens this year in the Time Machine. */
    val openYear: () -> Unit,
)

/**
 * Home's Your listening card (issue #1343; the owner, 23 Nov: "show more. Like whats hot now, genres and artists… and
 * also my top songs", "B with swipe but same card"): the hero card's shape in a colour of its own, with a page each for
 * hot now, top songs, this year's artists and genres, and the look back lists. Swipe sideways or tap a name at its top.
 * Nothing on it moves when tapped: a list opens its own page of songs. Nothing when [card] has no page.
 */
@Composable
fun ListeningCard(card: TimeCard, actions: ListeningActions) {
    val pages = listenPages(card)
    if (pages.isEmpty()) return
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)
            .clip(RoundedCornerShape(LISTEN_CORNER))
            .background(Brush.verticalGradient(0f to LISTEN_TOP, 1f to LISTEN_FOOT))
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
                    ListenPage.HOT -> card.hot?.let { HotPage(it, actions) }
                    ListenPage.SONGS -> card.top?.let { SongsPage(it, actions) }
                    ListenPage.ARTISTS -> ArtistsPage(card.artists, actions)
                    ListenPage.GENRES -> GenresPage(card.genres, actions)
                    ListenPage.BACK -> BackPage(card.lists, actions)
                }
            }
        }
    }
}

/** A page's first line: what its numbers are of ("Last 30 days"), and where the rest is ("See all"). */
@Composable
private fun PageLine(of: String, more: String? = null, onMore: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = Space.Medium), verticalAlignment = Alignment.CenterVertically) {
        Text(of, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (more != null) Text(more, Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onMore).padding(horizontal = Space.Small, vertical = Space.Tight).testTag("listeningMore"),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = LISTEN_ACCENT, maxLines = 1)
    }
}

@Composable
private fun rememberSongs(list: TimeCardList, actions: ListeningActions): List<Song> {
    val songs by produceState(emptyList<Song>(), list.kind, list.songIds) { value = actions.songsOf(list.songIds) }
    return songs
}

/** Hot now: the most played song of the last 30 days, and Play and Shuffle for the list. A tap on the song opens the list. */
@Composable
private fun HotPage(list: TimeCardList, actions: ListeningActions) {
    val songs = rememberSongs(list, actions)
    val scope = rememberCoroutineScope()
    fun play(shuffle: Boolean) {
        if (songs.isNotEmpty()) actions.play(songs, 0, shuffle)
        else scope.launch { actions.songsOf(list.songIds).takeIf { it.isNotEmpty() }?.let { actions.play(it, 0, shuffle) } }
    }
    val first = songs.firstOrNull()
    PageLine(list.line, "See all") { actions.openList(list.kind) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Open Hot now") { actions.openList(list.kind) },
        horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.Bottom) {
        LocalCoverDraw.current(first?.coverArt ?: list.covers.firstOrNull(), 300, Modifier.size(LISTEN_COVER), RoundedCornerShape(8.dp), null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            WholeWordsText(first?.title.orEmpty(), MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
            Text(first?.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            list.plays.firstOrNull()?.let { Text(playCount(it), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = LISTEN_ACCENT, maxLines = 1) }
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
}

/** Top songs: the first [LISTEN_SONGS] of all time with their plays. A tap plays the list from that song. */
@Composable
private fun SongsPage(list: TimeCardList, actions: ListeningActions) {
    val songs = rememberSongs(list, actions)
    val plays = list.songIds.zip(list.plays).toMap()
    val draw = LocalCoverDraw.current
    PageLine(list.line, "See all") { actions.openList(list.kind) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        songs.take(LISTEN_SONGS).forEachIndexed { i, song ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Play ${song.title}") { actions.play(songs, i, false) }.testTag("listeningSong"),
                horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(LISTEN_RANK), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                draw(song.coverArt, 150, Modifier.size(LISTEN_SMALL_COVER), RoundedCornerShape(4.dp), null)
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                plays[song.id]?.let { Text(playCount(it), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.82f), maxLines = 1) }
            }
        }
    }
}

/** This year's artists, each with its picture and plays. A tap opens the artist, when it's in the library. */
@Composable
private fun ArtistsPage(artists: List<CardArtist>, actions: ListeningActions) {
    PageLine("This year", "See all", actions.openYear)
    val draw = LocalCoverDraw.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        artists.forEach { a ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = a.id != null, onClickLabel = "Open ${a.name}") { a.id?.let(actions.openArtist) }.testTag("listeningArtist"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.Tight),
            ) {
                draw(Artist(a.id ?: a.name, a.name, coverArt = a.coverArt, imageUrl = a.imageUrl).picture, 150, Modifier.size(LISTEN_ARTIST), androidx.compose.foundation.shape.CircleShape, null)
                // Whole words only, a little smaller when a name is wide ("Karnivool" at large text broke in two).
                WholeWordsText(a.name, MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(top = Space.Tight), color = Color.White, maxLines = 2)
                Text(playCount(a.plays), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

/** This year's genres, each with its share of the plays as a bar: the longest is the most played. */
@Composable
private fun GenresPage(genres: List<CardGenre>, actions: ListeningActions) {
    PageLine("This year", "See all", actions.openYear)
    val most = genres.maxOf { it.share }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        genres.forEachIndexed { i, g ->
            Column(Modifier.testTag("listeningGenre"), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.share} %", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(LISTEN_BAR).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(genreBar(g.share, most)).height(LISTEN_BAR).clip(RoundedCornerShape(50)).background(LISTEN_BARS[i % LISTEN_BARS.size]))
                }
            }
        }
    }
}

/** The look back lists side by side: its date on a square in its own colour, its name and how many songs. A tap opens it. */
@Composable
private fun BackPage(lists: List<TimeCardList>, actions: ListeningActions) {
    PageLine("From your Time Machine")
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

/** How much of its track a genre's bar fills: the most played fills it, the others their share of that; never nothing. */
internal fun genreBar(share: Int, most: Int): Float = (share.toFloat() / most.coerceAtLeast(1)).coerceIn(0.04f, 1f)

/** The card's own colour: a deep teal, so it reads as your history and not as today's mixes. */
private val LISTEN_TOP = Color(0xFF1C3A40)
private val LISTEN_FOOT = Color(0xFF0F181B)
/** The accent on the card: the dark theme's amber in both themes, as the card is dark in both. */
private val LISTEN_ACCENT = Color(0xFFFFB547)
private val LISTEN_BARS = listOf(Color(0xFFB05AA8), Color(0xFF4F8FD0), Color(0xFFD89A3C), Color(0xFF3FA39A))
private val LISTEN_CORNER = 16.dp
private val LISTEN_COVER = 120.dp
private val LISTEN_SMALL_COVER = 40.dp
private val LISTEN_ARTIST = 64.dp
private val LISTEN_BUTTON = 48.dp
private val LISTEN_BAR = 6.dp
private val LISTEN_RANK = 16.dp
private val LISTEN_MARK = 24.dp
private const val LISTEN_SONGS = 4

/** A colour for each kind of list, the same every day. */
internal fun timeListColours(kind: String): Pair<Color, Color> = when (kind) {
    TimeListKind.DAY.id -> Color(0xFFD89A3C) to Color(0xFF7A3A1E)
    TimeListKind.LAST_MONTH.id -> Color(0xFF4F8FD0) to Color(0xFF3A4E8A)
    TimeListKind.LAST_YEAR.id -> Color(0xFFB05AA8) to Color(0xFF5A2E7A)
    TimeListKind.TWO_YEARS.id -> Color(0xFF5FA86A) to Color(0xFF2E5A4A)
    TimeListKind.FIVE_YEARS.id -> Color(0xFFC8605A) to Color(0xFF6A2A3A)
    TimeListKind.HOT.id -> Color(0xFFD0603A) to Color(0xFF5A1E1A)
    else -> Color(0xFF3FA39A) to Color(0xFF1C4A50)
}
