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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.siansa.data.bookLength
import app.siansa.playback.BookPlayerState

/**
 * The audiobook player (issue #521), its own page apart from the music's now playing: no shuffle, repeat, queue
 * or crossfade. Skip back and forward by the amounts in Settings, chapter previous and next, the chapter's
 * progress bar with the book's total under the controls, speed per book, a sleep timer to the chapter's end.
 */
@Composable
fun BookPlayerScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val state by graph.bookPlayer.state.collectAsStateWithLifecycle()
    val book = state.book
    Screen(title = book?.title ?: "Book", nav = nav) { padding ->
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Nothing is playing", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Screen
        }
        val chapter = state.chapter
        val chapterStart = chapter?.startSec ?: 0.0
        val chapterEnd = chapter?.endSec ?: book.durationSec.toDouble()
        val inChapter = (state.positionSec - chapterStart).coerceIn(0.0, (chapterEnd - chapterStart).coerceAtLeast(1.0))
        var dragging by remember { mutableStateOf<Float?>(null) }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.Page),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.Section))
            Cover(book.cover, LARGE_COVER, Modifier.size(220.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.height(Space.Section))
            if (chapter != null) {
                Text("CHAPTER ${chapter.index + 1} OF ${book.chapters.size}", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Space.Tight))
            }
            Text(chapter?.title ?: book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(book.author.takeIf { it.isNotBlank() }, book.narrator.takeIf { it.isNotBlank() }?.let { "read by $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.Section))
            // The chapter's bar; dragging shows where you'd land and seeks when let go.
            val length = (chapterEnd - chapterStart).coerceAtLeast(1.0)
            val shown = dragging ?: (inChapter / length).toFloat()
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { graph.bookPlayer.seekTo(chapterStart + it * length) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                modifier = Modifier.fillMaxWidth().testTag("bookChapterBar"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val at = shown * length
                Text(formatDuration(at.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (chapter != null) "chapter · ${formatDuration(length.toLong())}" else bookLength(book.durationSec.toDouble()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("−" + formatDuration((length - at).toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.Page))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                IconButton(onClick = { graph.bookPlayer.previousChapter() }, enabled = book.chapters.isNotEmpty(), modifier = Modifier.testTag("bookPrevChapter")) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous chapter", Modifier.size(30.dp))
                }
                SkipButton(back = true, seconds = settings.bookSkipBackSec, tag = "bookSkipBack") { graph.bookPlayer.skipBack() }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Accent).clickable { graph.bookPlayer.togglePlay() }.testTag("bookPlayPause"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.buffering && !state.playing) CircularProgressIndicator(Modifier.size(28.dp), color = OnAccent, strokeWidth = 2.dp)
                    else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(36.dp), tint = OnAccent)
                }
                SkipButton(back = false, seconds = settings.bookSkipForwardSec, tag = "bookSkipForward") { graph.bookPlayer.skipForward() }
                IconButton(onClick = { graph.bookPlayer.nextChapter() }, enabled = book.chapters.isNotEmpty(), modifier = Modifier.testTag("bookNextChapter")) {
                    Icon(Icons.Rounded.SkipNext, "Next chapter", Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(Space.Section))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                SpeedChip(state.speed) { graph.bookPlayer.setSpeed(it) }
                val sleep by graph.sleepTimer.state.collectAsStateWithLifecycle()
                FilterChip(
                    selected = graph.bookPlayer.sleepAtChapterEnd,
                    onClick = { graph.bookPlayer.toggleSleepAtChapterEnd() },
                    label = { Text(if (graph.bookPlayer.sleepAtChapterEnd) "Sleep · end of chapter" else "Sleep") },
                    leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("bookSleep"),
                )
            }
            Spacer(Modifier.height(Space.Section))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Book: ${bookLength(state.positionSec)} of ${bookLength(book.durationSec.toDouble())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${bookLength((book.durationSec - state.positionSec).coerceAtLeast(0.0))} left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(bottomBarPadding(Space.Section)))
        }
    }
}

/** ↺ or ↻ with the seconds it skips, from Settings › Playback › Books. */
@Composable
internal fun SkipButton(back: Boolean, seconds: Int, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (back) Icons.Rounded.Replay10 else Icons.Rounded.Forward30, if (back) "Back $seconds seconds" else "Forward $seconds seconds", Modifier.size(22.dp))
            Text("$seconds", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** The speed, cycling through the usual steps on tap. */
@Composable
private fun SpeedChip(speed: Float, onPick: (Float) -> Unit) {
    FilterChip(
        selected = speed != 1f,
        onClick = { onPick(nextSpeed(speed)) },
        label = { Text(speedLabel(speed)) },
        modifier = Modifier.testTag("bookSpeed"),
    )
}

/** The speeds the chip steps through (issue #521). */
internal val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 0.5f)

internal fun nextSpeed(speed: Float): Float {
    val i = SPEEDS.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
    return SPEEDS[(i + 1) % SPEEDS.size]
}

internal fun speedLabel(speed: Float): String = (if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "%.2f".format(speed).trimEnd('0').trimEnd('.')) + "×"

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
            IconButton(onClick = { graph.bookPlayer.togglePlay() }, modifier = Modifier.testTag("bookMiniPlay")) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", tint = Color.White)
            }
            IconButton(onClick = { graph.bookPlayer.skipForward() }, modifier = Modifier.testTag("bookMiniForward")) { Icon(Icons.Rounded.Forward30, "Forward ${settings.bookSkipForwardSec} seconds", tint = Color.White) }
        }
    }
}
