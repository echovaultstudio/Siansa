package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.AppSettings
import app.siansa.data.SyncPhase
import app.siansa.log.AppLog
import app.siansa.mix.Notice
import app.siansa.mix.NoticeKind
import app.siansa.mix.Notices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The notices at the top of Home (issue #784): a row of chips for what needs you, what you're partway through and
 * what's new. A tap opens it; the × hides that one. Nothing shows when there's nothing to say.
 */
@Composable
fun NoticesRow(nav: NavController, graph: AppGraph, settings: AppSettings) {
    val sync by graph.sync.progress.collectAsStateWithLifecycle()
    val failed = sync.phase == SyncPhase.FAILED
    var notices by remember { mutableStateOf<List<Notice>>(emptyList()) }
    LaunchedEffect(failed, settings.booksReady, settings.notices) {
        notices = withContext(Dispatchers.IO) {
            val candidates = noticeCandidates(graph, settings, failed)
            Notices.order(candidates, settings.notices, graph.notices.taps(), graph.notices.hidden())
        }
        AppLog.d("Home", "Notices: ${notices.joinToString { it.key }.ifEmpty { "none" }}")
    }
    NoticeChips(
        notices,
        onOpen = { notice ->
            AppLog.i("Home", "Notice opened: ${notice.kind.id}")
            graph.notices.tap(notice.kind)
            nav.navigate(notice.route)
        },
        onHide = { notice ->
            AppLog.i("Home", "Notice hidden: ${notice.key}")
            graph.notices.hide(notice.key)
            notices = notices - notice
        },
    )
}

/** The chips themselves, one gap from the section above (Home's section gap); nothing when [notices] is empty. */
@Composable
internal fun NoticeChips(notices: List<Notice>, onOpen: (Notice) -> Unit, onHide: (Notice) -> Unit) {
    if (notices.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(top = LocalSectionGap.current).testTag("noticesRow"),
        contentPadding = PaddingValues(horizontal = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        items(notices, key = { it.key }) { notice ->
            AssistChip(
                onClick = { onOpen(notice) },
                label = { Text(notice.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(noticeIcon(notice.kind), null, Modifier.size(18.dp)) },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Hide",
                        modifier = Modifier.size(18.dp).clickable(onClickLabel = "Hide this notice") { onHide(notice) },
                    )
                },
                modifier = Modifier.widthIn(max = NOTICE_MAX_WIDTH).testTag("notice-${notice.kind.id}"),
            )
        }
    }
}

/** A long book title is cut off rather than making one chip fill the screen. */
internal val NOTICE_MAX_WIDTH = 280.dp

private fun noticeIcon(kind: NoticeKind): ImageVector = when (kind) {
    NoticeKind.NEEDS_YOU -> Icons.Rounded.Warning
    NoticeKind.CARRY_ON -> Icons.Rounded.PlayArrow
    NoticeKind.NEW_ALBUMS -> Icons.Rounded.Album
    NoticeKind.YOUR_MONTH -> Icons.Rounded.BarChart
    NoticeKind.NEW_ARTISTS -> Icons.Rounded.Person
}

/** Every notice there is something to say about now; [Notices.order] picks and orders them. Looks only at what's on the phone, except the books in progress. */
private suspend fun noticeCandidates(graph: AppGraph, settings: AppSettings, syncFailed: Boolean): List<Notice> = buildList {
    val today = LocalDate.now()
    if (syncFailed) add(Notices.needsYou())
    if (settings.booksReady) {
        try {
            val progress = graph.books.progress.value
            val now = System.currentTimeMillis()
            graph.books.inProgress()
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
        val newest = graph.library.albums("newest").take(20)
        newest.firstOrNull()?.let { first -> Notices.newAlbums(Notices.newAlbumCount(newest.map { it.created }, today), first.id)?.let(::add) }
    }
    Notices.yourMonth(today)?.let(::add)
    Notices.newArtists(graph.suggestions.savedToday().size, today)?.let(::add)
}
