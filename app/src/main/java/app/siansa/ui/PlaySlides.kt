package app.siansa.ui

import app.siansa.net.soundReady
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

/**
 * A slide as it's shown: which way, and whether it's gone far enough to start on letting go. On the DJ mix bar
 * (issue #1033, [bar]): how far up to it the finger has come ([rise], 0 to 1) and the place it's on ([spot]), armed once
 * it's on one.
 */
data class SlideState(val way: SlideWay, val armed: Boolean, val bar: Boolean = false, val rise: Float = 0f, val spot: Int? = null)

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
    onSliding: (SlideState?) -> Unit = {},
    preview: SlideState? = null,
    /** How far up the up choice goes from the button's centre, in px, to sit on the seek bar (issue #963); null for its usual reach. */
    upReachPx: Int? = null,
    /**
     * While a DJ mix plays (issue #1033): sliding up opens the DJ mix bar on the seek bar with these places, left to
     * right, instead of the up choice; letting go on one runs [onDjBar]. Null: up is the up choice as ever.
     */
    djBar: List<DjBarSpot>? = null,
    /** The DJ mix bar's width in px (the seek bar's), centred on the button, to tell which place the finger is on. */
    djBarWidthPx: Int? = null,
    onDjBar: (DjBarSpot) -> Unit = {},
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
    val bar by rememberUpdatedState(djBar)
    val barWidth by rememberUpdatedState(djBarWidthPx)
    val pickOnBar by rememberUpdatedState(onDjBar)
    val reachUp by rememberUpdatedState(upReachPx)
    val reachDefault = with(density) { SLIDE_REACH.toPx() }
    val barGrace = with(density) { DJ_BAR_GRACE.toPx() }
    // Up on the DJ mix bar: stays so while the finger moves along it, until it goes back down past halfway.
    var onBar by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<SlideState?>(null) }
    val state = preview ?: shown
    // The play button's own colour, as set under Style.
    val accent = Accent
    val onAccent = OnAccent

    fun show(next: SlideState?) {
        if (next == shown) return
        val before = shown
        shown = next
        sliding(next)
        // A click as a choice appears, changes, or is far enough to start; on the DJ mix bar, at each place.
        if (next != null && (next.way != before?.way || (next.armed && before?.armed != true) || (next.spot != null && next.spot != before?.spot))) {
            QueueHaptics.vibrate(context, QueueHaptics.Kind.START)
        }
    }

    // Taps and holds, and slides, each to Compose's own gesture detector. The slide's detector comes first for each
    // touch (it's further in), and once it has taken a moving finger the tap and the hold no longer happen.
    var held by remember { mutableStateOf(false) }
    val taps = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onPress = { held = false },
            onTap = { toggle() },
            onLongPress = {
                // Held: stop, as before; letting go after doesn't slide.
                held = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                stop()
            },
        )
    }
    val slidesInput = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = {
                val last = shown
                val places = bar
                if (last?.bar == true && last.spot != null && places != null && !held) {
                    QueueHaptics.vibrate(context, QueueHaptics.Kind.ADDED)
                    places.getOrNull(last.spot)?.let { pickOnBar(it) }
                } else if (last?.bar != true && last?.armed == true && !held) {
                    QueueHaptics.vibrate(context, QueueHaptics.Kind.ADDED)
                    runSlide(current.of(last.way))
                }
                onBar = false
                show(null)
            },
            onDragCancel = { onBar = false; show(null) },
            onDrag = { change, _ ->
                if (!current.on || held) return@detectDragGestures
                change.consume()
                // Where the finger is from the button's middle: only the way counts, and past the edge is fine.
                val d = change.position - Offset(size.width / 2f, size.height / 2f)
                // Up goes to the DJ mix bar on the seek bar (issues #1033, #1035): it opens as soon as the finger heads up,
                // and once the finger is on it, it moves along it freely; back down past halfway leaves it.
                val places = bar
                val up = -d.y
                if (!places.isNullOrEmpty() && (onBar || (up > deadZone && up >= abs(d.x)))) {
                    val reach = (reachUp ?: reachDefault.toInt()).toFloat()
                    val landAt = (reach - barGrace).coerceAtLeast(deadZone + 1f)
                    if (up >= landAt) onBar = true else if (up < reach / 2f) onBar = false
                    val spot = if (onBar) djBarSpotAt(d.x, (barWidth ?: 0).toFloat(), places.size) else null
                    val rise = if (onBar) 1f else ((up - deadZone) / (landAt - deadZone)).coerceIn(0f, 1f)
                    show(SlideState(SlideWay.UP, armed = spot != null, bar = true, rise = rise, spot = spot))
                    return@detectDragGestures
                }
                val way = slideWay(d.x, d.y, deadZone)
                val slide = way?.let { current.of(it) }
                show(
                    if (way == null || slide == PlaySlide.NOTHING) null
                    else SlideState(way, slideDistance(way, d.x, d.y) >= armAt && blocked(slide!!) == null),
                )
            },
        )
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
            // The touch is taken on the whole square, not only the round inside: a finger on the edge still counts.
            Modifier.fillMaxSize().then(taps).then(slidesInput).clip(CircleShape).background(accent).testTag("playButton"),
            contentAlignment = Alignment.Center,
        ) {
            if (djMix && slides.grooves) Grooves(playing, Modifier.fillMaxSize().testTag("playGrooves"))
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = onAccent, modifier = Modifier.size(38.dp))
        }
        // On the way to the DJ mix bar the bar itself is the choice: nothing beside the button.
        if (state != null && !state.bar) {
            val slide = slides.of(state.way)
            SlideChoice(slide, state, accent, onAccent, upReachPx)
        }
    }
}

/**
 * The choice of a slide, beside the play button on its side. Its name is in the middle of the cover ([SlideCoverLabel]),
 * where the thumb on the button can't cover it (issue #933).
 */
@Composable
private fun SlideChoice(slide: PlaySlide, state: SlideState, accent: Color, onAccent: Color, upReachPx: Int? = null) {
    val reach = with(LocalDensity.current) { SLIDE_REACH.roundToPx() }
    val (x, y) = when (state.way) {
        SlideWay.UP -> 0 to -(upReachPx ?: reach)
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
    }
}

/** What a slide from the play button shows in the cover: the choice, its way, whether it's far enough, and why it can't run. */
data class SlideLabel(val slide: PlaySlide, val way: SlideWay, val armed: Boolean, val why: String? = null, val bar: DjBarView? = null)

/**
 * A slide's choice in the middle of now playing's cover (issue #933), where a thumb on the play button can't hide it:
 * the cover blurs behind its icon and name. Until the finger is far enough it's faint, on a lighter blur, with "Slide a
 * little further" and the way to go; then it lights up in the accent with "Let go to start" (the second vibrate comes
 * from the button). Why a choice can't run ("Needs AudioMuse") shows in place of those words. Nothing while [label] is
 * null.
 */
@Composable
internal fun SlideCoverLabel(label: SlideLabel?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(label) }
    if (label != null) last = label
    androidx.compose.animation.AnimatedVisibility(
        visible = label != null,
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn(tween(LABEL_FADE_MS)),
        exit = androidx.compose.animation.fadeOut(tween(LABEL_FADE_MS)),
    ) {
        val shown = last ?: return@AnimatedVisibility
        val ready = shown.armed && shown.why == null
        // On the DJ mix bar (issue #1033): the place under the finger, what letting go there does, and how.
        val bar = shown.bar
        val place = bar?.chosenSpot
        val starting = bar?.starting == true
        val stopping = place == DjBarSpot.MIX && !starting
        val icon = if (bar != null) place?.icon(starting) ?: SiansaIcons.Turntable else shown.slide.icon
        val name = if (bar != null) place?.title(starting) ?: (if (starting) "DJ mix" else "Shape the mix") else shown.slide.label
        val action = when {
            bar == null -> shown.why ?: if (ready) "Let go to start" else "Slide a little further"
            stopping -> "Let go to stop"
            place != null && starting -> "Let go to start"
            place != null -> "Let go to reshape"
            else -> "Slide up onto the bar"
        }
        val scrim by androidx.compose.animation.core.animateFloatAsState(
            when {
                ready -> if (android.os.Build.VERSION.SDK_INT >= 31) SCRIM_READY else SCRIM_READY_NO_BLUR
                else -> if (android.os.Build.VERSION.SDK_INT >= 31) SCRIM_SLIDING else SCRIM_SLIDING_NO_BLUR
            },
            label = "slideScrim",
        )
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)).testTag("slideLabel"),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = Space.Section).alpha(if (ready) 1f else LABEL_FAINT),
            ) {
                // Its icon, on its own: no circle round it. Stop stays white, so it never looks like a shape to pick.
                icon?.let { Icon(it, null, tint = if (ready && !stopping) Accent else Color.White, modifier = Modifier.size(LABEL_ICON)) }
                androidx.compose.foundation.layout.Spacer(Modifier.size(Space.Medium))
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("slideName"),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(Space.Small))
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    // The way to go; on the DJ mix bar only until the finger is on it.
                    if (shown.why == null && place == null) {
                        Icon(shown.way.arrowIcon, null, tint = Accent, modifier = Modifier.size(LABEL_ARROW))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(Space.Tight))
                    }
                    Text(
                        action,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = LABEL_HINT),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                // What the place does, under its name.
                if (place != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(Space.Small))
                    Text(
                        place.hint(starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = LABEL_HINT),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("slideHint"),
                    )
                }
            }
        }
    }
}

/** How much the cover blurs behind a slide's name: lightly while sliding, fully once it's far enough (issue #933). */
internal fun slideBlur(label: SlideLabel?): androidx.compose.ui.unit.Dp = when {
    label == null -> 0.dp
    label.armed && label.why == null -> COVER_BLUR_READY
    else -> COVER_BLUR_SLIDING
}

/** The arrow pointing the way a slide goes. */
private val SlideWay.arrowIcon: ImageVector
    get() = when (this) {
        SlideWay.UP -> Icons.Rounded.KeyboardArrowUp
        SlideWay.DOWN -> Icons.Rounded.KeyboardArrowDown
        SlideWay.LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
        SlideWay.RIGHT -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
    }

/**
 * Record grooves over the play button, in its own colour (issue #919): fine rings a shade darker, a label in the middle,
 * and a glint that goes round while it plays and stands still when paused.
 */
@Composable
internal fun Grooves(playing: Boolean, modifier: Modifier = Modifier) {
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

val PLAY_SIZE = 68.dp

/** The finger is on the DJ mix bar a little before its middle line (issue #1033), so a short slide is enough. */
private val DJ_BAR_GRACE = Space.Page
/** Within this of the button's middle, no choice shows: back in the middle cancels. */
private val SLIDE_DEAD_ZONE = 20.dp
/** Far enough to start on letting go, from the button's middle: a little past its edge. */
private val SLIDE_ARM = PLAY_SIZE / 2 + Space.Small
/** The choice's middle from the button's: the button's edge, a gap, then the choice (issue #919: not too close, not far). */
private val SLIDE_REACH = PLAY_SIZE / 2 + Space.Page + 18.dp
private val CHOICE_SIZE = 36.dp
private val CHOICE_SIZE_RESTING = 30.dp
private val CHOICE_ICON = 20.dp
private const val RESTING_ALPHA = 0.18f
private const val LABEL_FADE_MS = 140
private val LABEL_ICON = 48.dp
private val LABEL_ARROW = 20.dp
private const val LABEL_FAINT = 0.6f
private const val LABEL_HINT = 0.85f
private val COVER_BLUR_SLIDING = 10.dp
private val COVER_BLUR_READY = 24.dp
private const val SCRIM_SLIDING = 0.25f
private const val SCRIM_READY = 0.4f
// Without blur (before Android 12) the cover is darkened more, so the name still reads over it.
private const val SCRIM_SLIDING_NO_BLUR = 0.45f
private const val SCRIM_READY_NO_BLUR = 0.62f
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
fun slideUnavailable(slide: PlaySlide, settings: app.siansa.data.AppSettings, aiProblem: String?, djProblem: String? = null): String? = when (slide) {
    PlaySlide.SONG_PATH -> when {
        !settings.soundReady -> "Needs AudioMuse"
        aiProblem != null -> "Not available now"
        else -> null
    }
    // A DJ mix only when its needs are met ([app.siansa.mix.DjReady]): AudioMuse and Siansa Beats answering.
    PlaySlide.DJ_MIX -> djProblem
    PlaySlide.STEADY, PlaySlide.START_A_MIX -> if (settings.audioMuseReady && aiProblem != null) "Not available now" else null
    PlaySlide.SONIC_MATCH, PlaySlide.NOTHING -> null
}
