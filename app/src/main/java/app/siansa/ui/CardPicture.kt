package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
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
 * four covers, its top album, its colours and name, one cover on its own light, its cover in two tones, three covers
 * fanned out, or its covers as stickers. [AUTO][PictureStyle.AUTO] shows a mix's mosaic only when its covers sit well
 * together ([mosaicSitsWell]), and Glow when they don't; an album keeps its cover.
 *
 * Its colours come from where its row says ([LocalPictureColours]): its covers' own made brighter, its songs' moods
 * ([songIds]), or a set. [covers] are its songs' covers, in order and with repeats (the most repeated is its top album);
 * [cover] stands in when there are none. [showName] puts [title] on it where the style has room for it and the card
 * doesn't already show it. [modifier] sizes it; [content] is drawn over it with its colours (a name on its own fade).
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
    // The card draws its name over the picture itself ([content]): Ink, Frost and Line then leave theirs out.
    nameOver: Boolean = false,
    style: PictureStyle = LocalPictureStyle.current,
    songIds: List<String> = emptyList(),
    content: @Composable androidx.compose.foundation.layout.BoxScope.(PicturePaint) -> Unit = {},
) {
    val draw = LocalCoverDraw.current
    val palette = LocalPictureColours.current
    val distinct = remember(covers) { covers.filterNotNull().distinct() }
    val top = remember(covers, cover) { topCover(covers, cover) }
    val mix = covers.isNotEmpty()
    // Decided once for these covers, so a card doesn't change while it's on screen.
    val shown = remember(style, distinct, top, mix) {
        pictureShown(style, distinct.size, top != null, mix) { distinct.take(4).map { CoverToneStore.get(it)?.main } }
    }
    val mood = if (palette == app.siansa.data.PictureColours.MOOD) rememberMoodPaint(songIds) else null
    val paint = remember(palette, mood, distinct, top, colors, title) {
        mood ?: setPaint(palette, title) ?: pictureColors(top, distinct, colors).let { PicturePaint.of(it.first, it.second) }
    }
    // Its own layer: drawn once and reused while a row scrolls, stickers and shadows too.
    Box(modifier.clip(shape).graphicsLayer {}.testTag("cardPicture-${shown.id}")) {
        when (shown) {
            PictureStyle.MOSAIC -> Column(Modifier.fillMaxSize()) {
                for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..1) draw(distinct[row * 2 + col], px / 2, Modifier.weight(1f).fillMaxSize(), RectangleShape, null)
                }
            }
            PictureStyle.COVER, PictureStyle.AUTO -> draw(top, px, Modifier.fillMaxSize(), RectangleShape, null)
            PictureStyle.INK, PictureStyle.FROST, PictureStyle.LINE -> SleekPicture(shown, paint, top, title, !nameOver, px)
            PictureStyle.GLOW -> {
                // Its light is its colours, not the cover blurred, so a dull cover glows too.
                Box(Modifier.fillMaxSize().glowFill(paint))
                draw(top, px, Modifier.align(Alignment.Center).fillMaxSize(GLOW_COVER).shadow(GLOW_SHADOW, RoundedCornerShape(4.dp)), RoundedCornerShape(4.dp), null)
            }
            PictureStyle.PILE, PictureStyle.TAPED, PictureStyle.CUTOUTS -> Stickers(shown, distinct, paint, title, showName, px)
        }
        content(paint)
    }
}

/** Its colours as soft light: the first from the top left, the second from the foot, on its deep shade. */
internal fun Modifier.meshFill(paint: PicturePaint): Modifier = drawBehind {
    drawRect(paint.deep)
    drawRect(Brush.radialGradient(listOf(paint.first, paint.first.copy(alpha = 0f)), Offset(size.width * 0.2f, size.height * 0.15f), size.width * 0.85f))
    drawRect(Brush.radialGradient(listOf(paint.second, paint.second.copy(alpha = 0f)), Offset(size.width * 0.95f, size.height * 0.85f), size.width * 0.7f))
}

/** Its colours as a glow behind its middle, for a cover to sit on. */
private fun Modifier.glowFill(paint: PicturePaint): Modifier = drawBehind {
    drawRect(paint.deep)
    drawRect(Brush.radialGradient(listOf(paint.first, paint.first.copy(alpha = 0f)), Offset(size.width * 0.38f, size.height * 0.34f), size.width * 0.62f))
    drawRect(Brush.radialGradient(listOf(paint.second, paint.second.copy(alpha = 0f)), Offset(size.width * 0.7f, size.height * 0.7f), size.width * 0.55f))
}

/**
 * The fade under a name over a picture (the owner, 20 Nov: "bad fades"): to [shade], its own deep colour, not black,
 * easing in over seven steps so there's no line where it starts.
 */
internal fun nameFade(shade: Color): Brush = Brush.verticalGradient(
    0f to shade.copy(alpha = 0f), 0.15f to shade.copy(alpha = 0.08f), 0.3f to shade.copy(alpha = 0.25f), 0.48f to shade.copy(alpha = 0.5f),
    0.66f to shade.copy(alpha = 0.74f), 0.84f to shade.copy(alpha = 0.9f), 1f to shade.copy(alpha = 0.96f),
)

/**
 * The sleek pictures (the owner, 20 Nov: the colour cards "don't suit the sleek style of the app"): black first, its
 * colour as a hint. [INK][PictureStyle.INK]: its colour glowing up from the foot, a short bar of it, its name at the top.
 * [FROST][PictureStyle.FROST]: its cover blurred behind dark glass, the cover small and sharp, its name at the foot.
 * [LINE][PictureStyle.LINE]: a thin edge in its colour, "MIX" in it, its name big at the foot.
 */
@Composable
private fun SleekPicture(kind: PictureStyle, paint: PicturePaint, top: String?, title: String, named: Boolean, px: Int) {
    val draw = LocalCoverDraw.current
    val accent = paint.first
    BoxWithConstraints(Modifier.fillMaxSize().background(SLEEK_GROUND)) {
        val roomy = maxWidth >= COLOUR_MEDIUM
        val inset = if (roomy) Space.Medium else Space.Small
        val showName = named && maxWidth >= COLOUR_SMALLEST
        val type = (if (maxWidth >= COLOUR_BIG) MaterialTheme.typography.titleLarge else if (roomy) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium)
            .copy(fontWeight = FontWeight.ExtraBold)
        when (kind) {
            PictureStyle.INK -> {
                // One soft light, wide and low, so there's little to band or shimmer as it moves.
                Box(Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0f)), Offset(size.width / 2f, size.height * 1.05f), size.width * 0.72f))
                })
                if (showName) WholeWordsText(title, type, Modifier.align(Alignment.TopStart).padding(inset), color = SLEEK_TEXT, maxLines = 3)
                Box(Modifier.align(Alignment.BottomStart).padding(inset).size(INK_BAR_WIDTH, INK_BAR_HEIGHT).clip(RoundedCornerShape(2.dp)).background(accent))
            }
            PictureStyle.FROST -> {
                draw(top, FROST_LIGHT_PX, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f; alpha = 0.8f }
                    .then(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Modifier.blur(FROST_BLUR) else Modifier), RectangleShape, null)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SLEEK_GROUND.copy(alpha = 0.15f), SLEEK_GROUND.copy(alpha = 0.72f)))))
                draw(top, px / 2, Modifier.align(Alignment.TopStart).padding(inset).fillMaxSize(FROST_COVER).shadow(FROST_SHADOW, RoundedCornerShape(4.dp)), RoundedCornerShape(4.dp), null)
                if (showName) WholeWordsText(title, type, Modifier.align(Alignment.BottomStart).padding(inset), color = SLEEK_TEXT, maxLines = 3)
            }
            else -> {
                Box(Modifier.fillMaxSize().border(LINE_EDGE, accent, RoundedCornerShape(8.dp)))
                if (roomy) Text("MIX", Modifier.align(Alignment.TopStart).padding(inset), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = accent, letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp))
                if (showName) WholeWordsText(title, type.copy(fontWeight = FontWeight.Black), Modifier.align(Alignment.BottomStart).padding(inset), color = SLEEK_TEXT, maxLines = 3)
            }
        }
    }
}

/** Where one sticker sits: its left and top as a share of the picture, its width (and height), its angle, round or not. */
internal class StickerSpot(val x: Float, val y: Float, val w: Float, val h: Float, val angle: Float, val round: Boolean = false)

/**
 * The stickers of [kind] for a card (the owner, 20 Nov: "several covers to create a sort of plastered sticker image"):
 * where each sits and its angle come from the card itself ([seed]), so no two cards look the same and one always looks
 * the same.
 */
internal fun stickerSpots(kind: PictureStyle, seed: Int): List<StickerSpot> {
    val jitter = { i: Int -> (Math.floorMod(seed * 31 + i * 17, 9) - 4).toFloat() }
    val base = when (kind) {
        PictureStyle.PILE -> listOf(
            StickerSpot(-0.08f, 0.04f, 0.52f, 0.52f, -12f), StickerSpot(0.42f, -0.06f, 0.52f, 0.52f, 9f), StickerSpot(0.18f, 0.30f, 0.52f, 0.52f, -4f),
            StickerSpot(-0.10f, 0.52f, 0.52f, 0.52f, 14f), StickerSpot(0.50f, 0.46f, 0.52f, 0.52f, -10f),
        )
        PictureStyle.TAPED -> listOf(
            StickerSpot(0.06f, 0.06f, 0.5f, 0.5f, -6f), StickerSpot(0.46f, 0.04f, 0.5f, 0.5f, 5f),
            StickerSpot(0.10f, 0.44f, 0.5f, 0.5f, 4f), StickerSpot(0.48f, 0.42f, 0.5f, 0.5f, -7f),
        )
        else -> listOf(
            StickerSpot(-0.10f, -0.08f, 0.62f, 0.62f, 0f, round = true), StickerSpot(0.52f, 0.06f, 0.40f, 0.52f, 7f),
            StickerSpot(0.30f, 0.46f, 0.34f, 0.34f, -9f), StickerSpot(0.64f, 0.58f, 0.30f, 0.30f, 0f, round = true),
        )
    }
    // The pile's order turns with the card, so its top sticker differs from card to card.
    val turn = if (kind == PictureStyle.PILE) Math.floorMod(seed, base.size) else 0
    return base.indices.map { i -> base[(i + turn) % base.size].let { StickerSpot(it.x, it.y, it.w, it.h, if (it.round) 0f else it.angle + jitter(i), it.round) } }
}

@Composable
private fun Stickers(kind: PictureStyle, covers: List<String>, paint: PicturePaint, title: String, showName: Boolean, px: Int) {
    val draw = LocalCoverDraw.current
    val seed = title.hashCode()
    val spots = remember(kind, seed) { stickerSpots(kind, seed) }
    val taped = kind == PictureStyle.TAPED
    BoxWithConstraints(Modifier.fillMaxSize().then(if (taped) Modifier.background(Brush.linearGradient(listOf(PAPER_LIGHT, PAPER_DARK))) else Modifier.meshFill(paint))) {
        val edge = max(STICKER_EDGE_LEAST.value, maxWidth.value / STICKER_EDGE_SHARE).dp
        val wide = maxWidth
        val high = maxHeight
        spots.forEachIndexed { i, spot ->
            val shape = if (spot.round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(3.dp)
            val id = covers[Math.floorMod(i + seed, covers.size)]
            Box(
                Modifier.offset(x = maxWidth * spot.x, y = maxHeight * spot.y).size(maxWidth * spot.w, maxHeight * spot.h)  // spacing: exact (a sticker's place)
                    .graphicsLayer { rotationZ = spot.angle },
            ) {
                val stuck = Modifier.fillMaxSize().shadow(STICKER_SHADOW, shape)
                draw(id, px / 2, if (taped) stuck else stuck.border(edge, STICKER_EDGE, shape), shape, null)
                if (taped) {
                    // A strip of tape over its top edge.
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(y = -(high * TAPE_HEIGHT) / 2).size(wide * spot.w * TAPE_WIDTH, high * TAPE_HEIGHT)  // spacing: exact (the tape)
                            .graphicsLayer { rotationZ = spot.angle * 2 - 8f }.background(TAPE),
                    )
                }
            }
        }
        if (showName && maxWidth >= COLOUR_SMALLEST) {
            if (taped) {
                // Its name on a label stuck on the paper.
                Text(title, style = (if (maxWidth >= COLOUR_BIG) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium).copy(hyphens = androidx.compose.ui.text.style.Hyphens.None),
                    fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Space.Small).graphicsLayer { rotationZ = -2f }.background(LABEL).padding(horizontal = Space.Small, vertical = Space.Tight))
            } else {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(nameFade(paint.deep)).padding(start = Space.Small, end = Space.Small, top = Space.Section, bottom = Space.Small)) {
                    WholeWordsText(title, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = Color.White, maxLines = 2)
                }
            }
        }
    }
}

/**
 * What [style] draws for a card with [distinct] different covers: a style it hasn't the covers for falls back to one
 * cover, and to its colours when it has none. [AUTO][PictureStyle.AUTO] gives an album ([mix] false) its cover, and a
 * mix its mosaic only when [tones] (the first four covers' main colours, null where not known yet) sit well together,
 * else Glow (the owner, 20 Nov: one flat cover looked poor).
 */
internal fun pictureShown(style: PictureStyle, distinct: Int, hasCover: Boolean, mix: Boolean = true, tones: () -> List<Color?>): PictureStyle {
    val one = if (hasCover) PictureStyle.COVER else PictureStyle.INK
    return when (style) {
        PictureStyle.AUTO -> when {
            !hasCover -> PictureStyle.INK
            !mix -> PictureStyle.COVER
            distinct >= 4 && mosaicSitsWell(tones()) -> PictureStyle.MOSAIC
            else -> PictureStyle.GLOW
        }
        PictureStyle.MOSAIC -> if (distinct >= 4) PictureStyle.MOSAIC else one
        PictureStyle.PILE, PictureStyle.TAPED, PictureStyle.CUTOUTS -> if (distinct >= 2) style else one
        PictureStyle.COVER, PictureStyle.GLOW, PictureStyle.FROST -> if (hasCover) style else PictureStyle.INK
        PictureStyle.INK, PictureStyle.LINE -> style
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
private const val GLOW_COVER = 0.56f
private val GLOW_SHADOW = 12.dp
private val COLOUR_BIG = 160.dp
private val COLOUR_MEDIUM = 96.dp
private val COLOUR_SMALLEST = 64.dp
private val STICKER_SHADOW = 5.dp
private val STICKER_EDGE_LEAST = 2.dp
private const val STICKER_EDGE_SHARE = 60f
private val STICKER_EDGE = Color(0xFFF6F3EC)
private const val TAPE_WIDTH = 0.36f
private const val TAPE_HEIGHT = 0.07f
private val TAPE = Color(0xA6FFECAA)
private val PAPER_LIGHT = Color(0xFFEFE6D2)
private val PAPER_DARK = Color(0xFFD9CBAE)
private val LABEL = Color(0xFF1A1206)
private val SLEEK_GROUND = Color(0xFF131315)
private val SLEEK_TEXT = Color(0xFFF2F2F3)
private val INK_BAR_WIDTH = 18.dp
private val INK_BAR_HEIGHT = 3.dp
private val LINE_EDGE = 1.5.dp
private const val FROST_LIGHT_PX = 64
private const val FROST_COVER = 0.38f
private val FROST_BLUR = 18.dp
private val FROST_SHADOW = 8.dp
