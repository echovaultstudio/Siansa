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
}

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
