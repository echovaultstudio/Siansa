package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
fun BooksCarousel(nav: NavController, podcasts: Boolean) {
    val graph = LocalGraph.current
    val progress by graph.books.progress.collectAsStateWithLifecycle()
    val shelves by loadState("book-shelves-$podcasts") {
        graph.books.refreshProgress()
        graph.books.shelves(podcasts)
    }
    val cards = booksForCarousel(shelves.valueOrNull().orEmpty().flatMap { shelf -> shelf.books.map { shelf.id to it } }, progress)
    if (cards.isEmpty()) return
    val pager = rememberPagerState { cards.size }
    Column(Modifier.fillMaxWidth().padding(bottom = Space.Page).testTag(if (podcasts) "homePodcasts" else "homeBooks")) {
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
                    onClick = { graph.bookPlayer.play(book, progress.currentSec); AppLog.i("Books", "Continue \"${book.title}\" from Home") },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.Small).size(40.dp).testTag("bookContinue"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = OnAccent),
                ) { Icon(Icons.Rounded.PlayArrow, "Continue ${book.title}", Modifier.size(24.dp)) }
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

/** A book's page (issue #521): cover, who made it, Continue or Play, progress, description and chapters. */
@Composable
fun BookScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val progressAll by graph.books.progress.collectAsStateWithLifecycle()
    val page by loadState(id) { graph.books.book(id) }
    val book = page.valueOrNull()
    val progress = progressAll[id]
    val playing by graph.bookPlayer.state.collectAsStateWithLifecycle()
    Screen(title = book?.title ?: "Book", nav = nav) { padding ->
        LoadBox(page, Modifier.padding(padding)) { b ->
            val current = if (playing.book?.id == b.id) playing.positionSec else progress?.currentSec ?: 0.0
            val chapterNow = b.chapterAt(current)
            // Loaded here: a list's content block isn't composable (issue #521).
            val episodes by loadState("episodes-${b.id}") { if (b.podcast) graph.audiobookshelf.episodes(b.id) else emptyList() }
            val bookmarks by loadState("bookmarks-${b.id}") { runCatching { graph.audiobookshelf.bookmarks(b.id) }.getOrDefault(emptyList()) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
                item {
                    Row(Modifier.fillMaxWidth().padding(Space.Page), verticalAlignment = Alignment.Bottom) {
                        Cover(b.cover, LARGE_COVER, Modifier.size(width = 128.dp, height = 184.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(Space.Page))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            b.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(Space.Small))
                            val by = listOfNotNull(
                                b.author.takeIf { it.isNotBlank() }?.let { "by $it" },
                                b.narrator.takeIf { it.isNotBlank() }?.let { "read by $it" },
                                listOfNotNull(b.series?.let { s -> s + (b.sequence?.let { ", book $it" } ?: "") }, b.year?.toString()).joinToString(" · ").takeIf { it.isNotBlank() },
                            )
                            by.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = Space.Page), horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                        val started = progress?.started == true && progress.finished != true
                        Button(
                            onClick = {
                                graph.bookPlayer.play(b, if (started) current else 0.0)
                                AppLog.i("Books", "${if (started) "Continue" else "Play"} \"${b.title}\" from its page")
                                nav.navigate("bookplayer")
                            },
                            modifier = Modifier.testTag("bookPlay"),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(Space.Tight))
                            Text(if (started) "Continue" + (chapterNow?.let { " · chapter ${it.index + 1}" } ?: "") else "Play")
                        }
                        OutlinedButton(
                            onClick = { graph.bookPlayer.markFinished(b, finished = progress?.finished != true) },
                            modifier = Modifier.testTag("bookFinished"),
                        ) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(Modifier.width(Space.Tight))
                            Text(if (progress?.finished == true) "Finished" else "Mark finished")
                        }
                    }
                }
                item {
                    // The whole book on the phone, or not (issue #521).
                    val downloads by graph.bookDownloads.state.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()
                    val launch = rememberPlayLauncher()
                    Row(Modifier.padding(horizontal = Space.Page, vertical = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            downloads.fetching == b.id -> {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Downloading, file ${downloads.fetched} of ${downloads.of}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            b.id in downloads.done -> OutlinedButton(onClick = { graph.bookDownloads.remove(b.id) }, modifier = Modifier.testTag("bookRemoveDownload")) {
                                Icon(Icons.Rounded.DownloadDone, null); Spacer(Modifier.width(Space.Tight)); Text("On this phone · remove")
                            }
                            else -> OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try { graph.bookDownloads.download(b) } catch (e: Exception) { launch.notify(e.message ?: "Couldn't download the book") }
                                    }
                                },
                                modifier = Modifier.testTag("bookDownload"),
                            ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(Space.Tight)); Text("Download") }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Medium)) {
                        val fraction = if (b.durationSec > 0) (current / b.durationSec).toFloat().coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Spacer(Modifier.height(Space.Small))
                        Text(
                            if (current > 0) "${bookLength(current)} of ${bookLength(b.durationSec.toDouble())} · ${(fraction * 100).toInt()} % · ${bookLength(b.durationSec - current)} left"
                            else bookLength(b.durationSec.toDouble()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                b.description?.takeIf { it.isNotBlank() }?.let { text ->
                    item { Text(stripHtml(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Space.Page), maxLines = 8, overflow = TextOverflow.Ellipsis) }
                }
                if (b.podcast) {
                    // A podcast's episodes, newest first (issue #521); tap one to play it.
                    val list = episodes.valueOrNull().orEmpty()
                    if (list.isNotEmpty()) {
                        item { SectionTitle("${list.size} episodes") }
                        items(list.size, key = { "ep-${list[it].id}" }) { i ->
                            val e = list[i]
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    graph.bookPlayer.playEpisode(b, e)
                                    AppLog.i("Books", "Play episode \"${e.title}\" of \"${b.title}\"")
                                    nav.navigate("bookplayer")
                                }.padding(horizontal = Space.Page, vertical = Space.Small).testTag("episode-${e.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.Medium),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(episodeLine(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatDuration(e.durationSec.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Modifier.fillMaxWidth().clickable { graph.bookPlayer.play(b, m.timeSec); nav.navigate("bookplayer") }.padding(horizontal = Space.Page, vertical = Space.Small).testTag("bookmark-$i"),
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
                            nav.navigate("bookplayer")
                        }
                    }
                }
            }
        }
    }
}

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
    listOfNotNull(e.season?.let { "S$it" }, e.number?.let { "E$it" }).joinToString(" ").takeIf { it.isNotBlank() },
    e.publishedAt.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")) },
).joinToString(" · ")

/** Audiobookshelf descriptions can carry HTML; the words only. */
internal fun stripHtml(text: String): String =
    text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()
