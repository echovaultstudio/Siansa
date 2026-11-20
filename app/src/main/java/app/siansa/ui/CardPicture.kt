package app.siansa.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.PictureStyle
import kotlin.math.max
import kotlin.math.min

/** How the card pictures of the row being drawn show (the owner, 20 Nov), from its cog in Arrange Home. */
val LocalPictureStyle = compositionLocalOf { PictureStyle.AUTO }

/** Draws one cover: its id, size in pixels, where, its shape and an optional colour filter. */
typealias CoverDraw = @Composable (String?, Int, Modifier, Shape, ColorFilter?) -> Unit

/** The app's [Cover]; screenshot scenes, which have no server, draw their own. */
val LocalCoverDraw = staticCompositionLocalOf<CoverDraw> { { id, px, modifier, shape, filter -> Cover(id, px, modifier, shape, filter) } }

/**
 * A card's picture in its row's [PictureStyle] (the owner, 20 Nov: "some do not look good with the cover mosaic"):
 * four covers, its top album, its colours and name, one cover on its own light, its cover in two tones, or three covers
 * fanned out. [AUTO][PictureStyle.AUTO] shows the mosaic only when its covers sit well together ([mosaicSitsWell]).
 *
 * [covers] are its songs' covers, in order and with repeats (the most repeated is its top album); [cover] stands in when
 * there are none. [showName] puts [title] on it where the style has room for it (Colour, Duotone) and the card doesn't
 * already show it over the picture. [modifier] sizes it.
 */
@Composable
fun CardPicture(
    covers: List<String?>,
    cover: String?,
    colors: Pair<Color, Color>,
    title: String,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    px: Int = 300,
    showName: Boolean = false,
    style: PictureStyle = LocalPictureStyle.current,
) {
    val draw = LocalCoverDraw.current
    val distinct = remember(covers) { covers.filterNotNull().distinct() }
    val top = remember(covers, cover) { topCover(covers, cover) }
    // Decided once for these covers, so a card doesn't change while it's on screen.
    val shown = remember(style, distinct, top) {
        pictureShown(style, distinct.size, top != null) { distinct.take(4).map { CoverToneStore.get(it)?.main } }
    }
    val tones = remember(distinct, top, colors) { pictureColors(top, distinct, colors) }
    Box(modifier.clip(shape).testTag("cardPicture-${shown.id}")) {
        when (shown) {
            PictureStyle.MOSAIC -> Column(Modifier.fillMaxSize()) {
                for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..1) draw(distinct[row * 2 + col], px / 2, Modifier.weight(1f).fillMaxSize(), RectangleShape, null)
                }
            }
            PictureStyle.COVER, PictureStyle.AUTO -> draw(top, px, Modifier.fillMaxSize(), RectangleShape, null)
            PictureStyle.COLOUR -> ColourPicture(tones, title, showName)
            PictureStyle.GLOW -> {
                Box(Modifier.fillMaxSize().background(lerp(tones.first, Color.Black, 0.55f)))
                // The cover itself, small and soft, as its light: blurred where Android can, and drawn from a tiny
                // picture everywhere, so it's soft even where it can't.
                draw(top, GLOW_LIGHT_PX, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f; alpha = 0.85f }
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(GLOW_BLUR) else Modifier), RectangleShape, null)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
                draw(top, px, Modifier.align(Alignment.Center).fillMaxSize(GLOW_COVER).shadow(GLOW_SHADOW, RoundedCornerShape(4.dp)), RoundedCornerShape(4.dp), null)
            }
            PictureStyle.DUOTONE -> {
                val filter = remember(tones) { duotone(lerp(tones.first, Color.Black, 0.78f), lerp(tones.first, Color.White, 0.45f)) }
                draw(top, px, Modifier.fillMaxSize(), RectangleShape, filter)
                if (showName) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = Space.Small, vertical = Space.Tight)) {
                        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            PictureStyle.STACK -> BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(tones.first, Color.Black, 0.72f), lerp(tones.second, Color.Black, 0.82f))))) {
                val step = maxWidth * STACK_STEP
                val sleeve = RoundedCornerShape(4.dp)
                val back = distinct.getOrNull(2) ?: distinct.getOrNull(1)
                val middle = distinct.firstOrNull { it != top } ?: distinct.first()
                draw(back, px / 2, Modifier.align(Alignment.Center).offset(x = -step, y = step / 3).fillMaxSize(STACK_SLEEVE)
                    .graphicsLayer { rotationZ = -10f }.shadow(STACK_SHADOW, sleeve), sleeve, null)
                draw(middle, px / 2, Modifier.align(Alignment.Center).offset(x = step, y = -step / 4).fillMaxSize(STACK_SLEEVE)
                    .graphicsLayer { rotationZ = 8f }.shadow(STACK_SHADOW, sleeve), sleeve, null)
                draw(top, px, Modifier.align(Alignment.Center).offset(y = step / 3).fillMaxSize(STACK_SLEEVE).shadow(STACK_SHADOW, sleeve), sleeve, null)
            }
        }
    }
}

/** Its colours, light at the top and deeper at the foot, its name big on them. */
@Composable
private fun ColourPicture(tones: Pair<Color, Color>, title: String, showName: Boolean) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(lerp(tones.first, Color.White, 0.08f), lerp(tones.first, Color.Black, 0.3f), lerp(tones.second, Color.Black, 0.5f))),
        ),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (!showName || maxWidth < COLOUR_SMALLEST) return@BoxWithConstraints
        val type = when {
            maxWidth >= COLOUR_BIG -> MaterialTheme.typography.headlineSmall
            maxWidth >= COLOUR_MEDIUM -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.labelLarge
        }
        // Whole words, a size smaller where a long one wouldn't fit, as on a carousel's sleeves.
        WholeWordsText(
            title, type.copy(fontWeight = FontWeight.Black),
            Modifier.padding(if (maxWidth >= COLOUR_MEDIUM) Space.Medium else Space.Small),
            color = Color.White, maxLines = 3,
        )
    }
}

/**
 * What [style] draws for a card with [distinct] different covers: a style it hasn't the covers for falls back to one
 * cover, and to its colours when it has none. [AUTO][PictureStyle.AUTO] takes the mosaic only when [tones] (the first
 * four covers' main colours, null where not known yet) sit well together.
 */
internal fun pictureShown(style: PictureStyle, distinct: Int, hasCover: Boolean, tones: () -> List<Color?>): PictureStyle {
    val one = if (hasCover) PictureStyle.COVER else PictureStyle.COLOUR
    return when (style) {
        PictureStyle.AUTO -> if (distinct >= 4 && mosaicSitsWell(tones())) PictureStyle.MOSAIC else one
        PictureStyle.MOSAIC -> if (distinct >= 4) PictureStyle.MOSAIC else one
        PictureStyle.STACK -> if (distinct >= 2) PictureStyle.STACK else one
        PictureStyle.COVER, PictureStyle.GLOW, PictureStyle.DUOTONE -> if (hasCover) style else PictureStyle.COLOUR
        PictureStyle.COLOUR -> PictureStyle.COLOUR
    }
}

/**
 * Whether four covers look good two by two: not one very light beside one very dark, and their colours close on the
 * colour wheel (black, white and grey go with anything). Covers whose colours aren't known yet don't count against it.
 */
internal fun mosaicSitsWell(tones: List<Color?>): Boolean {
    val known = tones.filterNotNull().filter { it != Color.Unspecified }
    if (known.size < 2) return true
    val light = known.map { it.luminance() }
    if (light.max() - light.min() > MOSAIC_LIGHT_SPREAD) return false
    val hues = known.filter { chroma(it) >= MOSAIC_COLOURFUL }.map { hue(it) }
    return hues.size < 2 || hueRange(hues) <= MOSAIC_HUE_SPREAD
}

/** The cover most of its songs have (its top album), or [fallback] when none has one. */
internal fun topCover(covers: List<String?>, fallback: String?): String? {
    val counts = covers.filterNotNull().groupingBy { it }.eachCount()
    val most = counts.maxOfOrNull { it.value } ?: return fallback
    return covers.firstOrNull { it != null && counts[it] == most } ?: fallback
}

/** Its colours from its covers' own, where known; else [fallback] (the card's colours). */
internal fun pictureColors(top: String?, distinct: List<String>, fallback: Pair<Color, Color>): Pair<Color, Color> {
    val mains = (listOfNotNull(top) + distinct).distinct().mapNotNull { id -> CoverToneStore.get(id)?.main?.takeIf { it != Color.Unspecified } }
    val first = mains.firstOrNull() ?: return fallback
    return first to (mains.drop(1).firstOrNull { colourDistance(it, first) > 0.15f } ?: lerp(first, fallback.second, 0.5f))
}

/** Grey picture to two colours: its shadows [dark], its lights [light]. */
internal fun duotone(dark: Color, light: Color): ColorFilter {
    fun row(d: Float, l: Float) = floatArrayOf((l - d) * 0.299f, (l - d) * 0.587f, (l - d) * 0.114f, 0f, d * 255f)
    return ColorFilter.colorMatrix(ColorMatrix(row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
}

internal fun chroma(c: Color): Float = max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue))

/** [c]'s hue, 0 to 360. */
internal fun hue(c: Color): Float {
    val hi = max(c.red, max(c.green, c.blue))
    val d = chroma(c)
    if (d == 0f) return 0f
    val h = when (hi) {
        c.red -> ((c.green - c.blue) / d).mod(6f)
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    }
    return h * 60f
}

/** How far round the colour wheel [hues] spread: 360 less the widest gap between two of them. */
internal fun hueRange(hues: List<Float>): Float {
    val sorted = hues.sorted()
    val gaps = sorted.zipWithNext { a, b -> b - a } + (sorted.first() + 360f - sorted.last())
    return 360f - gaps.max()
}

private fun colourDistance(a: Color, b: Color): Float = max(kotlin.math.abs(a.red - b.red), max(kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue)))

private const val MOSAIC_LIGHT_SPREAD = 0.45f
private const val MOSAIC_COLOURFUL = 0.18f
private const val MOSAIC_HUE_SPREAD = 110f
private const val GLOW_LIGHT_PX = 48
private const val GLOW_COVER = 0.56f
private const val STACK_SLEEVE = 0.54f
private const val STACK_STEP = 0.16f
private val GLOW_BLUR = 24.dp
private val GLOW_SHADOW = 12.dp
private val STACK_SHADOW = 8.dp
private val COLOUR_BIG = 160.dp
private val COLOUR_MEDIUM = 96.dp
private val COLOUR_SMALLEST = 64.dp
