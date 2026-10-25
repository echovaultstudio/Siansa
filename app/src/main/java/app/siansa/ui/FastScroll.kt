package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.siansa.data.ListSort
import app.siansa.data.Song
import app.siansa.data.SongDates
import app.siansa.data.isoMillis
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Where the quick-scroll handle goes, for long lists that aren't sorted by name (issue #374). Lists sorted
 * by name get the A to Z bar instead ([AlphabetBar]).
 */
object FastScroll {
    /** The position to jump to for a touch [fraction] (0 at the top, 1 at the bottom) of a list of [count]. */
    fun indexAt(fraction: Float, count: Int): Int =
        if (count <= 0) 0 else (fraction.coerceIn(0f, 1f) * (count - 1)).roundToInt()

    /**
     * Where the handle sits for a list of [count] showing [first] at the top and about [visible] on screen:
     * at the bottom once the last ones are in view.
     */
    fun fractionOf(first: Int, count: Int, visible: Int): Float {
        val room = count - visible.coerceAtLeast(1)
        return if (room <= 0) 0f else (first.toFloat() / room).coerceIn(0f, 1f)
    }

    /** The month a date falls in, e.g. "Mar 2025", or "This month"; [never] for no date. */
    fun monthLabel(millis: Long, never: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (millis <= 0) return never
        val month = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))
        if (month == YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))) return "This month"
        return month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.year
    }

    fun playsLabel(plays: Long): String = when {
        plays <= 0 -> "Not played"
        plays == 1L -> "1 play"
        else -> "$plays plays"
    }

    /** Whether [names] are in A to Z order (ignoring case and accents), so a letter says where you are. */
    fun inLetterOrder(names: List<String>): Boolean {
        if (names.size < 2) return false
        val letters = names.map { AlphabetIndex.LETTERS.indexOf(AlphabetIndex.letterOf(it)) }
        return letters.zipWithNext().all { (a, b) -> a <= b } && letters.toSet().size >= 3
    }

    /**
     * The label for an item named by [names] at [index] of [count]: its letter when the list runs A to Z by one
     * of the names given (an album's artist, then its title), else its position.
     */
    fun nameLabel(index: Int, count: Int, letterBy: List<String>?): String =
        letterBy?.getOrNull(index)?.let { AlphabetIndex.letterOf(it).toString() } ?: positionLabel(index, count)

    /** "12 of 840": where you are in a list that has no better label. */
    fun positionLabel(index: Int, count: Int): String = "${index + 1} of $count"

    /**
     * What the handle says for a song in a list sorted by [sort] (beta testers #24): its letter, or the month it
     * was added or last played. Null for the list's own order, which the page labels itself.
     */
    fun songLabel(sort: ListSort, song: Song, dates: SongDates?, heardAt: Long?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String? =
        when (sort) {
            ListSort.TITLE -> AlphabetIndex.letterOf(song.title).toString()
            ListSort.ARTIST -> AlphabetIndex.letterOf(song.artist).toString()
            ListSort.ADDED -> monthLabel(isoMillis(dates?.created) ?: 0L, "No date", now, zone)
            ListSort.PLAYED -> monthLabel(listOfNotNull(isoMillis(dates?.played), heardAt).maxOrNull() ?: 0L, "Not played", now, zone)
            ListSort.DEFAULT -> null
        }

    /**
     * Each item's position in a list that starts with [headers] rows and has a heading row before the items in
     * [headed] (an album's disc headings).
     */
    fun positions(count: Int, headers: Int = 1, headed: Set<Int> = emptySet()): IntArray {
        val out = IntArray(count.coerceAtLeast(0))
        var at = headers
        for (i in out.indices) {
            if (i in headed) at++
            out[i] = at++
        }
        return out
    }

    /** The item at list [position] (or the one whose heading it is) in [positions]; 0 above the first. */
    fun itemAt(positions: IntArray, position: Int): Int {
        if (positions.isEmpty()) return 0
        var lo = 0
        var hi = positions.size - 1
        if (position <= positions[0]) return 0
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (positions[mid] <= position) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/**
 * The quick-scroll handle for any long list or grid (beta testers #24), with a label for where you are.
 * [count] items sit at the list positions [positions]; [firstVisible], [visible] and [scrollTo] work in list
 * positions, [labelAt] by item. Lists shorter than [AlphabetIndex.MIN_ITEMS] don't need it: callers check [showFastScroll].
 */
@Composable
fun ListFastScroll(
    count: Int,
    positions: IntArray,
    firstVisible: () -> Int,
    visible: () -> Int,
    scrollTo: (Int) -> Unit,
    labelAt: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val first by rememberUpdatedState(firstVisible)
    val shown by rememberUpdatedState(visible)
    val at by rememberUpdatedState(positions)
    val fraction by remember(count) {
        derivedStateOf { FastScroll.fractionOf(FastScroll.itemAt(at, first()), count, shown()) }
    }
    var last by remember { mutableIntStateOf(-1) }
    FastScrollBar(
        fraction = fraction,
        labelAt = { f -> labelAt(FastScroll.indexAt(f, count)) },
        onScroll = { f ->
            val i = FastScroll.indexAt(f, count)
            if (i != last) {
                last = i
                at.getOrNull(i)?.let(scrollTo)
            }
        },
        modifier = modifier,
    )
}

/** Whether a list of [count] gets the quick-scroll handle. */
fun showFastScroll(count: Int): Boolean = count >= AlphabetIndex.MIN_ITEMS

/**
 * A handle on the right of a long list: drag it, or touch anywhere along the bar, to move through the
 * whole list at once. While you hold it, a label next to your finger says where you are (a month, a play
 * count). Takes the same room as the A to Z bar, so the list doesn't move when the sort changes.
 */
@Composable
fun FastScrollBar(fraction: Float, labelAt: (Float) -> String?, onScroll: (Float) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scroll by rememberUpdatedState(onScroll)
    val label by rememberUpdatedState(labelAt)
    var touchY by remember { mutableFloatStateOf(-1f) }
    var shown by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val thumbPx = with(density) { THUMB_HEIGHT.roundToPx() }
    val gapPx = with(density) { Space.Small.roundToPx() }
    val touching = touchY >= 0f

    fun touch(y: Float) {
        if (height <= 0) return
        touchY = y.coerceIn(0f, height.toFloat())
        val f = touchY / height
        val now = label(f)
        if (now != shown) {
            shown = now
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        scroll(f)
    }

    Box(
        modifier
            .fillMaxHeight()
            .width(AlphabetBarWidth)
            .onSizeChanged { height = it.height }
            .semantics { contentDescription = "Scroll quickly" }
            .testTag("fastScroll")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    touch(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        touch(change.position.y)
                    }
                    touchY = -1f
                    shown = null
                }
            },
    ) {
        val place = if (touching) touchY / height.coerceAtLeast(1) else fraction
        // The track: a faint line, so it's clear there's something to grab.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(2.dp)  // spacing: exact (hairline track)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(50)),
        )
        // The handle, at the part of the list on screen.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed(with(density) { THUMB_WIDTH.roundToPx() }, thumbPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val y = (place * (constraints.maxHeight - thumbPx)).roundToInt().coerceAtLeast(0)
                        p.place((constraints.maxWidth - p.width) / 2, y)
                    }
                }
                .clip(RoundedCornerShape(50))
                .background(if (touching) Accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                .testTag("fastScrollThumb"),
        )
        shown?.let { text ->
            // A label pointing at the bar, level with your finger, placed outside the bar without taking room.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints())
                        layout(0, 0) {
                            p.place(-(p.width + gapPx), (touchY - p.height / 2f).toInt().coerceIn(0, (height - p.height).coerceAtLeast(0)))
                        }
                    }
                    .background(Accent, RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8, bottomStartPercent = 50))
                    .padding(horizontal = Space.Page, vertical = Space.Medium),
            ) {
                Text(text, color = OnAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private val THUMB_HEIGHT = 44.dp
private val THUMB_WIDTH = 6.dp
