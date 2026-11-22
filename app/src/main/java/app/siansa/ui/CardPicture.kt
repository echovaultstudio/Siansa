package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
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

/** How the cards at the foot of the row's hero show their pictures (the owner, 22 Nov): chosen apart from the hero's. */
val LocalHeroCardPictures = compositionLocalOf { PictureStyle.AUTO }

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
    // Where its name goes (the owner, 21 Nov: the Names choice): true on it (each picture its own way, a name on a
    // fade where the picture has none), false nowhere on it (the card puts it under), null as the picture does.
    names: Boolean? = null,
    // Its line under its name, where its name is on it.
    line: String = "",
    style: PictureStyle = LocalPictureStyle.current,
    songIds: List<String> = emptyList(),
    // Its main artists, for Frost's glass and the Poster ("Sleep Token, Deftones, Loathe"): [artistLine].
    artists: String = "",
    content: @Composable androidx.compose.foundation.layout.BoxScope.(PicturePaint) -> Unit = {},
) {
    val draw = LocalCoverDraw.current
    val palette = LocalPictureColours.current
    val ownName = names != false && !nameOver
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
            PictureStyle.AURA -> AuraPicture(paint, title, ownName)
            PictureStyle.FROST -> FrostPicture(paint, top, title, artists, ownName, px)
            PictureStyle.POSTER -> PosterPicture(paint, distinct.ifEmpty { listOfNotNull(top) }, title, artists, ownName, px)
            PictureStyle.GLOW -> {
                // Its light is its colours, not the cover blurred, so a dull cover glows too.
                Box(Modifier.fillMaxSize().glowFill(paint))
                draw(top, px, Modifier.align(Alignment.Center).fillMaxSize(GLOW_COVER).shadow(GLOW_SHADOW, RoundedCornerShape(4.dp)), RoundedCornerShape(4.dp), null)
            }
            PictureStyle.PILE, PictureStyle.TAPED, PictureStyle.CUTOUTS -> Stickers(shown, distinct, paint, title, names ?: showName, px)
        }
        // A picture with no name of its own gets one on a fade of its own colour, when names go on the picture.
        if (names == true && !nameOver && shown in UNNAMED) NameOnPicture(title, line, paint)
        content(paint)
    }
}

/**
 * Whether a card's picture in [style] puts its name on itself (Aura, Frost, Poster), with the row's Names on Auto: its
 * card then shows just its line under it, not the name a second time (the owner, 21 Nov: names under were cut off and
 * said twice).
 */
internal fun pictureNamesItself(style: PictureStyle, covers: List<String?>, cover: String?): Boolean {
    val distinct = covers.filterNotNull().distinct()
    return pictureShown(style, distinct.size, topCover(covers, cover) != null, covers.isNotEmpty()) { emptyList() } in SELF_NAMED
}

private val SELF_NAMED = setOf(PictureStyle.AURA, PictureStyle.FROST, PictureStyle.POSTER)

private val UNNAMED = setOf(PictureStyle.AUTO, PictureStyle.MOSAIC, PictureStyle.COVER, PictureStyle.GLOW)

@Composable
private fun androidx.compose.foundation.layout.BoxScope.NameOnPicture(title: String, line: String, paint: PicturePaint) {
    BoxWithConstraints(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
        val wide = maxWidth
        if (wide < COLOUR_SMALLEST) return@BoxWithConstraints
        val roomy = wide >= COLOUR_MEDIUM
        Column(
            Modifier.fillMaxWidth().background(nameFade(paint.deep))
                .padding(start = Space.Small, end = Space.Small, top = if (roomy) Space.Large else Space.Section, bottom = Space.Small),
            verticalArrangement = Arrangement.spacedBy(Space.Tight),
        ) {
            WholeWordsText(title, (if (wide >= COLOUR_BIG) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge).copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White, maxLines = 3)
            if (roomy && line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Where the cards of the row being drawn show their names (the owner, 21 Nov). */
val LocalCardNames = androidx.compose.runtime.compositionLocalOf { app.siansa.data.CardNames.AUTO }

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
 * Whether the cards may move now: Home says no while it scrolls ([LocalPictureMotion]), and Android's "remove
 * animations" says no for good.
 */
val LocalPictureMotion = androidx.compose.runtime.compositionLocalOf { true }

@Composable
private fun animationsOn(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/**
 * A slow clock for a moving card, 0 to 1 every [periodMs]: it starts where [seed] says, so cards beside each other
 * don't move together, and only runs while the card may move ([LocalPictureMotion]). Read it only while drawing, so
 * the card is redrawn, not composed again, at about 30 frames a second.
 */
@Composable
private fun rememberDrift(periodMs: Int, seed: Int): androidx.compose.runtime.State<Float> {
    val phase = remember { androidx.compose.runtime.mutableFloatStateOf(Math.floorMod(seed, 1000) / 1000f) }
    val moving = LocalPictureMotion.current && animationsOn()
    androidx.compose.runtime.LaunchedEffect(moving, periodMs) {
        if (!moving) return@LaunchedEffect
        var last = androidx.compose.animation.core.withInfiniteAnimationFrameNanos { it }
        while (true) {
            val now = androidx.compose.animation.core.withInfiniteAnimationFrameNanos { it }
            if (now - last >= DRIFT_FRAME_NANOS) {
                phase.floatValue = (phase.floatValue + (now - last) / 1_000_000f / periodMs) % 1f
                last = now
            }
        }
    }
    return phase
}

/** A mix's main artists, most songs first: "Sleep Token, Deftones, Loathe". */
fun artistLine(songs: List<app.siansa.data.Song>, most: Int = 3): String =
    songs.map { it.artist }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }.take(most).joinToString(", ") { it.key }

/** Fine grain over a moving card, so its soft colours never show steps. */
private val GRAIN: androidx.compose.ui.graphics.ImageBitmap by lazy {
    val size = 96
    val random = java.util.Random(7)
    val pixels = IntArray(size * size) { if (random.nextBoolean()) 0x22FFFFFF else 0x22000000 }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.grain() {
    drawRect(androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(GRAIN, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)), alpha = GRAIN_ALPHA)
}

/**
 * Aura (the owner, 21 Nov, after Apple Music's moving mixes): layers of its colours drifting like light through water,
 * slow for a calm mix and quicker for a loud one ([PicturePaint.energy]), fine grain over them, its name big and tight
 * at the foot.
 */
@Composable
private fun AuraPicture(paint: PicturePaint, title: String, named: Boolean) {
    val period = (AURA_SLOW_MS - (AURA_SLOW_MS - AURA_QUICK_MS) * paint.energy).toInt()
    val drift = rememberDrift(period, title.hashCode())
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            val a = drift.value * 2f * Math.PI.toFloat()
            val w = size.width
            val h = size.height
            drawRect(lerp(paint.deep, Color.Black, 0.35f))
            fun blob(c: Color, x: Float, y: Float, r: Float) = drawRect(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(x * w, y * h), r * w))
            blob(paint.first, 0.28f + 0.18f * kotlin.math.sin(a), 0.3f + 0.12f * kotlin.math.cos(a), 0.9f)
            blob(paint.second, 0.72f + 0.16f * kotlin.math.cos(a + 1f), 0.74f + 0.14f * kotlin.math.sin(a + 1f), 0.85f)
            blob(paint.first.copy(alpha = 0.6f), 0.55f + 0.22f * kotlin.math.sin(2f * a + 2f), 0.2f + 0.16f * kotlin.math.cos(a + 2f), 0.5f)
            drawRect(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f)))
            grain()
        },
    ) {
        val roomy = maxWidth >= COLOUR_MEDIUM
        val inset = if (roomy) Space.Medium else Space.Small
        // No "SIANSA" over it (the owner, 22 Nov: "we don't need to ever show that"): the name alone.
        if (named && maxWidth >= COLOUR_SMALLEST) {
            val type = if (maxWidth >= COLOUR_BIG) MaterialTheme.typography.headlineMedium else if (roomy) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall
            WholeWordsText(title, type.copy(fontWeight = FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(-0.4f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = type.fontSize * 1.02f),
                Modifier.align(Alignment.BottomStart).padding(inset), color = Color.White, maxLines = 3)
        }
    }
}

/**
 * Frost (the owner, 21 Nov): its cover blurred to fill the card and slowly breathing, the sleeve floating on it with a
 * slight tilt, its name and main artists on a pane of glass.
 */
@Composable
private fun FrostPicture(paint: PicturePaint, top: String?, title: String, artists: String, named: Boolean, px: Int) {
    val draw = LocalCoverDraw.current
    val drift = rememberDrift(FROST_BREATH_MS, title.hashCode())
    BoxWithConstraints(Modifier.fillMaxSize().background(lerp(paint.deep, Color.Black, 0.3f))) {
        val wide = maxWidth
        val roomy = wide >= COLOUR_MEDIUM
        draw(top, FROST_LIGHT_PX, Modifier.fillMaxSize().graphicsLayer {
            val breath = 1.25f + 0.1f * kotlin.math.sin(drift.value * 2f * Math.PI.toFloat())
            scaleX = breath; scaleY = breath; alpha = 0.9f
        }.then(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Modifier.blur(FROST_BLUR) else Modifier), RectangleShape, null)
        // A soft light from the top, and the foot a little darker for the glass.
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent), Offset(size.width / 2f, 0f), size.width))
            drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)))
            grain()
        })
        draw(top, px / 2, Modifier.align(Alignment.TopCenter).offset(y = maxHeight * FROST_SLEEVE_TOP).fillMaxSize(if (named && roomy) FROST_SLEEVE else FROST_SLEEVE_ALONE)  // spacing: exact (where the sleeve floats)
            .graphicsLayer { rotationX = 6f; cameraDistance = 12f * density }.shadow(FROST_SHADOW, RoundedCornerShape(5.dp)), RoundedCornerShape(5.dp), null)
        if (named && maxWidth >= COLOUR_SMALLEST) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.Small).clip(RoundedCornerShape(8.dp))
                    .background(GLASS).border(GLASS_EDGE, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = Space.Small, vertical = Space.Tight + Space.Tight / 2),
            ) {
                Text(title, style = (if (wide >= COLOUR_BIG) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium).copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (wide >= ARTISTS_FROM && artists.isNotBlank()) Text(artists, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Poster (the owner, 21 Nov, after Tidal's mixes): three of its covers washed in one bold colour of it, its name in
 * heavy type on that colour below, and its main artists.
 */
@Composable
private fun PosterPicture(paint: PicturePaint, covers: List<String>, title: String, artists: String, named: Boolean, px: Int) {
    val draw = LocalCoverDraw.current
    val accent = paint.first
    val ink = if (accent.luminance() > POSTER_DARK_TEXT) POSTER_INK else Color.White
    val wash = remember(accent) { wash(lerp(accent, Color.Black, 0.82f), lerp(accent, Color.White, 0.55f)) }
    fun cover(i: Int) = covers.getOrNull(i % covers.size.coerceAtLeast(1))
    BoxWithConstraints(Modifier.fillMaxSize().background(accent)) {
        val wide = maxWidth
        val roomy = wide >= COLOUR_MEDIUM
        Box(Modifier.fillMaxWidth().fillMaxHeight(if (named) POSTER_IMAGE else 1f)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(POSTER_HAIRLINE)) {  // spacing: exact (a hairline between covers)
                draw(cover(0), px / 2, Modifier.weight(1.3f).fillMaxHeight(), RectangleShape, wash)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(POSTER_HAIRLINE)) {  // spacing: exact (a hairline between covers)
                    draw(cover(1), px / 3, Modifier.weight(1f).fillMaxWidth(), RectangleShape, wash)
                    draw(cover(2), px / 3, Modifier.weight(1f).fillMaxWidth(), RectangleShape, wash)
                }
            }
            if (named) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to accent)))
        }
        if (named && wide >= COLOUR_SMALLEST) {
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = if (roomy) Space.Medium else Space.Small, vertical = Space.Small)) {
                val type = if (wide >= COLOUR_BIG) MaterialTheme.typography.headlineSmall else if (roomy) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge
                WholeWordsText(title, type.copy(fontWeight = FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(-0.4f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = type.fontSize * 1.02f),
                    color = ink, maxLines = 3)
                if (wide >= ARTISTS_FROM && artists.isNotBlank()) Text(artists, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A cover in grey, its shadows [dark] and its lights [light]: washed in one colour. */
internal fun wash(dark: Color, light: Color): ColorFilter {
    fun row(d: Float, l: Float) = floatArrayOf((l - d) * 0.299f, (l - d) * 0.587f, (l - d) * 0.114f, 0f, d * 255f)
    return ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
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
    val one = if (hasCover) PictureStyle.COVER else PictureStyle.AURA
    return when (style) {
        PictureStyle.AUTO -> when {
            !hasCover -> PictureStyle.AURA
            !mix -> PictureStyle.COVER
            distinct >= 4 && mosaicSitsWell(tones()) -> PictureStyle.MOSAIC
            else -> PictureStyle.GLOW
        }
        PictureStyle.MOSAIC -> if (distinct >= 4) PictureStyle.MOSAIC else one
        PictureStyle.PILE, PictureStyle.TAPED, PictureStyle.CUTOUTS -> if (distinct >= 2) style else one
        PictureStyle.COVER, PictureStyle.GLOW, PictureStyle.FROST, PictureStyle.POSTER -> if (hasCover) style else PictureStyle.AURA
        PictureStyle.AURA -> style
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
private const val FROST_LIGHT_PX = 64
private val FROST_BLUR = 18.dp
private val FROST_SHADOW = 8.dp
private const val DRIFT_FRAME_NANOS = 33_000_000L
private const val GRAIN_ALPHA = 0.22f
private const val AURA_SLOW_MS = 22_000f
private const val AURA_QUICK_MS = 8_000f
private const val FROST_BREATH_MS = 14_000
private const val FROST_SLEEVE = 0.48f
private const val FROST_SLEEVE_ALONE = 0.6f
private const val FROST_SLEEVE_TOP = 0.11f
private val GLASS = Color(0x61141418)
private val GLASS_EDGE = 0.5.dp
private const val POSTER_IMAGE = 0.62f
private val POSTER_HAIRLINE = 2.dp
private const val POSTER_DARK_TEXT = 0.4f
private val POSTER_INK = Color(0xFF0B0B0C)
// Its artists under its name only where there's room for them to be read.
private val ARTISTS_FROM = 136.dp
