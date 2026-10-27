package app.siansa.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Book
import app.siansa.data.bookLength
import app.siansa.log.AppLog
import app.siansa.playback.BookPlayerState
import kotlinx.coroutines.launch
import kotlin.math.max

/** The book player's colours, the same as now playing's: white on the cover's colours, the accent for buttons. */
@Composable
private fun bookPlayerColors() = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    surface = Color.Transparent,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
)

/**
 * The audiobook player (issue #521), in the same style as the music's now playing (#542): a sheet that slides up
 * over the page, the cover blurred into the background, the chapter at the top, a large cover, the title with a
 * bookmark button, the chapter's seek bar, the controls (chapter back, skip back, play, skip forward, chapter
 * next), and the speed, where you are in the whole book and the sleep timer underneath. The ⋮ menu has the rest.
 * Dragging down closes it, like now playing.
 */
@Composable
fun BookPlayerScreen(nav: NavController, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val state by graph.bookPlayer.state.collectAsStateWithLifecycle()
    val book = state.book
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val launch = rememberPlayLauncher()
    val close by rememberUpdatedState(onClose)

    fun openBookPage() {
        val id = book?.id ?: return
        onClose()
        nav.navigate("book/$id")
    }

    // Dragging down moves the whole player with the finger; let go far or fast enough and it closes (as now playing).
    var dismissY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val cornerMaxPx = with(LocalDensity.current) { 28.dp.toPx() }
    fun settle(speed: Float) {
        if (dismissY > heightPx * 0.22f || (dismissY > 40f && speed > 1400f)) {
            close()
        } else if (dismissY > 0f) {
            val from = dismissY
            scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dismissY = v } }
        }
    }
    val dismissOnPull = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissY > 0f && available.y < 0f) {
                    val used = max(available.y, -dismissY)
                    dismissY += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dismissY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissY > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    MaterialTheme(colorScheme = bookPlayerColors(), typography = MaterialTheme.typography) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    translationY = dismissY
                    val corner = playerCardCornerPx(dismissY, cornerMaxPx)
                    shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                    clip = true
                }
                .background(Color.Black)
                .nestedScroll(dismissOnPull)
                .testTag("bookPlayer"),
        ) {
            // The cover, enlarged and blurred, as the background (a darker plain version before Android 12).
            Cover(
                book?.cover,
                300,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = if (Build.VERSION.SDK_INT >= 31) 0.85f else 0.45f }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(70.dp) else Modifier),
                RectangleShape,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.55f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    )
                )
            )

            val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageHeight = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier
                            .height(pageHeight)
                            .statusBarsPadding()
                            .padding(horizontal = Space.Section)
                            .padding(bottom = navBar),
                    ) {
                        Spacer(Modifier.height(playerTopGapDp(LocalConfiguration.current.screenHeightDp).dp))
                        BookTopRow(state, onClose = onClose, onBook = ::openBookPage, onMenu = { menuOpen = true })

                        Box(Modifier.weight(1f).fillMaxWidth().padding(top = Space.Page, bottom = Space.Medium), contentAlignment = Alignment.Center) {
                            Cover(book?.cover, LARGE_COVER, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).clickable(enabled = book != null, onClick = ::openBookPage), RoundedCornerShape(10.dp))
                        }

                        BookTitleRow(book) { graph.bookPlayer.bookmark { launch.notify(it) } }
                        BookSeekBar(state)
                        BookControls(state, settings.bookSkipBackSec, settings.bookSkipForwardSec)
                        // The speed bottom left, where you are in the whole book in the middle, sleep bottom right.
                        Row(Modifier.fillMaxWidth().padding(top = Space.Medium, bottom = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(SIDE_WIDTH), contentAlignment = Alignment.Center) {
                                SpeedButton(state.speed) { graph.bookPlayer.setSpeed(nextSpeed(state.speed)) }
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (book != null) Text(
                                    "${bookLength(state.positionSec)} of ${bookLength(book.durationSec.toDouble())} · ${bookLength((book.durationSec - state.positionSec).coerceAtLeast(0.0))} left",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("bookWhole"),
                                )
                            }
                            Box(Modifier.width(SIDE_WIDTH), contentAlignment = Alignment.Center) {
                                SleepButton(on = graph.bookPlayer.sleepAtChapterEnd) { graph.bookPlayer.toggleSleepAtChapterEnd() }
                            }
                        }
                    }
                }
            }

            if (menuOpen && book != null) {
                BookMenu(
                    book = book,
                    onDismiss = { menuOpen = false },
                    onOpenBook = ::openBookPage,
                    notify = { launch.notify(it) },
                )
            }
        }
    }
}

/** ˅ to close, the chapter in the middle (opens the book's page), ⋮ on the right. */
@Composable
private fun BookTopRow(state: BookPlayerState, onClose: () -> Unit, onBook: () -> Unit, onMenu: () -> Unit) {
    val book = state.book
    val chapter = state.chapter
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("bookClose")) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
        Column(Modifier.weight(1f).clickable(enabled = book != null, onClick = onBook), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (chapter != null && book != null) "CHAPTER ${chapter.index + 1} OF ${book.chapters.size}" else "PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                chapter?.title?.ifBlank { null } ?: book?.title.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMenu, enabled = book != null, modifier = Modifier.testTag("bookMenu")) { Icon(Icons.Rounded.MoreVert, "Book options") }
    }
}

/** The title and who wrote and reads it, with a bookmark button where now playing has the heart. */
@Composable
private fun BookTitleRow(book: Book?, onBookmark: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                book?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                book?.let { b -> listOfNotNull(b.author.takeIf { it.isNotBlank() }, b.narrator.takeIf { it.isNotBlank() }?.let { "read by $it" }).joinToString(" · ") }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onBookmark, enabled = book != null, modifier = Modifier.testTag("bookBookmark")) {
            Icon(Icons.Rounded.BookmarkBorder, "Bookmark here", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * The chapter's bar in now playing's style: a thin line with a small round thumb, amber for what's played. Dragging
 * shows where you'd land and seeks when let go. Without chapters it's the whole book.
 */
@Composable
private fun BookSeekBar(state: BookPlayerState) {
    val graph = LocalGraph.current
    val (start, length, inChapter) = chapterWindow(state)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: (inChapter / length).toFloat().coerceIn(0f, 1f)
    fun seek(to: Float) {
        AppLog.d("BookPlayer", "Seek to ${(start + to * length).toInt()} s")
        graph.bookPlayer.seekTo(start + to * length)
    }
    val thumb by animateDpAsState(if (dragging != null) 16.dp else 11.dp, tween(100), label = "thumb")
    Column(Modifier.padding(top = Space.Medium)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .testTag("bookChapterBar")
                .pointerInput(start, length) {
                    detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(start, length) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { seek(it) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val width = maxWidth
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.width(width * fraction).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
            Box(
                Modifier
                    .offset(x = (width * fraction - thumb / 2).coerceIn(0.dp, width - thumb))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDuration((fraction * length).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "-" + formatDuration((length - fraction * length).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The controls, laid out as now playing's: a small button at each end (the chapter before and after), the skips
 * either side of the big play button.
 */
@Composable
internal fun BookControls(state: BookPlayerState, backSec: Int, forwardSec: Int) {
    val graph = LocalGraph.current
    val chapters = state.book?.chapters?.isNotEmpty() == true
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.Small).testTag("bookControls"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideButton(Icons.Rounded.SkipPrevious, "Previous chapter", enabled = chapters, tag = "bookPrevChapter") { graph.bookPlayer.previousChapter() }
        SkipButton(back = true, seconds = backSec, tag = "bookSkipBack") { graph.bookPlayer.skipBack() }
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable { graph.bookPlayer.togglePlay() }
                .testTag("bookPlayPause"),
            contentAlignment = Alignment.Center,
        ) {
            if (state.buffering && !state.playing) CircularProgressIndicator(Modifier.size(28.dp), color = OnAccent, strokeWidth = 2.dp)
            else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = OnAccent, modifier = Modifier.size(38.dp))
        }
        SkipButton(back = false, seconds = forwardSec, tag = "bookSkipForward") { graph.bookPlayer.skipForward() }
        SideButton(Icons.Rounded.SkipNext, "Next chapter", enabled = chapters, tag = "bookNextChapter") { graph.bookPlayer.nextChapter() }
    }
}

/** The chapter buttons at the row's ends: the size of now playing's repeat and add buttons. */
@Composable
private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(32.dp))
    }
}

/** ↺ or ↻ with the seconds it skips (Settings › Playback › Books), the size of now playing's previous and next. */
@Composable
internal fun SkipButton(back: Boolean, seconds: Int, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (back) Icons.Rounded.Replay10 else Icons.Rounded.Forward30, if (back) "Back $seconds seconds" else "Forward $seconds seconds", Modifier.size(30.dp), tint = Color.White)
            Text("$seconds", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** The speed, bottom left, in the style of the lyrics button: lit when it isn't 1×. Tap for the next step. */
@Composable
private fun SpeedButton(speed: Float, onClick: () -> Unit) {
    val on = speed != 1f
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Speed ${speedLabel(speed)}", onClick = onClick).testTag("bookSpeed"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Speed, null, tint = if (on) Accent else Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
            Text(speedLabel(speed), style = MaterialTheme.typography.labelSmall, color = if (on) Accent else Color.White.copy(alpha = 0.9f), maxLines = 1)
        }
    }
}

/** The moon, bottom right, in the style of now playing's sleep timer: lit while the book stops at the chapter's end. */
@Composable
private fun SleepButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = if (on) "Sleep at the end of the chapter: on" else "Sleep timer", onClick = onClick).testTag("bookSleep"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Bedtime, null, tint = if (on) Accent else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(if (on) 18.dp else 22.dp))
            if (on) Text("chapter", style = MaterialTheme.typography.labelSmall, color = Accent, maxLines = 1)
        }
    }
}

/** The ⋮ menu, a sheet like now playing's: the book at the top, then what you can do with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookMenu(book: Book, onDismiss: () -> Unit, onOpenBook: () -> Unit, notify: (String) -> Unit) {
    val graph = LocalGraph.current
    val progress by graph.books.progress.collectAsStateWithLifecycle()
    val finished = progress[book.id]?.finished == true
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    SiansaDarkTheme {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.Small)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Tight), verticalAlignment = Alignment.CenterVertically) {
                    Cover(book.cover, 150, Modifier.size(48.dp), RoundedCornerShape(4.dp))
                    Spacer(Modifier.width(Space.Medium))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(Modifier.padding(top = Space.Medium, bottom = Space.Tight))
                MenuRow(Icons.Rounded.Bookmark, "Bookmark here") {
                    graph.bookPlayer.bookmark(notify)
                    onDismiss()
                }
                MenuRow(Icons.Rounded.Bedtime, "Sleep at the end of the chapter", tag = if (graph.bookPlayer.sleepAtChapterEnd) "on" else null) {
                    graph.bookPlayer.toggleSleepAtChapterEnd()
                    onDismiss()
                }
                MenuRow(Icons.Rounded.Speed, "Speed ${speedLabel(graph.bookPlayer.state.value.speed)} · tap for the next") {
                    graph.bookPlayer.setSpeed(nextSpeed(graph.bookPlayer.state.value.speed))
                }
                MenuRow(Icons.Rounded.Check, if (finished) "Mark not finished" else "Mark finished") {
                    graph.bookPlayer.markFinished(book, finished = !finished)
                    onDismiss()
                }
                MenuRow(Icons.Rounded.MenuBook, "Open the book's page") {
                    onDismiss()
                    onOpenBook()
                }
            }
        }
    }
}

/**
 * What the seek bar spans: the chapter's start in the book, its length and how far into it the book is (clamped);
 * without chapters, the whole book. A length of at least a second, so a bar is never divided by zero.
 */
internal fun chapterWindow(state: BookPlayerState): Triple<Double, Double, Double> {
    val book = state.book
    val chapter = state.chapter
    val start = chapter?.startSec ?: 0.0
    val end = chapter?.endSec ?: (book?.durationSec?.toDouble() ?: 0.0)
    val length = (end - start).coerceAtLeast(1.0)
    return Triple(start, length, (state.positionSec - start).coerceIn(0.0, length))
}

/** The speeds the button steps through (issue #521). */
internal val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 0.5f)

internal fun nextSpeed(speed: Float): Float {
    val i = SPEEDS.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
    return SPEEDS[(i + 1) % SPEEDS.size]
}

internal fun speedLabel(speed: Float): String = (if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "%.2f".format(speed).trimEnd('0').trimEnd('.')) + "×"

/** The width of the buttons' slots at the bottom row's ends, as in now playing. */
private val SIDE_WIDTH = 72.dp

/**
 * The mini player while a book is loaded and no music is (issue #521): the cover, the chapter, ↺ play/pause ↻.
 * Tap it to open the book player.
 */
@Composable
internal fun BookMiniPlayer(state: BookPlayerState, onOpen: () -> Unit) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val book = state.book ?: return
    val tone = rememberCoverColor(book.cover)
    val background = androidx.compose.ui.graphics.lerp(tone, Color.Black, 0.45f)
    Box(Modifier.padding(horizontal = Space.Small, vertical = Space.Tight)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(background).clickable(onClick = onOpen).padding(Space.Small).testTag("bookMiniPlayer"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(book.cover, SMALL_COVER, Modifier.size(44.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.width(Space.Medium))
            Column(Modifier.weight(1f)) {
                Text(book.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.chapter?.let { "Chapter ${it.index + 1} · ${it.title}" } ?: book.author, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { graph.bookPlayer.skipBack() }, modifier = Modifier.testTag("bookMiniBack")) { Icon(Icons.Rounded.Replay10, "Back ${settings.bookSkipBackSec} seconds", tint = Color.White) }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { graph.bookPlayer.togglePlay() }
                    .testTag("bookMiniPlay"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", tint = Color.White)
            }
            IconButton(onClick = { graph.bookPlayer.skipForward() }, modifier = Modifier.testTag("bookMiniForward")) { Icon(Icons.Rounded.Forward30, "Forward ${settings.bookSkipForwardSec} seconds", tint = Color.White) }
        }
    }
}
