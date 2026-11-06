package app.siansa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.hypot

/** What a slide from the play button does (issue #919). */
enum class PlaySlide(val label: String) {
    DJ_MIX("DJ mix"),
    STEADY("Steady radio"),
    SONG_PATH("Song path"),
    SONIC_MATCH("Sonic match"),
    START_A_MIX("Start a mix…"),
    NOTHING("Nothing");

    val icon: ImageVector?
        get() = when (this) {
            DJ_MIX -> SiansaIcons.Turntable
            STEADY -> Icons.Rounded.Waves
            SONG_PATH -> Icons.AutoMirrored.Rounded.AltRoute
            SONIC_MATCH -> Icons.Rounded.GraphicEq
            START_A_MIX -> Icons.Rounded.Radio
            NOTHING -> null
        }

    companion object {
        fun parse(name: String?): PlaySlide? = entries.firstOrNull { it.name == name }
    }
}

/** The four ways a finger can slide from the play button. */
enum class SlideWay(val label: String) { UP("up"), DOWN("down"), LEFT("left"), RIGHT("right") }

/**
 * Sliding from the play button (issue #919): on unless switched off under Settings › Style › Now playing, and what each
 * way does. Up for a DJ mix, down for a Steady radio, left for a song path, right for songs that sound like it.
 */
data class PlaySlides(
    val on: Boolean = true,
    val up: PlaySlide = PlaySlide.DJ_MIX,
    val down: PlaySlide = PlaySlide.STEADY,
    val left: PlaySlide = PlaySlide.SONG_PATH,
    val right: PlaySlide = PlaySlide.SONIC_MATCH,
    /** The play button gets record grooves while a DJ mix plays. */
    val grooves: Boolean = true,
) {
    fun of(way: SlideWay): PlaySlide = when (way) {
        SlideWay.UP -> up
        SlideWay.DOWN -> down
        SlideWay.LEFT -> left
        SlideWay.RIGHT -> right
    }

    fun with(way: SlideWay, slide: PlaySlide): PlaySlides = when (way) {
        SlideWay.UP -> copy(up = slide)
        SlideWay.DOWN -> copy(down = slide)
        SlideWay.LEFT -> copy(left = slide)
        SlideWay.RIGHT -> copy(right = slide)
    }

    /** As kept in the settings: "on;grooves;DJ_MIX;STEADY;SONG_PATH;SONIC_MATCH". */
    fun format(): String = listOf(if (on) "on" else "off", if (grooves) "grooves" else "plain", up.name, down.name, left.name, right.name).joinToString(";")

    companion object {
        /** [format]'s string back; anything missing or unknown is the default. */
        fun parse(text: String?): PlaySlides {
            val default = PlaySlides()
            val parts = text?.split(";") ?: return default
            return PlaySlides(
                on = parts.getOrNull(0)?.let { it != "off" } ?: default.on,
                grooves = parts.getOrNull(1)?.let { it != "plain" } ?: default.grooves,
                up = PlaySlide.parse(parts.getOrNull(2)) ?: default.up,
                down = PlaySlide.parse(parts.getOrNull(3)) ?: default.down,
                left = PlaySlide.parse(parts.getOrNull(4)) ?: default.left,
                right = PlaySlide.parse(parts.getOrNull(5)) ?: default.right,
            )
        }
    }
}

/**
 * Which way a finger [dx], [dy] away from where it went down is sliding: the longer of the two, or none while it's within
 * [deadZone] of where it started (back in the middle cancels).
 */
fun slideWay(dx: Float, dy: Float, deadZone: Float): SlideWay? = when {
    hypot(dx, dy) < deadZone -> null
    abs(dx) >= abs(dy) -> if (dx < 0) SlideWay.LEFT else SlideWay.RIGHT
    else -> if (dy < 0) SlideWay.UP else SlideWay.DOWN
}

/** How far along [way] a finger [dx], [dy] away has gone (negative going the other way). */
fun slideDistance(way: SlideWay, dx: Float, dy: Float): Float = when (way) {
    SlideWay.UP -> -dy
    SlideWay.DOWN -> dy
    SlideWay.LEFT -> -dx
    SlideWay.RIGHT -> dx
}

/** A slide as it's shown: which way, and whether it's gone far enough to start on letting go. */
data class SlideState(val way: SlideWay, val armed: Boolean)

/**
 * Now playing's play button (issue #919). A tap plays or pauses, and pressing and holding stops, as before. Sliding a
 * finger from it up, down, left or right (past the button's edge is fine; only the way counts) shows that way's choice
 * beside the button ([slides]), growing and filling with the play button's colour, with a click, once it's far enough;
 * let go there and [onSlide] runs it. Back in the middle, or a way with Nothing on it, and nothing happens. [unavailable]
 * says why a choice can't run now ("Needs AudioMuse"), shown in place of its name.
 *
 * While a DJ mix plays and [grooves] is on, the button shows record grooves in its own colour, with a glint that turns
 * while it plays. [onSliding] tells the controls around it to make way. [preview] draws a slide without a finger, for
 * the screenshot tests.
 */
@Composable
internal fun PlayButton(
    playing: Boolean,
    slides: PlaySlides,
    djMix: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSlide: (PlaySlide) -> Unit,
    modifier: Modifier = Modifier,
    unavailable: (PlaySlide) -> String? = { null },
    onSliding: (SlideWay?) -> Unit = {},
    preview: SlideState? = null,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val deadZone = with(density) { SLIDE_DEAD_ZONE.toPx() }
    val armAt = with(density) { SLIDE_ARM.toPx() }
    val toggle by rememberUpdatedState(onToggle)
    val stop by rememberUpdatedState(onStop)
    val runSlide by rememberUpdatedState(onSlide)
    val sliding by rememberUpdatedState(onSliding)
    val current by rememberUpdatedState(slides)
    val blocked by rememberUpdatedState(unavailable)
    var shown by remember { mutableStateOf<SlideState?>(null) }
    val state = preview ?: shown
    // The play button's own colour, as set under Style.
    val accent = Accent
    val onAccent = OnAccent

    fun show(next: SlideState?) {
        if (next == shown) return
        val before = shown
        shown = next
        if (next?.way != before?.way) sliding(next?.way)
        // A click as a choice appears, changes, or is far enough to start.
        if (next != null && (next.way != before?.way || (next.armed && before?.armed != true))) {
            QueueHaptics.vibrate(context, QueueHaptics.Kind.START)
        }
    }

    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val slop = viewConfiguration.touchSlop
            // Until it's held long enough, let go, or moved: a tap, a hold or a slide.
            var outcome = TAP_PENDING
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (outcome == TAP_PENDING) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        outcome = CANCELLED
                    } else if (change.changedToUp()) {
                        change.consume()
                        outcome = TAPPED
                    } else if (current.on && (change.position - down.position).getDistance() > slop) {
                        change.consume()
                        outcome = SLIDING
                    }
                }
            }
            when (outcome) {
                TAPPED -> toggle()
                TAP_PENDING -> {
                    // Held: stop, as before.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    stop()
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                    } while (!change.changedToUp())
                }
                SLIDING -> {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val d = change.position - down.position
                        val way = slideWay(d.x, d.y, deadZone)
                        val slide = way?.let { current.of(it) }
                        val next = if (way == null || slide == PlaySlide.NOTHING) null
                        else SlideState(way, slideDistance(way, d.x, d.y) >= armAt && blocked(slide!!) == null)
                        if (change.changedToUp()) {
                            if (next?.armed == true) {
                                QueueHaptics.vibrate(context, QueueHaptics.Kind.ADDED)
                                runSlide(current.of(next.way))
                            }
                            break
                        }
                        show(next)
                    }
                    show(null)
                }
            }
        }
    }

    Box(
        modifier
            .size(PLAY_SIZE)
            .zIndex(if (state != null) 1f else 0f)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Play or pause"
                onClick(label = if (playing) "Pause" else "Play") { toggle(); true }
                onLongClick(label = "Stop") { stop(); true }
                if (slides.on) {
                    customActions = SlideWay.entries.map { slides.of(it) }.filter { it != PlaySlide.NOTHING && unavailable(it) == null }
                        .distinct().map { slide -> CustomAccessibilityAction(slide.label) { onSlide(slide); true } }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(accent).then(gestures).testTag("playButton"),
            contentAlignment = Alignment.Center,
        ) {
            if (djMix && slides.grooves) Grooves(playing, Modifier.fillMaxSize().testTag("playGrooves"))
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = onAccent, modifier = Modifier.size(38.dp))
        }
        if (state != null) {
            val slide = slides.of(state.way)
            SlideChoice(slide, state, unavailable(slide), accent, onAccent)
        }
    }
}

/** The choice of a slide, beside the play button on its side, with its name (or why it can't run) beyond it. */
@Composable
private fun SlideChoice(slide: PlaySlide, state: SlideState, why: String?, accent: Color, onAccent: Color) {
    val density = LocalDensity.current
    val reach = with(density) { SLIDE_REACH.roundToPx() }
    val (x, y) = when (state.way) {
        SlideWay.UP -> 0 to -reach
        SlideWay.DOWN -> 0 to reach
        SlideWay.LEFT -> -reach to 0
        SlideWay.RIGHT -> reach to 0
    }
    val size = if (state.armed) CHOICE_SIZE else CHOICE_SIZE_RESTING
    Box(Modifier.wrapContentSize(unbounded = true).offset { IntOffset(x, y) }.testTag("slideChoice-${state.way.name.lowercase()}"), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(if (state.armed) accent else Color.White.copy(alpha = RESTING_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            slide.icon?.let { Icon(it, null, tint = if (state.armed) onAccent else Color.White, modifier = Modifier.size(CHOICE_ICON)) }
        }
        // The name goes beyond the choice, away from the button: above, except going down.
        val nameShift = with(density) { (CHOICE_SIZE / 2 + Space.Page).roundToPx() }
        Text(
            why ?: slide.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (state.armed) accent else Color.White,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .offset { IntOffset(0, if (state.way == SlideWay.DOWN) nameShift else -nameShift) }
                .clip(RoundedCornerShape(Space.Small))
                .background(Color.Black.copy(alpha = NAME_BACKING))
                .padding(horizontal = Space.Small, vertical = Space.Tight)
                .testTag("slideName"),
        )
    }
}

/**
 * Record grooves over the play button, in its own colour (issue #919): fine rings a shade darker, a label in the middle,
 * and a glint that goes round while it plays and stands still when paused.
 */
@Composable
private fun Grooves(playing: Boolean, modifier: Modifier = Modifier) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            turn.animateTo(turn.value + 360f, tween(GLINT_TURN_MS, easing = LinearEasing))
            turn.snapTo(turn.value % 360f)
        }
    }
    Canvas(modifier) {
        val r = size.minDimension / 2
        val line = 1.dp.toPx()  // spacing: exact (a groove's hairline)
        GROOVE_RADII.forEachIndexed { i, f ->
            drawCircle(Color.Black.copy(alpha = if (i % 2 == 0) GROOVE_DARK else GROOVE_LIGHT), radius = r * f, style = Stroke(line))
        }
        drawCircle(Color.Black.copy(alpha = LABEL_SHADE), radius = r * LABEL_RADIUS)
        rotate(turn.value) {
            val glint = r * GLINT_RADIUS
            drawArc(
                Color.White.copy(alpha = GLINT_ALPHA),
                startAngle = GLINT_START, sweepAngle = GLINT_SWEEP, useCenter = false,
                topLeft = Offset(center.x - glint, center.y - glint), size = Size(glint * 2, glint * 2),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),  // spacing: exact (the glint's line)
            )
        }
    }
}

private const val TAP_PENDING = 0
private const val TAPPED = 1
private const val SLIDING = 2
private const val CANCELLED = 3

val PLAY_SIZE = 68.dp
/** Within this of where the finger went down, no choice shows: back in the middle cancels. */
private val SLIDE_DEAD_ZONE = 20.dp
/** Far enough to start on letting go: half the play button. */
private val SLIDE_ARM = 34.dp
/** The choice's middle from the button's: the button's edge, a gap, then the choice (issue #919: not too close, not far). */
private val SLIDE_REACH = PLAY_SIZE / 2 + Space.Page + 18.dp
private val CHOICE_SIZE = 36.dp
private val CHOICE_SIZE_RESTING = 30.dp
private val CHOICE_ICON = 20.dp
private const val RESTING_ALPHA = 0.18f
private const val NAME_BACKING = 0.35f
private val GROOVE_RADII = listOf(0.94f, 0.86f, 0.78f, 0.70f, 0.62f, 0.54f)
private const val GROOVE_DARK = 0.16f
private const val GROOVE_LIGHT = 0.10f
private const val LABEL_RADIUS = 0.40f
private const val LABEL_SHADE = 0.08f
private const val GLINT_RADIUS = 0.78f
private const val GLINT_ALPHA = 0.45f
private const val GLINT_START = 200f
private const val GLINT_SWEEP = 50f
/** One turn of the glint: slow, so it reads as turning rather than spinning. */
private const val GLINT_TURN_MS = 4_000

/**
 * Why [slide] can't run now, in a few words shown in place of its name, or null when it can: a mix as the ⋮ menu's
 * Start a mix allows (not while AudioMuse is set up but can't be reached), a song path only with AudioMuse.
 */
fun slideUnavailable(slide: PlaySlide, settings: app.siansa.data.AppSettings, aiProblem: String?): String? = when (slide) {
    PlaySlide.SONG_PATH -> when {
        !settings.audioMuseReady -> "Needs AudioMuse"
        aiProblem != null -> "Not available now"
        else -> null
    }
    PlaySlide.DJ_MIX, PlaySlide.STEADY, PlaySlide.START_A_MIX -> if (settings.audioMuseReady && aiProblem != null) "Not available now" else null
    PlaySlide.SONIC_MATCH, PlaySlide.NOTHING -> null
}
