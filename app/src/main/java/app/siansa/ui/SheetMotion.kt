package app.siansa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Now playing and the book player as one motion (#600): a sheet whose [progress] runs from 0 (closed, below the
 * screen) to 1 (open). The finger drives it in both directions, dragging the player down or the mini player up,
 * and letting go hands the finger's speed to a spring, so a hard swipe closes fast and a gentle one settles.
 * While it moves, the mini player fades and shrinks away underneath and the sheet's own content fades in, so
 * one becomes the other instead of one sliding over the other.
 */
class PlayerSheet(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)) {
    /** 0 closed … 1 open; what the screen draws. */
    val progress = Animatable(0f)

    /** Where it's headed: open or closed, whatever [progress] is right now. */
    var isOpen by mutableStateOf(false)
        private set

    /** The screen's height, so a speed in px/s can be turned into progress. */
    var heightPx: Float = 1f

    /** Whether there's anything to draw: open, or still on its way out. */
    val isVisible: Boolean get() = isOpen || progress.value > 0f

    fun open(velocityPxPerSec: Float = 0f) { isOpen = true; settleTo(1f, velocityPxPerSec) }
    fun close(velocityPxPerSec: Float = 0f) { isOpen = false; settleTo(0f, velocityPxPerSec) }

    /** The finger moved [dyPx] (down is positive): the sheet follows it. */
    fun dragBy(dyPx: Float) {
        val next = (progress.value - dyPx / heightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        scope.launch { progress.snapTo(next) }
    }

    /** The finger let go at [velocityPxPerSec] (down is positive): opens or closes, carrying that speed on. */
    fun settle(velocityPxPerSec: Float) {
        if (sheetShouldOpen(isOpen, progress.value, velocityPxPerSec)) open(velocityPxPerSec) else close(velocityPxPerSec)
    }

    private fun settleTo(target: Float, velocityPxPerSec: Float) {
        val initial = -velocityPxPerSec / heightPx.coerceAtLeast(1f)
        scope.launch { progress.animateTo(target, SheetSpring, initialVelocity = initial) }
    }
}

/** How fast a swipe has to be to decide on its own, in px/s. */
const val SHEET_FLING_PX_PER_SEC = 1400f

/** A soft spring, nearly critically damped: quick, no bounce, and it takes the finger's speed. */
val SheetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 450f)

/**
 * Whether the sheet opens when the finger lets go: a fast swipe decides by its direction; otherwise it goes
 * on past a point: an open sheet closes once dragged down more than 22 %, a closed one opens once dragged up
 * more than 22 %, so a short pull springs back where it came from.
 */
fun sheetShouldOpen(wasOpen: Boolean, progress: Float, velocityPxPerSec: Float): Boolean = when {
    velocityPxPerSec < -SHEET_FLING_PX_PER_SEC -> true
    velocityPxPerSec > SHEET_FLING_PX_PER_SEC -> false
    wasOpen -> progress > 0.78f
    else -> progress > 0.22f
}

/** How far into the sheet's travel the mini player has faded out (0 at rest, 1 gone). */
fun miniFade(progress: Float): Float = (progress / 0.35f).coerceIn(0f, 1f)

/** The mini player's opacity as the sheet comes up. */
fun miniAlpha(progress: Float): Float = 1f - miniFade(progress)

/** The mini player shrinks a little (to 94 %) as it fades, so it recedes rather than switches off. */
fun miniScale(progress: Float): Float = 1f - 0.06f * miniFade(progress)

/** The sheet's content fades in over the first 30 % of its travel, so it isn't a flat card sliding up. */
fun sheetContentAlpha(progress: Float): Float = (progress / 0.3f).coerceIn(0f, 1f)

/** The sheet itself starts a little see-through and is solid once its content is in. */
fun sheetAlpha(progress: Float): Float = 0.6f + 0.4f * sheetContentAlpha(progress)

/** The sheet's top corners: square when it fills the screen, rounding (up to [maxPx]) as it leaves the top. */
fun sheetCornerPx(progress: Float, heightPx: Float, maxPx: Float): Float = playerCardCornerPx((1f - progress) * heightPx, maxPx)
