package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.siansa.mix.MixShape

/**
 * A place on the DJ mix bar (issue #1033): while a DJ mix plays, sliding up from now playing's play button turns the
 * seek bar into this bar, and letting go on a place stops the mix or gives it that shape. [short] is its name on the
 * bar, [title] its name on the cover.
 */
enum class DjBarSpot(val id: String, val short: String, val title: String) {
    STEADY("steady", "Steady", MixShape.STEADY.label),
    BUILD("build", "Build", MixShape.BUILD.label),
    STOP("stop", "Stop", "Stop the mix"),
    EUPHORIC("euphoric", "Euphoric", MixShape.EUPHORIC.label),
    WIND_DOWN("wind-down", "Wind down", MixShape.WIND_DOWN.label);

    /** The shape this place gives the mix; null for Stop. */
    val shape: MixShape?
        get() = when (this) {
            STEADY -> MixShape.STEADY
            BUILD -> MixShape.BUILD
            STOP -> null
            EUPHORIC -> MixShape.EUPHORIC
            WIND_DOWN -> MixShape.WIND_DOWN
        }

    /** What letting go here does, under its name on the cover. */
    val hint: String
        get() = shape?.hint ?: "No more blends: the songs left in the queue play on as they are."

    /** Its drawing: the energy's way over the next songs, or a stop square. */
    val icon: ImageVector
        get() = DjBarIcons.of(this)

    companion object {
        /** Left to right as the bar starts: the steady shapes, Stop in the middle, the lively and the calm (the owner). */
        val default = listOf(STEADY, BUILD, STOP, EUPHORIC, WIND_DOWN)

        /** The place that gives [shape]. */
        fun of(shape: MixShape): DjBarSpot = entries.first { it.shape == shape }

        /**
         * The bar's order as kept in the settings ("steady,build,stop,euphoric,wind-down"); unknown names are left out
         * and missing places go at the end in [default]'s order, so every place is always on the bar once.
         */
        fun parse(text: String?): List<DjBarSpot> {
            val named = text.orEmpty().split(',').mapNotNull { name -> entries.firstOrNull { it.id == name.trim() } }.distinct()
            return named + default.filter { it !in named }
        }

        fun format(order: List<DjBarSpot>): String = order.joinToString(",") { it.id }
    }
}

/**
 * Which of [count] places across the bar a finger [dx] px from the play button's middle is on: the bar is [widthPx]
 * wide and centred on the button, each place an equal part of it. Past either end is the end place.
 */
fun djBarSpotAt(dx: Float, widthPx: Float, count: Int): Int {
    if (count <= 1 || widthPx <= 0f) return 0
    val along = dx + widthPx / 2f
    return (along / (widthPx / count)).toInt().coerceIn(0, count - 1)
}

/**
 * The places on the bar for what's playing: a DJ radio's in the order set under Style (shapes and Stop); an album's or
 * playlist's DJ mix plays its own songs in its own order, so only Stop. Null when no DJ mix plays.
 */
fun djBarSpots(order: List<DjBarSpot>, djRadio: Boolean, listMix: Boolean): List<DjBarSpot>? = when {
    djRadio -> order
    listMix -> listOf(DjBarSpot.STOP)
    else -> null
}

/** The DJ mix bar as a slide shows it: its places, the one the finger is on, and how far it has come in (0 to 1). */
data class DjBarView(val spots: List<DjBarSpot>, val chosen: Int?, val rise: Float) {
    val chosenSpot: DjBarSpot? get() = chosen?.let { spots.getOrNull(it) }
}

/**
 * The DJ mix bar (issue #1033), drawn over the seek bar while a finger slides up from the play button during a DJ mix.
 * A see-through panel the seek bar's width, with each place in an equal part: its drawing in a circle, its name under
 * it. It fades in as the finger comes up ([rise]). The place under the finger ([chosen]) grows and fills: Stop in
 * white, a shape in the accent. The mix's shape [now] has a ring in the accent and its name in it.
 */
@Composable
internal fun DjBar(spots: List<DjBarSpot>, chosen: Int?, now: DjBarSpot?, rise: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(DJ_BAR_CORNER)
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rise
                // Grows out of the seek bar's line as it comes in.
                scaleY = DJ_BAR_GROW_FROM + (1f - DJ_BAR_GROW_FROM) * rise
            }
            .clip(shape)
            .background(Color.Black.copy(alpha = DJ_BAR_SHADE))
            .border(DJ_BAR_EDGE, Color.White.copy(alpha = DJ_BAR_EDGE_ALPHA), shape)
            .testTag("djBar"),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.Tight)) {
            spots.forEachIndexed { i, spot ->
                DjBarPlace(spot, chosen = i == chosen, now = spot == now, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DjBarPlace(spot: DjBarSpot, chosen: Boolean, now: Boolean, modifier: Modifier) {
    val stop = spot == DjBarSpot.STOP
    val accent = Accent
    val size by animateDpAsState(
        when {
            chosen -> DJ_SPOT_CHOSEN
            stop -> DJ_SPOT_STOP
            else -> DJ_SPOT
        },
        label = "djSpotSize",
    )
    val fill by animateColorAsState(
        when {
            chosen && stop -> Color.White
            chosen -> accent
            stop -> Color.White.copy(alpha = DJ_STOP_TINT)
            else -> Color.White.copy(alpha = DJ_SPOT_TINT)
        },
        label = "djSpotFill",
    )
    val ink = when {
        chosen && stop -> DJ_STOP_INK
        chosen -> OnAccent
        else -> Color.White
    }
    Column(
        modifier.testTag(if (chosen) "djBarChosen" else "djBarPlace"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        Box(Modifier.size(DJ_SPOT_CHOSEN), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(fill)
                    .then(if (now && !chosen) Modifier.border(DJ_NOW_RING, accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(spot.icon, null, tint = ink, modifier = Modifier.size(DJ_SPOT_ICON))
            }
        }
        // The whole name, never cut: a long one ("Wind down", or any at a large font size) gets a little smaller until
        // it fits its place, with a little room either side. The line stays as tall, so the bar's spacing never moves.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val weight = if (chosen || now) FontWeight.Bold else FontWeight.Medium
            val style = MaterialTheme.typography.labelSmall.copy(fontWeight = weight, letterSpacing = 0.sp, lineHeight = DJ_NAME_LINE)
            val room = with(LocalDensity.current) { (maxWidth - Space.Tight).roundToPx() }
            val measurer = rememberTextMeasurer()
            val size = remember(spot.short, weight, room, style) { fittedSize(spot.short, style, room) { t, st -> measurer.measure(t, st, maxLines = 1, softWrap = false).size.width } }
            Text(
                spot.short,
                style = style.copy(fontSize = size),
                color = when {
                    chosen -> Color.White
                    now -> accent
                    else -> Color.White.copy(alpha = DJ_NAME_ALPHA)
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The largest name size from [DJ_NAME_SIZE] down to [DJ_NAME_SMALLEST] (half a point at a time) at which [text] in
 * [style] is no wider than [roomPx], as [widthOf] measures it; the smallest when even that is too wide.
 */
internal fun fittedSize(text: String, style: TextStyle, roomPx: Int, widthOf: (String, TextStyle) -> Int): TextUnit {
    var size = DJ_NAME_SIZE.value
    while (size > DJ_NAME_SMALLEST.value && widthOf(text, style.copy(fontSize = size.sp)) > roomPx) size -= 0.5f
    return size.coerceAtLeast(DJ_NAME_SMALLEST.value).sp
}

/** The bar's drawings: the energy's way over the next songs, as the mockup drew them, and a stop square. */
private object DjBarIcons {
    private fun line(name: String, vararg points: Pair<Float, Float>): ImageVector =
        ImageVector.Builder(name = "Siansa.DjBar.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(points[0].first, points[0].second)
                points.drop(1).forEach { (x, y) -> lineTo(x, y) }
            }
        }.build()

    private val steady by lazy { line("Steady", 4f to 12f, 20f to 12f) }
    private val build by lazy { line("Build", 4f to 18f, 20f to 6f) }
    private val euphoric by lazy { line("Euphoric", 4f to 18f, 9.5f to 7f, 20f to 7f) }
    private val windDown by lazy { line("WindDown", 4f to 6f, 20f to 18f) }
    private val stop by lazy {
        ImageVector.Builder(name = "Siansa.DjBar.Stop", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.5f, 7f)
                lineTo(15.5f, 7f)
                arcTo(1.5f, 1.5f, 0f, false, true, 17f, 8.5f)
                lineTo(17f, 15.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 15.5f, 17f)
                lineTo(8.5f, 17f)
                arcTo(1.5f, 1.5f, 0f, false, true, 7f, 15.5f)
                lineTo(7f, 8.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 8.5f, 7f)
                close()
            }
        }.build()
    }

    fun of(spot: DjBarSpot): ImageVector = when (spot) {
        DjBarSpot.STEADY -> steady
        DjBarSpot.BUILD -> build
        DjBarSpot.STOP -> stop
        DjBarSpot.EUPHORIC -> euphoric
        DjBarSpot.WIND_DOWN -> windDown
    }
}

/**
 * The bar's room: the seek bar's 56 dp and the 8 dp under it down to the play button, so nothing moves. A place's
 * circle row ([DJ_SPOT_CHOSEN]) and its name (12 sp) with [Space.Tight] above, between and below them fill it evenly.
 */
internal val DJ_BAR_HEIGHT = 64.dp
private val DJ_BAR_CORNER = 22.dp
private val DJ_SPOT_CHOSEN = 40.dp
private val DJ_SPOT_STOP = 34.dp
private val DJ_SPOT = 30.dp
private val DJ_SPOT_ICON = 20.dp
private val DJ_NOW_RING = 1.5.dp
private val DJ_BAR_EDGE = 1.dp

/** The panel is see-through (the owner): the page shows through its shade. */
private const val DJ_BAR_SHADE = 0.32f
private const val DJ_BAR_EDGE_ALPHA = 0.14f
private const val DJ_BAR_GROW_FROM = 0.4f
private const val DJ_SPOT_TINT = 0.12f
private const val DJ_STOP_TINT = 0.2f
private const val DJ_NAME_ALPHA = 0.72f
private val DJ_NAME_SIZE = 11.sp
private val DJ_NAME_SMALLEST = 8.sp
/** One line of a name, whatever its size: 12 sp, so the bar is always as tall. */
private val DJ_NAME_LINE = 12.sp
private val DJ_STOP_INK = Color(0xFF141414)
