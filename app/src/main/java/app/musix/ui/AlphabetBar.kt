package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width the list leaves free on its right for the bar. */
val AlphabetBarWidth = 26.dp

/**
 * The A to Z bar on the right of a long list sorted by name. Tap a letter, or drag along the bar, to
 * jump there; a big letter shows next to your finger while you do. Letters with nothing in them are
 * dimmed (picking one goes to the next letter that has something), and [current] (the letter at the top
 * of the list) is highlighted.
 */
@Composable
fun AlphabetBar(present: Set<Char>, current: Char?, onPick: (Char) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    val letters = AlphabetIndex.LETTERS
    var touching by remember { mutableStateOf<Char?>(null) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val bubble = 64.dp
    val bubblePx = with(LocalDensity.current) { bubble.roundToPx() }
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    fun touch(y: Float) {
        if (height <= 0) return
        touchY = y.coerceIn(0f, height.toFloat())
        val letter = letters[(touchY / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != touching) {
            touching = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pick(letter)
        }
    }

    Box(modifier.semantics { contentDescription = "Jump to letter" }) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(AlphabetBarWidth)
                .clip(RoundedCornerShape(50))
                .background(if (touching != null) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                .onSizeChanged { height = it.height }
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
                        touching = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val lit = touching ?: current
            letters.forEach { l ->
                Text(
                    l.toString(),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (l == lit) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = when {
                        l == lit -> Accent
                        l in present -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                )
            }
        }
        touching?.let { l ->
            // A teardrop pointing at the bar, level with your finger.
            // Placed outside the bar without taking any room, so the bar never shifts.
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints.fixed(bubblePx, bubblePx))
                        layout(0, 0) {
                            p.place(-(bubblePx + gapPx), (touchY - bubblePx / 2f).toInt().coerceIn(0, (height - bubblePx).coerceAtLeast(0)))
                        }
                    }
                    .background(Accent, RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8, bottomStartPercent = 50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    l.toString(),
                    color = OnAccent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
