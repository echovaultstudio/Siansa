package app.siansa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.siansa.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Pull Home down to load it again (the owner, 22 Nov: "a pull down to refresh on home page, with an animated Siansa
 * logo and the header"): the page follows the finger, the Siansa logo grows into the space above it, and on letting go
 * the page stays down with the logo's half circles turning in that space for a moment, then goes back up.
 *
 * The turn is its own length, not the loading's (the owner, 24 Nov: "needs to be smoother and never hang. Let stuff
 * work in background but always show the animation"): [onRefresh] starts the loading and nothing here waits for it.
 * The pull is Siansa's own, not Material's, which took no pull while its mark was on the way back up ("sometimes I
 * swipe but it doesn't show the icon until I swipe a few times"): here a new pull takes the page over wherever it is,
 * and a quick flick down loads Home as a whole pull does.
 */
@Composable
internal fun HomePullToRefresh(onRefresh: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val header = with(density) { PULL_HEADER.toPx() }
    val pull = remember(density) { HomePull(lock = header / PULL_DRAG, flick = with(density) { PULL_FLICK.toPx() }, scope) }
    pull.onRefresh = onRefresh
    Box(Modifier.fillMaxSize().nestedScroll(pull.connection).testTag("homePull")) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = header * pull.fraction }, content = content)
        PullLogo(pull, Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Where Home's pull is: [fraction] of the way down (0 at rest, 1 locked), and whether the logo is [turning]. The
 * finger moves it through [connection], from what Home's list leaves over at its top; [lock] is how far the finger
 * goes for a whole pull and [flick] how fast a flick down has to be, both in pixels.
 */
internal class HomePull(private val lock: Float, private val flick: Float, private val scope: CoroutineScope) {
    var fraction by mutableFloatStateOf(0f)
        private set
    var turning by mutableStateOf(false)
        private set
    var onRefresh: () -> Unit = {}
    private var settling: Job? = null

    val connection = object : NestedScrollConnection {
        // Back up with the finger before the list scrolls.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (turning || source != NestedScrollSource.UserInput || available.y >= 0f || fraction <= 0f) return Offset.Zero
            settling?.cancel()
            val before = fraction
            fraction = pulledBy(before, available.y, lock)
            return Offset(0f, (fraction - before) * lock)
        }

        // Down with the finger, with what the list at its top has no use for. All of it is taken, so nothing stretches.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (turning || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            settling?.cancel()
            fraction = pulledBy(fraction, available.y, lock)
            return Offset(0f, available.y)
        }

        // Let go: load Home again when pulled far enough or flicked, back up otherwise.
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (turning || fraction <= 0f) return Velocity.Zero
            if (pullLoads(fraction, available.y, flick)) load() else back()
            return available
        }
    }

    private fun load() {
        turning = true
        settling?.cancel()
        settling = scope.launch {
            try {
                animate(fraction, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> fraction = value }
                // Only now, with the page settled and the logo turning: loading composes Home again, and that never
                // lands in the frames where the finger hands over.
                onRefresh()
                delay(PULL_TURN_MS)
            } finally {
                turning = false
            }
            animate(fraction, 0f, animationSpec = tween(PULL_BACK_MS, easing = FastOutSlowInEasing)) { value, _ -> fraction = value }
        }
    }

    private fun back() {
        settling?.cancel()
        settling = scope.launch {
            animate(fraction, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> fraction = value }
        }
    }
}

/** Where a pull at [fraction] is after the finger moves [dy] pixels (down is more): never above rest, never past the lock. */
internal fun pulledBy(fraction: Float, dy: Float, lock: Float): Float = (fraction + dy / lock).coerceIn(0f, 1f)

/**
 * Whether letting go loads Home again: pulled all the way, or flicked down (at [velocity], at least [flick]) with the
 * logo already showing, so a short quick swipe does it too.
 */
internal fun pullLoads(fraction: Float, velocity: Float, flick: Float): Boolean =
    pullLocked(fraction) || (fraction >= FLICK_FROM && velocity >= flick)

@Composable
private fun PullLogo(pull: HomePull, modifier: Modifier) {
    val refreshing = pull.turning
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
    LaunchedEffect(pull) {
        snapshotFlow { pullLocked(pull.fraction) }.distinctUntilChanged().collect { locked ->
            if (locked && !pull.turning) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                val fraction = pull.fraction
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
                    if (!pull.turning && !held.carried) held.angle = pulledTurn(pull.fraction)
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

/** How much of the finger's way the page goes: it follows at a little over half, so a whole pull is a thumb's reach. */
private const val PULL_DRAG = 0.6f

/** How fast a flick down is, a second. */
private val PULL_FLICK = 700.dp

/** How far down a flick has to have got: where the logo shows clearly. */
private const val FLICK_FROM = 0.35f

/** How long the logo turns, whatever the loading takes: two thirds of a turn of its 33⅓. */
private const val PULL_TURN_MS = 1_200L
private const val PULL_BACK_MS = 320
private val PULL_LOGO = 96.dp // the launcher drawing has room round it: its mark is two thirds of this
private const val LOGO_FROM = 0.12f
private const val LOGO_FULL = 0.6f
private const val LOGO_VIEW = 108f
private const val LOGO_MIDDLE = 54f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val RECORD_TURN_MS = 1_800
private const val RECORD_STOP_MS = 700
