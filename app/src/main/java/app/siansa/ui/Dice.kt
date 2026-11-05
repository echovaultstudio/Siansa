package app.siansa.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * The dice on Home (issue #347) rolls when tapped: a quick tumble over two axes, a gentle tumble while the song is
 * fetched, then it lands on a face with a small bounce. With animations off (Developer options, or Remove
 * animations in Accessibility) it only fades to its new face.
 */

/** Where the pips of a die's [face] (1–6) sit, each as (x, y) from 0 to 1 across the face. */
internal fun dicePips(face: Int): List<Pair<Float, Float>> {
    val lo = PIP_LO
    val mid = 0.5f
    val hi = 1f - PIP_LO
    return when (face) {
        1 -> listOf(mid to mid)
        2 -> listOf(lo to lo, hi to hi)
        3 -> listOf(lo to lo, mid to mid, hi to hi)
        4 -> listOf(lo to lo, hi to lo, lo to hi, hi to hi)
        5 -> listOf(lo to lo, hi to lo, mid to mid, lo to hi, hi to hi)
        6 -> listOf(lo to lo, hi to lo, lo to mid, hi to mid, lo to hi, hi to hi)
        else -> throw IllegalArgumentException("A die has faces 1 to 6, not $face")
    }
}

private const val PIP_LO = 0.27f

/** Another face than [current], at random, so every turn of the tumble shows a change. */
internal fun nextFace(current: Int, random: Random = Random): Int {
    val pick = random.nextInt(1, 6)
    return if (pick >= current) pick + 1 else pick
}

/** The next angle at or after [degrees] where the die lies flat again (a multiple of [step]). */
internal fun landingAngle(degrees: Float, step: Float): Float = ceil(degrees / step) * step

/** Whether the phone's animations are off (animator duration scale 0): then the dice only fades. */
internal fun animationsOff(animatorScale: Float): Boolean = animatorScale == 0f

/** How long the quick tumble after a tap takes. */
private const val QUICK_MS = 500L

/**
 * A die drawn in [tint], its face's pips cut out, so any face can show. [rolls] goes up on each tap: the die
 * tumbles, keeps tumbling gently while [rolling], and lands with a bounce (and a light tick) when it's done.
 */
@Composable
internal fun RollingDie(rolls: Int, rolling: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduced = remember { animationsOff(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)) }
    val stillRolling by rememberUpdatedState(rolling)
    var face by remember { mutableIntStateOf(5) }
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val rz = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(rolls) {
        if (rolls == 0) return@LaunchedEffect
        if (reduced) {
            // Animations off: fade to the new face, stay faint while the song is fetched, then back.
            fade.animateTo(0f, tween(90))
            face = nextFace(face)
            fade.animateTo(if (stillRolling) 0.5f else 1f, tween(90))
            snapshotFlow { stillRolling }.first { !it }
            fade.animateTo(1f, tween(150))
            return@LaunchedEffect
        }
        val x0 = rx.value
        val y0 = ry.value
        val z0 = rz.value
        val start = withFrameMillis { it }
        var edge = 0
        while (true) {
            val t = withFrameMillis { it } - start
            val quick = (t / QUICK_MS.toFloat()).coerceAtMost(1f)
            val eased = FastOutSlowInEasing.transform(quick)
            // Seconds of the gentle tumble while the song is fetched.
            val slow = (t - QUICK_MS).coerceAtLeast(0L) / 1000f
            val x = eased * 720f + slow * 220f
            rx.snapTo(x0 + x)
            ry.snapTo(y0 + eased * 360f + slow * 140f)
            rz.snapTo(z0 + eased * 90f + slow * 45f)
            // Up into the air and down again on the quick tumble.
            scale.snapTo(1f + 0.18f * sin(PI * quick).toFloat())
            // A new face each time the die is edge-on, so the change isn't seen.
            val passed = ((x + 90f) / 180f).toInt()
            if (passed != edge) {
                edge = passed
                face = nextFace(face)
            }
            if (quick >= 1f && !stillRolling) break
        }
        // Lands flat, the nearest way ahead, with a small bounce.
        val land = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        coroutineScope {
            launch { rx.animateTo(landingAngle(rx.value, 180f), land) }
            launch { ry.animateTo(landingAngle(ry.value, 180f), land) }
            launch { rz.animateTo(landingAngle(rz.value, 90f), land) }
            launch {
                scale.snapTo(0.88f)
                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            }
        }
    }

    Canvas(
        modifier
            .size(24.dp)
            .graphicsLayer {
                rotationX = rx.value
                rotationY = ry.value
                rotationZ = rz.value
                scaleX = scale.value
                scaleY = scale.value
                cameraDistance = 10f * density
                // A face turned away catches less light.
                val facing = abs(cos(Math.toRadians(rx.value.toDouble())) * cos(Math.toRadians(ry.value.toDouble()))).toFloat()
                alpha = fade.value * (0.55f + 0.45f * facing)
                // The pips are cut out of the die, so they show what's behind it.
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        // The die as Material's dice icon draws it: 18 of 24, rounded corners.
        val side = size.minDimension * 0.75f
        val corner = size.minDimension * 0.11f
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        drawRoundRect(tint, topLeft, Size(side, side), CornerRadius(corner, corner))
        val pip = side * 0.095f
        dicePips(face).forEach { (px, py) ->
            drawCircle(Color.Black, pip, Offset(topLeft.x + px * side, topLeft.y + py * side), blendMode = BlendMode.Clear)
        }
    }
}
