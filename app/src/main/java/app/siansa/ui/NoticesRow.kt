package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.AppGraph
import app.siansa.data.AppSettings
import app.siansa.data.SyncPhase
import app.siansa.log.AppLog
import androidx.compose.material.icons.rounded.Favorite
import app.siansa.mix.Notice
import app.siansa.mix.NoticeKind
import app.siansa.mix.Notices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The notices at the top of Home (issue #784): chips for what needs you, what you're partway through and what's new.
 * Pinned kinds always show; the automatic ones come and go as they're needed (issue #794). None while [shown] is off
 * (Settings › Notices and Cards › Notices).
 */
@Composable
internal fun rememberNotices(graph: AppGraph, settings: AppSettings, shown: Boolean): List<Notice> {
    val sync by graph.sync.progress.collectAsStateWithLifecycle()
    val failed = sync.phase == SyncPhase.FAILED
    // From the last shown, so the row doesn't vanish and come back each time Home is opened.
    var notices by remember { mutableStateOf(if (shown) graph.notices.shown else emptyList()) }
    // Worked out again when the library changes (issue #922): on a cold start Home opened before the library had
    // loaded, so New in your library only showed after going to another page and back.
    val library by graph.library.version.collectAsStateWithLifecycle()
    LaunchedEffect(failed, settings.booksReady, settings.notices, shown, library) {
        val fresh = if (!shown) emptyList() else withContext(Dispatchers.IO) {
            val candidates = noticeCandidates(graph, settings, failed)
            Notices.order(candidates, settings.notices, graph.notices.taps())
        }
        if (fresh != notices) AppLog.d("Home", "Notices: ${fresh.joinToString { it.key }.ifEmpty { "none" }}")
        notices = fresh
        graph.notices.shown = fresh
    }
    return notices
}

/**
 * The top of Home (issue #872): the notice chips, then Home's menu button ([menuButton]) in a circle exactly a chip's
 * height, in line with them, a page margin from the right edge. A chip's height is measured from a chip's own
 * content, so the circle matches at any text size, with or without notices. Chips that don't fit scroll, and blend
 * out under a fade just before the circle, as Your Library's chips do.
 */
@Composable
internal fun HomeTopBar(notices: List<Notice>, onOpen: (Notice) -> Unit, modifier: Modifier = Modifier, menuButton: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val surface = MaterialTheme.colorScheme.surface
    Layout(
        modifier = modifier.fillMaxWidth().testTag("homeTopBar"),
        content = {
            // A chip's content, never shown: its height is the circle's.
            NoticePillContent(Icons.Rounded.Album, "Ag")
            Row(
                Modifier.horizontalScroll(scroll).padding(start = Space.Page, end = Space.Small).testTag("noticesRow"),
                horizontalArrangement = Arrangement.spacedBy(Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same pill as Your Library's chips (issue #794), not Material's outlined assist chip.
                notices.forEach { notice -> androidx.compose.runtime.key(notice.key) { NoticePill(notice, onOpen = { onOpen(notice) }) } }
            }
            Box(
                Modifier
                    .graphicsLayer { alpha = if (scroll.canScrollForward) 1f else 0f }
                    .background(Brush.horizontalGradient(0f to surface.copy(alpha = 0f), 0.7f to surface, 1f to surface))
                    .testTag("noticesFade"),
            )
            // Passes on the exact size: the button is a chip's height whatever its own default.
            Box(propagateMinConstraints = true) { menuButton() }
        },
    ) { measurables, constraints ->
        val chip = measurables[0].measure(Constraints()).height
        val width = constraints.maxWidth
        val page = Space.Page.roundToPx()
        // The chips' room ends at the circle's left edge; the circle has its own space after it.
        val room = (width - page - chip).coerceAtLeast(0)
        val chips = measurables[1].measure(Constraints(maxWidth = room, minHeight = chip, maxHeight = chip))
        val fadeWidth = NOTICES_FADE.roundToPx().coerceAtMost(room)
        val fade = measurables[2].measure(Constraints.fixed(fadeWidth, chip))
        val button = measurables[3].measure(Constraints.fixed(chip, chip))
        layout(width, chip) {
            chips.place(0, 0)
            fade.place(room - fadeWidth, 0)
            button.place(width - page - chip, 0)
        }
    }
}

/** How wide the fade before Home's menu button is. */
private val NOTICES_FADE = 48.dp

/** A long book title is cut off rather than making one chip fill the screen. */
internal val NOTICE_MAX_WIDTH = 280.dp

/** One notice as a pill in Your Library's style: an icon and the text. */
@Composable
private fun NoticePill(notice: Notice, onOpen: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onOpen,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.widthIn(max = NOTICE_MAX_WIDTH).testTag("notice-${notice.kind.id}"),
    ) {
        NoticePillContent(noticeIcon(notice.kind), notice.text)
    }
}

/** What's inside a notice pill; also measured, never shown, for the height of Home's menu button. */
@Composable
private fun NoticePillContent(icon: ImageVector, text: String) {
    Row(
        Modifier.padding(horizontal = Space.Medium, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Accent)
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = PILL_FONT, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

internal fun noticeIcon(kind: NoticeKind): ImageVector = when (kind) {
    NoticeKind.NEEDS_YOU -> Icons.Rounded.Warning
    NoticeKind.CARRY_ON -> Icons.Rounded.PlayArrow
    NoticeKind.NEW_ALBUMS -> Icons.Rounded.Album
    NoticeKind.YOUR_MONTH -> Icons.Rounded.BarChart
    NoticeKind.NEW_ARTISTS -> Icons.Rounded.Person
    NoticeKind.LIKED -> Icons.Rounded.Favorite
}

/** Every notice there is something to say about now; [Notices.order] picks and orders them. Looks only at what's on the phone, except the books in progress. */
private suspend fun noticeCandidates(graph: AppGraph, settings: AppSettings, syncFailed: Boolean): List<Notice> = buildList {
    val today = LocalDate.now()
    if (syncFailed) add(Notices.needsYou())
    if (settings.booksReady) {
        try {
            // Progress is only loaded by the Books screens otherwise: without this, Carry on missed on a cold start.
            // Both reads are kept for a minute, so scrolling past doesn't ask the server again.
            graph.books.refreshProgress()
            val progress = graph.books.progress.value
            val now = System.currentTimeMillis()
            graph.books.inProgressFresh(now)
                .mapNotNull { book -> progress[book.id]?.takeIf { Notices.recentlyPlayed(it.updatedAt, now) }?.let { book to it.updatedAt } }
                .maxByOrNull { it.second }
                ?.let { (book, _) -> add(Notices.carryOn(book.id, book.title)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Home", "Notices: couldn't load books in progress: ${e.message}")
        }
    }
    if (graph.library.isSynced) {
        val newest = graph.library.albums("newest").take(Notices.NEW_ALBUM_LOOK)
        newest.firstOrNull()?.let { first -> Notices.newAlbums(Notices.newAlbumCount(newest.map { it.created }, today), first.id)?.let(::add) }
    }
    Notices.yourMonth(today)?.let(::add)
    Notices.newArtists(graph.suggestions.savedToday().size, today)?.let(::add)
    if (settings.notices.modeOf(NoticeKind.LIKED) != app.siansa.mix.NoticeMode.OFF) add(Notices.liked())
}
