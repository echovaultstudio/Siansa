package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.Chapter
import app.siansa.data.bookLength
import app.siansa.log.AppLog

/**
 * Home's "Your books" or "Your podcasts" carousel (issue #521): Audiobookshelf's own shelves, as big cards like
 * the mixes'. A started book says Continue, the chapter and the time left, with ▶ on the cover; others say
 * Up next (a series' next) or New. Only with a server set up; nothing while it can't be reached.
 */
@Composable
fun BooksCarousel(nav: NavController, podcasts: Boolean, look: app.siansa.data.HomeLook = app.siansa.data.HomeLook.AS_IS) {
    val graph = LocalGraph.current
    val progress by graph.books.progress.collectAsStateWithLifecycle()
    // The shelves as last read, so scrolling back to the carousel shows it at once, at its height (#569: it was
    // empty for a moment, then grew, pushing the page down). Read again only when a minute old.
    val shelvesAll by graph.books.shelvesState.collectAsStateWithLifecycle()
    LaunchedEffect(podcasts) { graph.books.refreshShelves(podcasts) }
    val cards = booksForCarousel(shelvesAll[podcasts].orEmpty().flatMap { shelf -> shelf.books.map { shelf.id to it } }, progress)
    if (cards.isEmpty()) return
    if (look != app.siansa.data.HomeLook.AS_IS) {
        // Another look, picked under Arrange Home (issue #655).
        HomeLookRow(look, if (podcasts) "podcasts" else "books", if (podcasts) "Your podcasts" else "Your books", cards.map { (label, book) ->
            HomeItem(book.id, book.title, label, book.cover, open = { nav.navigate("book/${book.id}") })
        })
        return
    }
    val pager = rememberPagerState { cards.size }
    Column(Modifier.fillMaxWidth().testTag(if (podcasts) "homePodcasts" else "homeBooks")) {
        SectionTitle(if (podcasts) "Your podcasts" else "Your books")
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Space.Page),
            pageSpacing = Space.Medium,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val (label, book) = cards[page]
            BookCard(book, progress[book.id], label, onOpen = { nav.navigate("book/${book.id}") })
        }
        CarouselDots(count = cards.size, current = pager.currentPage)
    }
}

/**
 * Which books the carousel shows and what each card says: started ones first ("Continue", "Pick up" for a
 * podcast), then "Up next" from the series shelf, then "New"; each book once, at most [CAROUSEL_MAX].
 */
internal fun booksForCarousel(shelved: List<Pair<String, Book>>, progress: Map<String, BookProgress>): List<Pair<String, Book>> {
    val seen = HashSet<String>()
    val out = ArrayList<Pair<String, Book>>()
    fun add(label: String, book: Book) { if (seen.add(book.id)) out += label to book }
    shelved.filter { (_, b) -> progress[b.id]?.let { it.started && !it.finished } == true }
        .forEach { (_, b) -> add(if (b.podcast) "Pick up" else "Continue", b) }
    shelved.filter { (shelf, _) -> shelf.contains("series", ignoreCase = true) || shelf.contains("continue-series") }
        .forEach { (_, b) -> add("Up next", b) }
    shelved.forEach { (_, b) -> if (progress[b.id]?.finished != true) add("New", b) }
    return out.take(CAROUSEL_MAX)
}

// The five that matter (owner, 27 Oct): what you're in, then up next and new; the rest are in Your Library.
private const val CAROUSEL_MAX = 5

/**
 * The top of a book's or podcast's page, laid out like an album's (issue #626): the cover's colour fading down
 * behind it all, the cover centred (a portrait for a book, a square for a podcast), then the title, the author in
 * bold, the details, Audiobookshelf's description (folded to a few lines until tapped), where you are, and the
 * album page's row of buttons. The cover doesn't move as the page scrolls.
 */
@Composable
internal fun BookPageHeader(
    b: Book,
    color: Color,
    surface: Color,
    details: String,
    position: BookPosition?,
    actions: @Composable () -> Unit,
) {
    val width = LocalConfiguration.current.screenWidthDp
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to color, 0.75f to color.copy(alpha = 0.35f), 1f to surface))
            .statusBarsPadding()
            .padding(top = 56.dp, start = Space.Page, end = Space.Page),  // spacing: exact (below the top bar)
    ) {
        val coverModifier = if (b.podcast) Modifier.size((width * 0.62f).dp)
        else Modifier.size(width = (width * 0.48f).dp, height = (width * 0.48f * BOOK_COVER_RATIO).dp)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Cover(b.cover, LARGE_COVER, coverModifier.shadow(18.dp, RoundedCornerShape(8.dp)).testTag("bookCover"), RoundedCornerShape(8.dp))
        }
        Spacer(Modifier.height(Space.Page))
        Column(Modifier.fillMaxWidth().testTag("bookWords")) {
            Text(b.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            b.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(Space.Small))
            CollectionByline(b.author.takeIf { it.isNotBlank() }, null, null, details)
        }
        b.description?.let(::stripHtml)?.takeIf { it.isNotBlank() }?.let { about ->
            Spacer(Modifier.height(Space.Medium))
            BookAbout(about)
        }
        position?.let { p ->
            Spacer(Modifier.height(Space.Medium))
            BookPositionBar(p)
        }
        Spacer(Modifier.height(Space.Tight))
        actions()
    }
}

/** Audiobookshelf's description of the book or podcast: a few lines, the rest a tap away. */
@Composable
private fun BookAbout(text: String) {
    var open by remember(text) { mutableStateOf(false) }
    var longer by remember(text) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(enabled = longer || open) { open = !open }.testTag("bookAbout")) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (open) Int.MAX_VALUE else BOOK_ABOUT_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!open) longer = it.hasVisualOverflow },
        )
        if (longer || open) Text(if (open) "Less" else "More", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Accent)
    }
}

/** Where you are: the episode on a podcast, a slim bar, and how much is heard and left. */
@Composable
private fun BookPositionBar(p: BookPosition) {
    Column(Modifier.fillMaxWidth().testTag("bookPosition")) {
        p.episodeTitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.Tight))
        }
        LinearProgressIndicator(
            progress = { p.fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).testTag("bookProgress"),  // spacing: exact (a hairline bar)
            color = Accent,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            gapSize = 0.dp,  // spacing: exact (no gap between the heard and unheard parts)
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(Space.Tight))
        Text(if (p.finished) "Finished" else p.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A book cover's height to its width: most audiobook covers are a little taller than wide. */
private const val BOOK_COVER_RATIO = 1.44f

/** How solid the title bar is as the page scrolls past its cover: clear until most of it has gone, then solid. */
internal fun titleBarAlpha(scrolledPx: Float, coverPx: Float): Float = ((scrolledPx - coverPx * 0.8f) / (coverPx * 0.3f)).coerceIn(0f, 1f)

/** The cover on a book card; the words start after it and a page's gap. */
private val CARD_COVER_WIDTH = 100.dp

@Composable
private fun BookCard(book: Book, progress: BookProgress?, label: String, onOpen: () -> Unit) {
    val graph = LocalGraph.current
    val colors = tileColors("book-${book.id}")
    val chapter = progress?.let { book.chapterAt(it.currentSec) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .clickable(onClick = onOpen)
            .testTag("bookCard"),
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(Space.Page)) {
            Cover(book.cover, 300, Modifier.size(width = CARD_COVER_WIDTH, height = 148.dp), RoundedCornerShape(8.dp))
            if (progress?.started == true && !progress.finished) {
                // ▶ on the cover continues where you were (owner's decision on #521).
                FilledIconButton(
                    onClick = { graph.bookPlayer.playOrToggle(book, progress.currentSec); AppLog.i("Books", "Continue \"${book.title}\" from Home") },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.Small).size(40.dp).testTag("bookContinue"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = OnAccent),
                ) {
                    // ⏸ while this book plays: the button pauses it (#553).
                    val playing by graph.bookPlayer.state.collectAsStateWithLifecycle()
                    val now = playing.book?.id == book.id && playing.playing
                    Icon(if (now) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now) "Pause ${book.title}" else "Continue ${book.title}", Modifier.size(24.dp))
                }
            }
        }
        // The words start a page's gap after the cover, which sits a page's gap in: the cover's width plus both.
        Column(Modifier.align(Alignment.TopStart).padding(start = CARD_COVER_WIDTH + Space.Page + Space.Page, top = Space.Page, end = Space.Page)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold)
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    book.author.takeIf { it.isNotBlank() },
                    book.narrator.takeIf { it.isNotBlank() }?.let { "read by $it" },
                    chapter?.let { "Chapter ${it.index + 1} of ${book.chapters.size}" },
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (progress?.started == true && !progress.finished) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = CARD_COVER_WIDTH + Space.Page + Space.Page, end = Space.Page, bottom = Space.Page)) {
                Text("${bookLength(progress.leftSec)} left", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(Space.Tight))
                LinearProgressIndicator(
                    progress = { progress.fraction.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp), // spacing: exact (a hairline bar)
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}

/**
 * A book's page (issue #521): cover, who made it, Continue or Play, progress, description and chapters. Styled like an
 * album's page (#550): the cover's colour fades down from the top behind the cover and title, with just a back arrow
 * over it until the page scrolls, then a solid bar in that colour. The cover sits left, the words to its right.
 */
@Composable
fun BookScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val progressAll by graph.books.progress.collectAsStateWithLifecycle()
    val page by loadState(id) { graph.books.book(id) }
    val book = page.valueOrNull()
    val progress = progressAll[id]
    val playing by graph.bookPlayer.state.collectAsStateWithLifecycle()
    val color = rememberCoverColor(book?.cover)
    val surface = MaterialTheme.colorScheme.surface
    val listState = rememberLazyListState()
    // The title bar fills in as the cover scrolls away, as on an album's page.
    val headerPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp * 0.62f).dp.toPx() }
    val scrolled by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
    }
    val barAlpha = titleBarAlpha(scrolled, headerPx)
    Box(Modifier.fillMaxSize().background(surface)) {
        LoadBox(page, Modifier) { b ->
            // Loaded here: a list's content block isn't composable (issue #521).
            val episodes by loadState("episodes-${b.id}") { if (b.podcast) graph.audiobookshelf.episodes(b.id) else emptyList() }
            val bookmarks by loadState("bookmarks-${b.id}") { runCatching { graph.audiobookshelf.bookmarks(b.id) }.getOrDefault(emptyList()) }
            val episodeList = episodes.valueOrNull().orEmpty()
            val position = bookPosition(b, playing, progress, episodeList)
            val current = position?.currentSec ?: 0.0
            val chapterNow = if (b.podcast) null else b.chapterAt(current)
            val finished = !b.podcast && progress?.finished == true
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
                item {
                    BookPageHeader(b, color, surface, bookDetails(b, episodeList.size.takeIf { b.podcast && it > 0 }, finished), position) {
                        BookActions(b, progress, position)
                    }
                }
                if (b.podcast) {
                    // A podcast's episodes, newest first (issue #521); tap one to play it.
                    val list = episodes.valueOrNull().orEmpty()
                    if (list.isNotEmpty()) {
                        item { SectionTitle("${list.size} episodes") }
                        items(list.size, key = { "ep-${list[it].id}" }) { i ->
                            EpisodeRow(list[i]) {
                                graph.bookPlayer.playEpisode(b, list[i])
                                AppLog.i("Books", "Play episode \"${list[i].title}\" of \"${b.title}\"")
                            }
                        }
                    }
                }
                val marks = bookmarks.valueOrNull().orEmpty()
                if (marks.isNotEmpty()) {
                    item { SectionTitle("Bookmarks") }
                    items(marks.size, key = { "bm-${marks[it].timeSec}" }) { i ->
                        val m = marks[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { graph.bookPlayer.play(b, m.timeSec) }.padding(horizontal = Space.Page, vertical = Space.Small).testTag("bookmark-$i"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
                        ) {
                            Icon(Icons.Rounded.Bookmark, null, tint = Accent, modifier = Modifier.size(20.dp))
                            Text(m.title.ifBlank { "Bookmark" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bookLength(m.timeSec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (b.chapters.isNotEmpty()) {
                    item { SectionTitle("${b.chapters.size} chapters") }
                    items(b.chapters.size, key = { "ch-$it" }) { i ->
                        val c = b.chapters[i]
                        ChapterRow(c, state = when {
                            chapterNow != null && c.index == chapterNow.index -> ChapterState.NOW
                            c.endSec <= current -> ChapterState.HEARD
                            else -> ChapterState.AHEAD
                        }) {
                            graph.bookPlayer.play(b, c.startSec)
                            AppLog.i("Books", "Play \"${b.title}\" from chapter ${c.index + 1}")
                        }
                    }
                }
            }
        }
        FadingTitleBar(nav, book?.title ?: "Book", color, barAlpha)
    }
}

/**
 * The album page's row of buttons for a book or podcast (issue #626): download and finished on the left as plain
 * buttons, the big round Play on the right (⏸ while it plays). There's no shuffle: a book plays in order.
 */
@Composable
private fun BookActions(b: Book, progress: BookProgress?, position: BookPosition?) {
    val graph = LocalGraph.current
    val playing by graph.bookPlayer.state.collectAsStateWithLifecycle()
    val downloads by graph.bookDownloads.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val launch = rememberPlayLauncher()
    var removing by remember { mutableStateOf(false) }
    PlayButtonRow {
        // The whole book on the phone, or not (issue #521), as an album's download button.
        IconButton(
            onClick = {
                when {
                    downloads.fetching == b.id -> Unit
                    b.id in downloads.done -> removing = true
                    else -> scope.launch {
                        try { graph.bookDownloads.download(b) } catch (e: Exception) { launch.notify(e.message ?: "Couldn't download the book") }
                    }
                }
            },
            modifier = Modifier.testTag(if (b.id in downloads.done) "bookRemoveDownload" else "bookDownload"),
        ) {
            when {
                downloads.fetching == b.id -> Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (downloads.of > 0) downloads.fetched.toFloat() / downloads.of else 0f },
                        modifier = Modifier.size(26.dp),
                        color = Accent,
                        strokeWidth = 2.5.dp,
                    )
                    Icon(Icons.Rounded.DownloadForOffline, "Downloading", tint = Accent.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
                b.id in downloads.done -> Icon(Icons.Rounded.DownloadForOffline, "On this phone", tint = Accent, modifier = Modifier.size(28.dp))
                else -> Icon(Icons.Outlined.ArrowCircleDown, "Download", Modifier.size(28.dp))
            }
        }
        // Finished, or not: ✓ marks it finished, ✕ takes that back. On a podcast it's the episode you're on.
        val episodeId = position?.episodeId
        val canMark = !b.podcast || episodeId != null
        val isFinished = if (b.podcast) position?.finished == true else progress?.finished == true
        if (canMark) {
            IconButton(
                onClick = {
                    if (b.podcast && episodeId != null) graph.bookPlayer.markEpisodeFinished(b, episodeId, position.totalSec, finished = !isFinished)
                    else graph.bookPlayer.markFinished(b, finished = !isFinished)
                },
                modifier = Modifier.testTag("bookFinished"),
            ) {
                if (isFinished) Icon(Icons.Rounded.Close, if (b.podcast) "Mark the episode not finished" else "Mark not finished", Modifier.size(28.dp))
                else Icon(Icons.Rounded.Check, if (b.podcast) "Mark the episode finished" else "Mark finished", Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        val now = playing.book?.id == b.id && playing.playing
        BigPlayButton(enabled = true, playing = now) {
            val started = position != null && !position.finished
            graph.bookPlayer.playOrToggle(b, if (started) position.currentSec else 0.0)
            AppLog.i("Books", "${if (now) "Pause" else if (started) "Continue" else "Play"} \"${b.title}\" from its page")
        }
    }
    if (removing) RemoveDialog(b.title, onDismiss = { removing = false }) { graph.bookDownloads.remove(b.id) }
}

/**
 * One episode in a podcast's list: tap it to play. Its own description from Audiobookshelf (the guests, what it's
 * about; issue #630) unfolds under it from the arrow on the right, so the list stays short until you want it.
 */
@Composable
private fun EpisodeRow(e: app.siansa.net.Episode, onPlay: () -> Unit) {
    val about = remember(e.id) { episodeAbout(e) }
    var open by rememberSaveable(e.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("episode-${e.id}")) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = Space.Page, end = if (about != null) Space.Tight else Space.Page, top = Space.Small, bottom = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(episodeLine(e), formatDuration(e.durationSec.toLong())).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (about != null) {
                IconButton(onClick = { open = !open }, modifier = Modifier.testTag("episodeAbout-${e.id}")) {
                    Icon(
                        if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        if (open) "Hide what the episode is about" else "What the episode is about",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (open && about != null) {
            Text(
                about,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, bottom = Space.Medium).testTag("episodeText-${e.id}"),
            )
        }
    }
}

/** An episode's own description as plain text, its paragraphs kept and blank runs squeezed; null when there's none. */
internal fun episodeAbout(e: app.siansa.net.Episode): String? =
    e.description?.let(::stripHtml)
        ?.lines()?.map { it.trim() }
        ?.joinToString("\n")
        ?.replace(Regex("\n{3,}"), "\n\n")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private enum class ChapterState { HEARD, NOW, AHEAD }

@Composable
private fun ChapterRow(c: Chapter, state: ChapterState, onClick: () -> Unit) {
    val color = when (state) {
        ChapterState.NOW -> Accent
        ChapterState.HEARD -> MaterialTheme.colorScheme.onSurfaceVariant
        ChapterState.AHEAD -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Small).testTag("chapter-${c.index}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Text("${c.index + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        Text(c.title, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = if (state == ChapterState.NOW) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatDuration(c.durationSec.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "S2 E14 · 12 Oct 2026", as much as the episode has. */
internal fun episodeLine(e: app.siansa.net.Episode): String = listOfNotNull(
    // Only what the episode has: Audiobookshelf sends empty seasons and numbers, which showed as "S E" (#626).
    listOfNotNull(e.season?.trim()?.takeIf { it.isNotEmpty() }?.let { "S$it" }, e.number?.trim()?.takeIf { it.isNotEmpty() }?.let { "E$it" }).joinToString(" ").takeIf { it.isNotBlank() },
    e.publishedAt.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")) },
).joinToString(" · ")

/** Audiobookshelf descriptions can carry HTML; the words only. */
internal fun stripHtml(text: String): String =
    text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        // Paragraphs and list items keep their breaks: show notes are mostly <p> and <li> (issue #630).
        .replace(Regex("</p>\\s*", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
        .replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()
