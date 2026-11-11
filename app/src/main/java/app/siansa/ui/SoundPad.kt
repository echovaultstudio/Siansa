package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.mix.MixSound
import app.siansa.mix.PadDot
import app.siansa.mix.SoundDial
import app.siansa.mix.VibeTuning
import app.siansa.mix.padCaption
import kotlin.math.roundToInt

/**
 * "Shape your sound" (issue #339; the owner's design, 11 Nov): the sound pad, where one dot sets energy (up and down)
 * and speed (left and right) together, the Niche ↔ Familiar pill, and a pill for each sound dial, all in the look of
 * the queue's Shape your mix. [dots] are the mix's songs on the pad (none before there's a mix); [more] are sounds
 * of the mix that aren't a dial yet, each added by a tap ([onAdd]). [onSettled] runs when a finger is lifted, so a
 * vibe's page can recalculate then (it reads the value [onChange] last gave it, which is already stored by then).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VibeSliders(
    tuning: VibeTuning,
    onChange: (VibeTuning) -> Unit,
    onSettled: () -> Unit = {},
    dots: List<PadDot> = emptyList(),
    soundsTitle: String = "Sounds",
    more: List<MixSound> = emptyList(),
    onAdd: (String) -> Unit = {},
) {
    val c = shapeColours()
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Column(Modifier.testTag("soundPadBlock"), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            SoundPad(
                tuning.energy, tuning.tempo, dots, c,
                caption = padCaption(tuning),
                onMove = { energy, tempo -> onChange(tuning.copy(energy = energy, tempo = tempo)) },
                onSettled = onSettled,
            )
            Text(
                padCaption(tuning) + if (dots.isNotEmpty()) " Each small dot is a song in this mix." else "",
                style = MaterialTheme.typography.bodySmall,
                color = c.label,
            )
        }
        StepPill("Niche", "Familiar", tuning.familiar, VibeTuning.SLIDER_MAX, "sliderFamiliar", c,
            onChange = { onChange(tuning.copy(familiar = it)) }, onSettled = onSettled)
        if (tuning.sounds.isNotEmpty()) {
            Text(soundsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("soundsTitle"))
            tuning.sounds.forEachIndexed { i, dial ->
                StepPill("Less ${dial.term}", "More ${dial.term}", dial.value, VibeTuning.DIAL_MAX, "sliderSound", c,
                    onChange = { v -> onChange(tuning.copy(sounds = tuning.sounds.toMutableList().also { it[i] = SoundDial(dial.term, v, dial.genre) })) },
                    onSettled = onSettled)
            }
        }
        if (more.isNotEmpty()) {
            Column(Modifier.testTag("soundsToAdd"), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                Text("Also in this mix", style = MaterialTheme.typography.labelMedium, color = c.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    more.forEach { sound ->
                        Row(
                            Modifier
                                .height(SHAPE_PILL_HEIGHT)
                                .clip(CircleShape)
                                .background(c.shade)
                                .border(SHAPE_EDGE, c.edge, CircleShape)
                                .clickable(onClickLabel = "Add a dial for ${sound.name}") { onAdd(sound.name) }
                                .padding(horizontal = Space.Medium)
                                .testTag("soundToAdd"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(SHAPE_GLYPH), tint = c.ink)
                            Spacer(Modifier.width(Space.Tight))
                            Text(sound.name, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The pad's and pills' colours: Shape your mix's see-through shade on a dark page, the same idea in ink on a light one. */
private class ShapeColours(val shade: Color, val edge: Color, val spot: Color, val ink: Color, val faint: Color, val label: Color)

@Composable
private fun shapeColours(): ShapeColours {
    val ink = LocalContentColor.current
    return if (ink.luminance() > 0.5f) {
        ShapeColours(Color.Black.copy(alpha = 0.32f), Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.12f), Color.White, Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.8f))
    } else {
        ShapeColours(ink.copy(alpha = 0.05f), ink.copy(alpha = 0.16f), ink.copy(alpha = 0.1f), ink, ink.copy(alpha = 0.34f), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where step [step] (−2…2) of the pad sits across it, 0 to 1: its five places keep clear of the edges. */
internal fun padAt(step: Int): Float = FIELD_INSET + (1f - 2 * FIELD_INSET) * (step.coerceIn(-2, 2) + 2) / 4f

/** The pad's place (0…4, from the left or the top) nearest to [fraction] of the way across it. */
internal fun padStep(fraction: Float): Int = ((fraction - FIELD_INSET) / ((1f - 2 * FIELD_INSET) / 4f)).roundToInt().coerceIn(0, 4)

/**
 * The sound pad: [energy] up and down, [tempo] left and right, each −2…2, set together by one dot that follows the
 * finger and snaps to the nearest of its 25 places. [dots] are songs, drawn where their own tempo and energy are.
 */
@Composable
private fun SoundPad(
    energy: Int,
    tempo: Int,
    dots: List<PadDot>,
    c: ShapeColours,
    caption: String,
    onMove: (energy: Int, tempo: Int) -> Unit,
    onSettled: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val accent = Accent
    val now by rememberUpdatedState(energy to tempo)
    val move by rememberUpdatedState(onMove)
    val settled by rememberUpdatedState(onSettled)
    val x by animateFloatAsState(padAt(tempo), label = "padX")
    val y by animateFloatAsState(padAt(-energy), label = "padY")
    val shape = RoundedCornerShape(FIELD_CORNER)
    fun step(e: Int, t: Int): Boolean {
        move(e.coerceIn(-2, 2), t.coerceIn(-2, 2))
        settled()
        return true
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(FIELD_HEIGHT)
            .clip(shape)
            .background(c.shade)
            .border(SHAPE_EDGE, c.edge, shape)
            .testTag("soundPad")
            .semantics {
                contentDescription = "Sound pad: up for more energy, right for faster"
                stateDescription = caption
                customActions = listOf(
                    CustomAccessibilityAction("More energy") { step(now.first + 1, now.second) },
                    CustomAccessibilityAction("Calmer") { step(now.first - 1, now.second) },
                    CustomAccessibilityAction("Faster") { step(now.first, now.second + 1) },
                    CustomAccessibilityAction("Slower") { step(now.first, now.second - 1) },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun place(at: Offset) {
                        val t = padStep(at.x / size.width) - 2
                        val e = 2 - padStep(at.y / size.height)
                        if (e to t != now) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            move(e, t)
                        }
                    }
                    place(down.position)
                    // The finger is the pad's while it's down: the page doesn't scroll under it.
                    drag(down.id) { change ->
                        place(change.position)
                        change.consume()
                    }
                    settled()
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            // The 25 places, the middle one (as planned) a little bigger.
            for (row in -2..2) for (col in -2..2) {
                val middle = row == 0 && col == 0
                drawCircle(
                    if (middle) c.faint.copy(alpha = 0.6f) else c.faint.copy(alpha = c.faint.alpha / 2),
                    (if (middle) 5.dp else 2.dp).toPx(),
                    Offset(size.width * padAt(col), size.height * padAt(row)),
                )
            }
            // The mix's songs: fast ones to the right, lively ones at the top.
            dots.forEach { d ->
                drawCircle(
                    c.ink.copy(alpha = 0.5f), 3.dp.toPx(),
                    Offset(size.width * (FIELD_SONGS_INSET + (1f - 2 * FIELD_SONGS_INSET) * d.x), size.height * (1f - FIELD_SONGS_INSET - (1f - 2 * FIELD_SONGS_INSET) * d.y)),
                )
            }
            val at = Offset(size.width * x, size.height * y)
            drawLine(c.edge, Offset(0f, at.y), Offset(size.width, at.y), 1.dp.toPx())
            drawLine(c.edge, Offset(at.x, 0f), Offset(at.x, size.height), 1.dp.toPx())
            drawCircle(accent.copy(alpha = 0.22f), FIELD_DOT_HALO.toPx(), at)
            drawCircle(accent, FIELD_DOT.toPx(), at)
        }
        val label = MaterialTheme.typography.labelMedium
        Text("More energy", style = label, color = c.label, modifier = Modifier.align(Alignment.TopCenter).padding(top = Space.Small))
        Text("Calmer", style = label, color = c.label, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.Small))
        // Just above the middle row of places, so neither covers a dot.
        Text("Slower", style = label, color = c.label, modifier = Modifier.align(Alignment.CenterStart).padding(start = Space.Medium, bottom = Space.Large))
        Text("Faster", style = label, color = c.label, modifier = Modifier.align(Alignment.CenterEnd).padding(end = Space.Medium, bottom = Space.Large))
    }
}

/**
 * One dial as a pill of places, [low] and [high] named above its ends: from −[max] to +[max] in whole steps, set by a
 * tap or by sliding along it, with a haptic tick per step. Five places ([max] 2) are drawn as Shape your mix's: a
 * circle each, the chosen one filled. More (the sound dials' eleven) are dots, the chosen one a knob with its number.
 */
@Composable
private fun StepPill(
    low: String,
    high: String,
    value: Int,
    max: Int,
    tag: String,
    c: ShapeColours,
    onChange: (Int) -> Unit,
    onSettled: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val now by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val settled by rememberUpdatedState(onSettled)
    val places = max * 2 + 1
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Row(Modifier.fillMaxWidth()) {
            Text(low, style = MaterialTheme.typography.labelMedium, color = c.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(high, style = MaterialTheme.typography.labelMedium, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(SHAPE_PILL_HEIGHT)
                .clip(CircleShape)
                .background(c.shade)
                .border(SHAPE_EDGE, c.edge, CircleShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$low to $high"
                    stateDescription = when {
                        value == 0 -> "As planned"
                        value < 0 -> "$low, ${-value} of $max"
                        else -> "$high, $value of $max"
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), -max.toFloat()..max.toFloat(), places - 2)
                    setProgress { asked ->
                        change(asked.roundToInt().coerceIn(-max, max))
                        settled()
                        true
                    }
                }
                .pointerInput(max) {
                    val inset = Space.Tight.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun place(x: Float) {
                            val step = ((x - inset) / (size.width - 2 * inset) * places).toInt().coerceIn(0, places - 1) - max
                            if (step != now) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                change(step)
                            }
                        }
                        place(down.position.x)
                        drag(down.id) { moved ->
                            place(moved.position.x)
                            moved.consume()
                        }
                        settled()
                    }
                }
                .padding(horizontal = Space.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (step in -max..max) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (max <= VibeTuning.SLIDER_MAX) PillSpot(step, step == value, c) else PillDot(step, value, c)
                }
            }
        }
    }
}

/** One of five places, as Shape your mix draws them: [step] (−2…2) as arrows pointing its way, the middle a dot. */
@Composable
private fun PillSpot(step: Int, chosen: Boolean, c: ShapeColours) {
    val spot by animateDpAsState(if (chosen) SHAPE_SPOT_CHOSEN else SHAPE_SPOT, label = "pillSpotSize")
    val fill by animateColorAsState(if (chosen) Accent else c.spot, label = "pillSpotFill")
    val ink = if (chosen) OnAccent else c.ink
    Box(Modifier.size(spot).clip(CircleShape).background(fill).testTag(if (chosen) "pillChosen" else "pillSpot"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(SHAPE_GLYPH)) {
            when (step) {
                0 -> drawCircle(ink, size.width * 0.17f)
                -1, 1 -> arrow(0.5f, step, ink)
                else -> {
                    arrow(0.34f, step, ink)
                    arrow(0.66f, step, ink)
                }
            }
        }
    }
}

/** An arrow head centred [at] of the way across, pointing left ([way] below 0) or right. */
private fun DrawScope.arrow(at: Float, way: Int, ink: Color) {
    val reach = size.width * 0.14f * if (way < 0) -1 else 1
    val x = size.width * at
    val path = Path().apply {
        moveTo(x - reach, size.height * 0.24f)
        lineTo(x + reach, size.height * 0.5f)
        lineTo(x - reach, size.height * 0.76f)
    }
    drawPath(path, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** One of a sound dial's places: a dot (in the accent between the middle and the knob), or the knob with its number. */
@Composable
private fun PillDot(step: Int, value: Int, c: ShapeColours) {
    if (step == value) {
        Box(Modifier.requiredSize(SHAPE_KNOB).clip(CircleShape).background(Accent).testTag("pillChosen"), contentAlignment = Alignment.Center) {
            Text(
                when {
                    value > 0 -> "+$value"
                    value < 0 -> "−${-value}"
                    else -> "0"
                },
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OnAccent, maxLines = 1,
            )
        }
    } else {
        val between = (value > 0 && step in 1 until value) || (value < 0 && step in (value + 1)..-1)
        Box(
            Modifier
                .size(if (step == 0) 8.dp else if (between) 6.dp else 4.dp)
                .clip(CircleShape)
                .background(if (between) Accent else if (step == 0) c.faint.copy(alpha = 0.6f) else c.faint),
        )
    }
}

/** The pad's height; its places sit [FIELD_INSET] of the way in from each edge, its songs [FIELD_SONGS_INSET]. */
private val FIELD_HEIGHT = 260.dp
private val FIELD_CORNER = 28.dp
private const val FIELD_INSET = 0.16f
private const val FIELD_SONGS_INSET = 0.08f
private val FIELD_DOT = 20.dp
private val FIELD_DOT_HALO = 28.dp

/** The pills are Shape your mix's bar: as high ([DJ_BAR_HEIGHT]), with its edge and its places' sizes. */
private val SHAPE_PILL_HEIGHT = DJ_BAR_HEIGHT
private val SHAPE_EDGE = 1.dp
private val SHAPE_SPOT_CHOSEN = 36.dp
private val SHAPE_SPOT = 28.dp
private val SHAPE_GLYPH = 18.dp
private val SHAPE_KNOB = 32.dp
