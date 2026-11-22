package app.siansa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.siansa.R
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pull Home down to load it again (the owner, 22 Nov: "a pull down to refresh on home page, with an animated Siansa
 * logo and the header until refresh is done"): the page follows the finger, the Siansa logo grows into the space above
 * it, and while Home loads again the page stays down with the logo's half circles turning in that space, until
 * [refreshing] ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomePullToRefresh(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    val header = with(LocalDensity.current) { PULL_HEADER.toPx() }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().testTag("homePull"),
        state = state,
        indicator = { PullLogo(state, refreshing, Modifier.align(Alignment.TopCenter)) },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = header * pulledDown(refreshing, state.distanceFraction) }, content = content)
    }
}

/** How far down the page sits, as a share of the logo's space: with the finger while pulling, all of it while loading. */
internal fun pulledDown(refreshing: Boolean, fraction: Float): Float = if (refreshing) 1f else fraction.coerceIn(0f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullLogo(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    // The half circles go round the bars with the finger while pulling (the owner, 22 Nov: "when pulling down the echo
    // waves should move around also in same speed as the swipe"), carry on from exactly where the finger left them, turning
    // like a record while Home loads (as the TV's logo does while a DJ mix plays, at 33⅓), then wind on to the next half
    // turn, where they look as at rest.
    val turn = remember { Animatable(0f) }
    // Where the finger left the half circles, kept as the layer is placed each frame, so the loading turn carries on
    // from exactly there in the very frame Home starts loading (the owner, 22 Nov: "the echo bars jump back instead
    // of continuing exactly from where user slid to": the turn used to start a frame late, from where the pull had
    // sprung back to by then).
    val held = remember { HeldTurn() }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            held.carried = true
            while (true) turn.animateTo(turn.value + FULL_TURN, tween(RECORD_TURN_MS, easing = LinearEasing))
        } else if (held.carried) {
            turn.animateTo(restingTurn(held.angle + turn.value) - held.angle, tween(RECORD_STOP_MS, easing = FastOutSlowInEasing))
            held.angle = 0f
            held.carried = false
            turn.snapTo(0f)
        }
    }
    // The pull locks where it's far enough to load Home again (the owner, 22 Nov: "you can over extend the swipe. I
    // would rather it lock in place with a short vibrate"): one short buzz as it gets there, each time it does.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { pullLocked(state.distanceFraction) }.distinctUntilChanged().collect { locked ->
            if (locked && !held.carried) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // Nothing here is read while composing: the pull and the turn are read where the layers are placed, so a pull or a
    // turning logo moves two layers on the GPU and nothing is composed, measured or drawn again (the owner: "no lag or
    // heaviness for the app"). The half circles are drawn once and their layer turned.
    Box(
        modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = Space.Small).size(PULL_HEADER)
            .semantics { contentDescription = if (refreshing) "Loading Home again" else "Pull to load Home again" }
            .testTag(if (refreshing) "homePullTurning" else "homePullLogo"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(PULL_LOGO).graphicsLayer {
                // Only ever in the room the page has given it (the owner, 22 Nov: "the logo appears in the notice
                // pills"): as big as that room, in its middle, and seen once there's enough of it.
                val fraction = pulledDown(refreshing, state.distanceFraction)
                val grow = logoSize(fraction)
                scaleX = grow
                scaleY = grow
                alpha = logoShown(fraction)
                translationY = -PULL_HEADER.toPx() * (1f - fraction) / 2f
            },
        ) {
            Image(painterResource(R.drawable.logo_bars), contentDescription = null, modifier = Modifier.matchParentSize())
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    // With the finger while pulling; from there on, the loading turn on top of where it was left.
                    if (!refreshing && !held.carried) held.angle = pulledTurn(state.distanceFraction)
                    rotationZ = held.angle + turn.value
                },
            ) {
                val unit = size.width / LOGO_VIEW
                PULL_RINGS.forEach { drawRing(it, unit) }
            }
        }
    }
}

/** Where a pull left the half circles, and whether the loading turn is carrying on from there. Not state: read and
 * written only as the layer is placed. */
private class HeldTurn(var angle: Float = 0f, var carried: Boolean = false)

/**
 * How far the half circles have gone round for a pull of [fraction] of the way: half a turn for a whole pull, about
 * what a wheel their size would roll under the finger, and no further however far the finger goes on (the owner,
 * 22 Nov: they lock there, so the loading turn always carries on from the same place). Half a turn looks as none does
 * (both sides are alike), so nothing jumps when the loading turn takes over or hands back.
 */
internal fun pulledTurn(fraction: Float): Float = HALF_TURN * fraction.coerceIn(0f, 1f)

/** Whether a pull of [fraction] of the way has locked: far enough that letting go loads Home again. */
internal fun pullLocked(fraction: Float): Boolean = fraction >= 1f

/** The logo's size as a share of its own, for a page pulled [fraction] of the way: never taller than the room given. */
internal fun logoSize(fraction: Float): Float = fraction.coerceIn(0f, 1f)

/** How clearly the logo shows: not at all until there's room for it ([LOGO_FROM]), fully from [LOGO_FULL]. */
internal fun logoShown(fraction: Float): Float = ((fraction - LOGO_FROM) / (LOGO_FULL - LOGO_FROM)).coerceIn(0f, 1f)

/** Where the half circles come to rest: the next half turn, where they look as they do at rest (both sides alike). */
internal fun restingTurn(turned: Float): Float = kotlin.math.ceil(turned / HALF_TURN) * HALF_TURN

/** One pair of the logo's half circles, as the launcher icon draws them (the TV's logo has the same). */
private class Ring(val radius: Float, val sweep: Float, val width: Float, val end: Color, val mid: Color)

private val PULL_RINGS = listOf(
    Ring(radius = 22.8f, sweep = 95f, width = 2.6f, end = Color(0x4DAFC2D3), mid = Color(0x8CAFC2D3)),
    Ring(radius = 31.2f, sweep = 94f, width = 2.2f, end = Color(0x22AFC2D3), mid = Color(0x3DAFC2D3)),
)

private fun DrawScope.drawRing(ring: Ring, unit: Float) {
    val r = ring.radius * unit
    val middle = LOGO_MIDDLE * unit
    val top = Offset(middle - r, middle - r)
    val box = Size(2 * r, 2 * r)
    val brush = Brush.verticalGradient(listOf(ring.end, ring.mid, ring.end), startY = middle - r, endY = middle + r)
    val stroke = Stroke(width = ring.width * unit, cap = StrokeCap.Round)
    drawArc(brush, 180f - ring.sweep / 2, ring.sweep, useCenter = false, topLeft = top, size = box, style = stroke)
    drawArc(brush, -ring.sweep / 2, ring.sweep, useCenter = false, topLeft = top, size = box, style = stroke)
}

private val PULL_HEADER = 72.dp
private val PULL_LOGO = 96.dp // the launcher drawing has room round it: its mark is two thirds of this
private const val LOGO_FROM = 0.35f
private const val LOGO_FULL = 0.85f
private const val LOGO_VIEW = 108f
private const val LOGO_MIDDLE = 54f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val RECORD_TURN_MS = 1_800
private const val RECORD_STOP_MS = 700
