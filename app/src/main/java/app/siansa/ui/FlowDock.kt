package app.siansa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Flow's bar, where the mini player is (the owner, 15 Nov: one bar, not a Play button over the player). While the Flow
 * page is open and no Flow mix plays, a pill in the mood's colour stands in the mini player's place, also when other
 * music is playing: "Play this vibe" and what's set. Pressing it starts the mix, and the pill's round ends fold into
 * the mini player's corners as its colour gives way to the player under it. The Flow page says what it shows here.
 */
internal object FlowDock {
    /** The pill is wanted: the Flow page is open and no Flow mix plays. */
    var shown by mutableStateOf(false)
    /** The mood's colour and the ink on it; read as the pill is drawn, so a drag of the pad's dot only redraws it. */
    var colour by mutableStateOf(Color(0xFF6E5DC4))
    var ink by mutableStateOf(Color.White)
    /** What's set, under "Play this vibe". */
    var summary by mutableStateOf("")
    /** Starts the mix. */
    var onPlay: () -> Unit = {}

    /** How long the fold takes. */
    const val FOLD_MS = 420

    /** The pill's corner (its ends are round) and the mini player's, which it folds to. */
    val ROUND = 33.dp
    val FOLDED = 10.dp
    /** The pill is as tall as the mini player: its row and the line of its progress. */
    val HEIGHT = 66.dp

    /** The bar's corner [fold] of the way from the pill (0) to the mini player (1). */
    fun corner(fold: Float): Float = ROUND.value + (FOLDED.value - ROUND.value) * fold.coerceIn(0f, 1f)

    /** How much of the pill shows [fold] of the way: all of it at first, gone before the corners have settled. */
    fun pill(fold: Float): Float = (1f - fold.coerceIn(0f, 1f) / 0.8f).coerceIn(0f, 1f)
}

/** A rounded outline inside the mini player's own margin, so the bar is clipped where the player's card is. */
private class BarShape(val corner: Float, val sidePx: Float, val topPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(sidePx, topPx, size.width - sidePx, size.height - topPx, CornerRadius(corner * density.density)))
}

/**
 * The mini player's place in the bottom bar: [mini] (the player, when [hasMini]) or Flow's pill over it. Only the
 * corners, the pill's see-through and what's in it change as it folds: nothing moves or is measured again, and the
 * numbers are read where the bar is drawn, so the fold costs a redraw of this bar a frame and nothing else.
 */
@Composable
internal fun FlowDockSlot(hasMini: Boolean, making: Boolean, mini: @Composable () -> Unit) {
    val waits = FlowDock.shown
    val fold = remember { Animatable(if (waits) 0f else 1f) }
    LaunchedEffect(waits) { fold.animateTo(if (waits) 0f else 1f, tween(FlowDock.FOLD_MS, easing = FastOutSlowInEasing)) }
    val pillThere by remember { derivedStateOf { fold.value < 1f } }
    val miniThere by remember { derivedStateOf { fold.value > 0f } }
    val density = LocalDensity.current
    val side = with(density) { Space.Small.toPx() }
    val top = with(density) { Space.Tight.toPx() }
    Box(
        Modifier.graphicsLayer {
            // Clipped only while it folds: the player under the pill mustn't show past the pill's round ends.
            clip = fold.value < 1f
            shape = BarShape(FlowDock.corner(fold.value), side, top)
        },
    ) {
        if (hasMini && (!waits || miniThere)) mini()
        if (waits || pillThere) {
            FlowPill(
                FlowDock.ink, FlowDock.summary, making,
                Modifier
                    .padding(horizontal = Space.Small, vertical = Space.Tight)
                    .graphicsLayer { alpha = FlowDock.pill(fold.value) }
                    .drawBehind { drawRect(FlowDock.colour) }
                    .clickable(enabled = waits && !making, onClickLabel = "Play this vibe", role = Role.Button) { FlowDock.onPlay() },
            )
        }
    }
}

/**
 * What's in Flow's pill, laid out as the mini player is: the stars where the cover goes, "Play this vibe" over
 * [summary] where the song is, and Play where the buttons are. Its colour and shape come with [modifier].
 */
@Composable
internal fun FlowPill(ink: Color, summary: String, making: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(FlowDock.HEIGHT).padding(horizontal = Space.Small).testTag("vibeGenerate"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(DscvrStars, null, Modifier.size(22.dp), tint = ink)
        }
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(if (making) "Making your vibe…" else "Play this vibe", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("vibeSummary"))
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (making) CircularProgressIndicator(Modifier.size(22.dp), color = ink, strokeWidth = 2.dp) // spacing: exact
            else Icon(Icons.Rounded.PlayArrow, null, tint = ink)
        }
    }
}
