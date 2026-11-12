package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.siansa.mix.MixShape

/**
 * A place on the DJ mix bar (issues #1033, #1035): sliding up from now playing's play button turns the seek bar into
 * this bar, and letting go on a place starts a DJ mix, stops the one playing, or gives it a shape. [MIX] is the place
 * that starts or stops the mix: Start while none plays, Stop while one does. Its id stays "stop", as orders were kept.
 */
enum class DjBarSpot(val id: String) {
    STEADY("steady"),
    BUILD("build"),
    MIX("stop"),
    EUPHORIC("euphoric"),
    WIND_DOWN("wind-down");

    /** The shape this place gives the mix; null for [MIX]. */
    val shape: MixShape?
        get() = when (this) {
            STEADY -> MixShape.STEADY
            BUILD -> MixShape.BUILD
            MIX -> null
            EUPHORIC -> MixShape.EUPHORIC
            WIND_DOWN -> MixShape.WIND_DOWN
        }

    /** Its name on the cover: a shape's, or Start / Stop for [MIX] ([starting]: no mix plays yet). */
    fun title(starting: Boolean): String = shape?.label ?: if (starting) "Start a DJ mix" else "Stop the mix"

    /** Its name in Style › DJ Mix Bar, where it's both. */
    val arrangeLabel: String get() = shape?.label ?: "Start or stop the mix"

    /** What letting go here does, under its name on the cover. */
    fun hint(starting: Boolean): String = shape?.hint ?: if (starting) {
        "A DJ mix from this song: songs that blend, beat-matched one into the next."
    } else {
        "No more blends: the songs left in the queue play on as they are."
    }

    /** Its drawing: the energy's way over the next songs, or a turntable to start and a square to stop. */
    fun icon(starting: Boolean): ImageVector = if (this == MIX) (if (starting) SiansaIcons.Turntable else DjBarIcons.stop) else DjBarIcons.of(this)

    companion object {
        /** Left to right as the bar starts: the steady shapes, Start / Stop in the middle, the lively and the calm. */
        val default = listOf(STEADY, BUILD, MIX, EUPHORIC, WIND_DOWN)

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

/** What the bar holds: its places, left to right, and whether its middle starts a mix (none plays) or stops one. */
data class DjBarPlaces(val spots: List<DjBarSpot>, val starting: Boolean)

/**
 * The bar for what's playing (issue #1035): while a DJ radio plays, its shapes and Stop in the order set under Style;
 * an album's or playlist's DJ mix plays its own songs in its own order, so only Stop; with no DJ mix, the same places
 * with Start in the middle, when a slide up starts a DJ mix ([canStart]). Null: no bar, up is the usual up choice.
 */
fun djBarPlaces(order: List<DjBarSpot>, djRadio: Boolean, listMix: Boolean, canStart: Boolean): DjBarPlaces? = when {
    djRadio -> DjBarPlaces(order, starting = false)
    listMix -> DjBarPlaces(listOf(DjBarSpot.MIX), starting = false)
    canStart -> DjBarPlaces(order, starting = true)
    else -> null
}

/**
 * The DJ mix bar as a slide shows it: its places, the one the finger is on, whether its middle starts a mix, and how far
 * up to it the finger has come (0 to 1).
 */
data class DjBarView(val spots: List<DjBarSpot>, val chosen: Int?, val starting: Boolean, val rise: Float) {
    val chosenSpot: DjBarSpot? get() = chosen?.let { spots.getOrNull(it) }
}

/**
 * The DJ mix bar (issues #1033, #1035) over now playing's seek bar, [DJ_BAR_HEIGHT] tall and the seek bar's width,
 * centred on its line. [open] (0 to 1, sprung by the page) grows it out of that line: the thin line thickens into a
 * see-through pill, which reveals the places' drawings as it opens and hides them again as it closes. Symbols only;
 * the cover names the place under the finger. The place [chosen] grows and fills (Stop in white, the rest in the
 * accent); the mix's shape [now] has a ring in the accent.
 */
@Composable
internal fun DjBar(
    spots: List<DjBarSpot>,
    chosen: Int?,
    now: DjBarSpot?,
    starting: Boolean,
    open: Float,
    modifier: Modifier = Modifier,
    onTap: ((DjBarSpot) -> Unit)? = null,
) {
    GlidePill(open, modifier.testTag("djBar")) {
        spots.forEachIndexed { i, spot ->
            GlidePlace(
                spot.icon(starting), spot.title(starting), chosen = i == chosen, now = spot == now,
                middle = spot == DjBarSpot.MIX, dark = spot == DjBarSpot.MIX && !starting,
                tag = "djBar", onTap = onTap?.let { tap -> { tap(spot) } }, modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The glide bar's pill (issues #1033, #1035), [DJ_BAR_HEIGHT] tall and as wide as it's given: [open] (0 to 1) grows
 * it out of a thin line, as from now playing's seek bar, into a see-through pill that reveals its places. Also the
 * queue's Shape your mix bars, always open. [places] are laid in a row, each with a weight.
 */
@Composable
internal fun GlidePill(open: Float, modifier: Modifier = Modifier, places: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val thickness = DJ_TRACK_LINE + (DJ_BAR_HEIGHT - DJ_TRACK_LINE) * open
    val shape = RoundedCornerShape(thickness / 2)
    Box(modifier.fillMaxWidth().height(DJ_BAR_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .clip(shape)
                // From the seek bar's faint line to the bar's see-through shade.
                .background(lerp(Color.White.copy(alpha = DJ_TRACK_ALPHA), Color.Black.copy(alpha = DJ_BAR_SHADE), open))
                .border(DJ_BAR_EDGE, Color.White.copy(alpha = DJ_BAR_EDGE_ALPHA * open), shape)
                .testTag("djBarPill"),
            contentAlignment = Alignment.Center,
        ) {
            // The places are the full bar's height, centred in the pill and cut by its edges: opening reveals them.
            Row(
                Modifier
                    .fillMaxWidth()
                    .requiredHeight(DJ_BAR_HEIGHT)
                    .graphicsLayer { alpha = ((open - DJ_PLACES_FROM) / (1f - DJ_PLACES_FROM)).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
                content = places,
            )
        }
    }
}

/**
 * One place on a glide bar: its drawing in a circle. [chosen] grows and fills it ([dark]: in white, as Stop; else in
 * the accent); [now] rings it in the accent; [middle] (Start / Stop) is a little bigger. [onTap] makes it a button
 * (the queue's bars); [tag] names its test tags.
 */
@Composable
internal fun GlidePlace(
    icon: ImageVector,
    description: String,
    chosen: Boolean,
    now: Boolean,
    middle: Boolean = false,
    dark: Boolean = false,
    tag: String = "djBar",
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = Accent
    val size by animateDpAsState(
        when {
            chosen -> DJ_SPOT_CHOSEN
            middle -> DJ_SPOT_MIDDLE
            else -> DJ_SPOT
        },
        label = "djSpotSize",
    )
    val fill by animateColorAsState(
        when {
            chosen && dark -> Color.White
            chosen -> accent
            middle -> Color.White.copy(alpha = DJ_MIDDLE_TINT)
            else -> Color.White.copy(alpha = DJ_SPOT_TINT)
        },
        label = "djSpotFill",
    )
    val ink = when {
        chosen && dark -> DJ_STOP_INK
        chosen -> OnAccent
        else -> Color.White
    }
    Box(
        modifier
            .fillMaxHeight()
            .then(if (onTap != null) Modifier.clickable(onClickLabel = description, onClick = onTap) else Modifier)
            .semantics { contentDescription = description }
            .testTag(if (chosen) "${tag}Chosen" else "${tag}Place"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .then(if (now && !chosen) Modifier.border(DJ_NOW_RING, accent, CircleShape) else Modifier)
                .testTag(if (chosen) "${tag}ChosenCircle" else "${tag}Circle"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(DJ_SPOT_ICON))
        }
    }
}

/**
 * How much a DJ radio may stray from the mix's way (issue #649), as the queue's Surprises bar shows it: four places, the
 * setting each gives, and its drawing (more sparks, more surprises).
 */
enum class SurpriseLevel(val value: Int, val label: String) {
    NONE(0, "None"),
    FEW(25, "A few"),
    SOME(55, "Some"),
    LOTS(85, "Lots");

    val hint: String
        get() = when (this) {
            NONE -> "Only songs that follow the mix's way."
            FEW -> "Now and then a song from a little further off."
            SOME -> "Songs from further off, every few songs."
            LOTS -> "Plenty of songs you might not expect."
        }

    val icon: ImageVector get() = DjBarIcons.surprise(ordinal)

    companion object {
        /** The level a surprise setting (0 to 100) is at, as [surpriseLabel] names it. */
        fun of(surprise: Int): SurpriseLevel = entries.first { surpriseLabel(surprise) == it.label }
    }
}

/** The bar's drawings: the energy's way over the next songs, as the mockup drew them, and a stop square. */
internal object DjBarIcons {
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
    val stop by lazy {
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

    private fun spark(path: androidx.compose.ui.graphics.vector.PathBuilder, cx: Float, cy: Float, r: Float) = path.apply {
        moveTo(cx, cy - r)
        quadTo(cx, cy, cx + r, cy)
        quadTo(cx, cy, cx, cy + r)
        quadTo(cx, cy, cx - r, cy)
        quadTo(cx, cy, cx, cy - r)
        close()
    }

    private val sparks: List<ImageVector> by lazy {
        val layouts = listOf(
            emptyList(),
            listOf(Triple(12f, 12f, 7f)),
            listOf(Triple(9f, 14f, 6f), Triple(16.5f, 7.5f, 4.5f)),
            listOf(Triple(8.5f, 15f, 5.5f), Triple(16.5f, 15.5f, 4f), Triple(13f, 6.5f, 4.5f)),
        )
        layouts.mapIndexed { i, sparks ->
            ImageVector.Builder(name = "Siansa.Surprise.$i", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
                if (sparks.isEmpty()) {
                    // None: a small ring, nothing in it.
                    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                        moveTo(12f, 8f)
                        arcTo(4f, 4f, 0f, true, true, 11.99f, 8f)
                    }
                } else {
                    path(fill = SolidColor(Color.Black)) { sparks.forEach { (x, y, r) -> spark(this, x, y, r) } }
                }
            }.build()
        }
    }

    /** The Surprises bar's drawing for a level (0 none, 1 to 3 sparks). */
    fun surprise(level: Int): ImageVector = sparks[level.coerceIn(0, 3)]

    /** A shape's drawing ([DjBarSpot.MIX] draws a turntable or [stop] instead). */
    fun of(spot: DjBarSpot): ImageVector = when (spot) {
        DjBarSpot.STEADY -> steady
        DjBarSpot.BUILD -> build
        DjBarSpot.MIX -> stop
        DjBarSpot.EUPHORIC -> euphoric
        DjBarSpot.WIND_DOWN -> windDown
    }
}

/** How tall the seek bar's touch row is; its line runs through the middle. */
internal val SEEK_TRACK_HEIGHT = 28.dp

/** Where the seek bar's line is, from the top of the seek bar (its top padding, then half its touch row). */
internal val DJ_BAR_CENTRE: Dp = Space.Medium + SEEK_TRACK_HEIGHT / 2

/**
 * The bar's height, open: a chosen place ([DJ_SPOT_CHOSEN]) with [Space.Tight] above and below. Centred on the seek bar's
 * line, it ends [Space.Page] above the play button (the seek bar's 56 dp and the 8 dp under it), so it never crowds Play.
 */
internal val DJ_BAR_HEIGHT = 44.dp
private val DJ_TRACK_LINE = 3.dp
private val DJ_SPOT_CHOSEN = 36.dp
private val DJ_SPOT_MIDDLE = 30.dp
private val DJ_SPOT = 28.dp
private val DJ_SPOT_ICON = 18.dp
private val DJ_NOW_RING = 1.5.dp
private val DJ_BAR_EDGE = 1.dp

/** The panel is see-through (the owner): the page shows through its shade. */
private const val DJ_BAR_SHADE = 0.32f
private const val DJ_BAR_EDGE_ALPHA = 0.14f
/** The seek bar's line, as SeekBar draws it, which the bar grows out of. */
private const val DJ_TRACK_ALPHA = 0.22f
/** The places start to show once the bar is this far open. */
private const val DJ_PLACES_FROM = 0.35f
private const val DJ_SPOT_TINT = 0.12f
private const val DJ_MIDDLE_TINT = 0.2f
private val DJ_STOP_INK = Color(0xFF141414)
