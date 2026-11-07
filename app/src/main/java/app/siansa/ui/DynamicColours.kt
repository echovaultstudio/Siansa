package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import app.siansa.data.LocalPlaylists
import app.siansa.log.AppLog
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Now playing's colours from the cover (issue #970): [controls] for the seek bar and the Play button, [text] for the
 * device name under them (text needs more contrast), and [onControls] for the play symbol on the button.
 */
data class DynamicColours(val controls: Color, val text: Color, val onControls: Color)

/** One of a cover's colours and how much of the cover it covers (Android's palette swatch), for [DynamicAccent.seedOf]. */
data class CoverSwatch(val color: Color, val population: Int)

/**
 * The cover's colour made to stand out on now playing (issue #970). The page behind the controls is the cover itself,
 * blurred and darkened ([backdropOf]), so the colour is the cover's own ([seedOf]), lightened until it stands out from
 * that by [CONTROLS_CONTRAST] (the seek bar, Play) and [TEXT_CONTRAST] (the device name), as accessibility asks of
 * controls and text.
 *
 * The colour is the one that fills most of the cover, as much as its liveliest (issue #994): a sepia cover with a red
 * light in it is tan, not red, and a muted colour stays muted. A cover in black, white and grey gets silver, from its
 * own greys: the theme's amber came from nowhere on it. Only a cover that couldn't be read keeps the theme's colour.
 */
object DynamicAccent {
    const val CONTROLS_CONTRAST = 3.0
    const val TEXT_CONTRAST = 4.5

    /** Below this chroma (the most and least of red, green and blue apart) a colour is grey: its hue means nothing. */
    private const val GREY = 0.04f

    /** A cover's colour is livened a little, so it reads as a colour on the dark page; never past [MOST_SATURATION]. */
    private const val LIVEN = 1.15f
    private const val MOST_SATURATION = 0.85f
    private const val LIGHTNESS_FROM = 0.40f

    /** A muted colour (below [MUTED] saturation) starts lighter, like the cover's own highlights, so it isn't muddy. */
    private const val MUTED = 0.35f
    private const val MUTED_LIGHTNESS_FROM = 0.62f

    /** Silver for a cover in black, white and grey: a light grey, lightened from here as needed. */
    private const val SILVER_FROM = 0.78f
    // Up to white: on the lightest covers, text may need it.
    private const val LIGHTNESS_TOP = 1.0f
    private const val STEP = 0.01f

    /** The play symbol on the button: dark or white, whichever reads better. */
    private val ON_DARK = Color(0xFF120D00)

    /**
     * The cover's colour from its [swatches] (issue #994): of the ones with a colour of their own, the one with the
     * most of the cover and of colour together (its share × √chroma, so a big muted area beats a small bright spot, and a
     * bright logo on a black cover still counts). With none (black, white and grey), a grey: [of] makes it silver. Null
     * without swatches.
     */
    fun seedOf(swatches: List<CoverSwatch>): Color? {
        if (swatches.isEmpty()) return null
        val coloured = swatches.filter { chroma(it.color) >= GREY }
        if (coloured.isEmpty()) return Color(0.5f, 0.5f, 0.5f)
        return coloured.maxBy { it.population * kotlin.math.sqrt(chroma(it.color)) }.color
    }

    /**
     * The colours for a cover whose own colour is [seed] ([seedOf]), on now playing's [backdrop] (see [backdropOf]). A
     * grey [seed] gives silver. Null only for a colour that can't be read.
     */
    fun of(seed: Color, backdrop: Color): DynamicColours? {
        if (seed.alpha <= 0f) return null
        val (h, s0, l0) = hsl(seed)
        val grey = chroma(seed) < GREY
        val s = if (grey) 0f else min(MOST_SATURATION, s0 * LIVEN)
        val from = when {
            grey -> SILVER_FROM
            s < MUTED -> max(l0, MUTED_LIGHTNESS_FROM)
            else -> max(l0, LIGHTNESS_FROM)
        }
        val controls = lightenUntil(h, s, from, backdrop, CONTROLS_CONTRAST)
        val text = lightenUntil(h, s, from, backdrop, TEXT_CONTRAST)
        val on = if (contrast(controls, ON_DARK) >= contrast(controls, Color.White)) ON_DARK else Color.White
        return DynamicColours(controls, text, on)
    }

    /**
     * What's behind the controls for a cover of [average] colour: now playing draws the cover blurred at 85% over
     * black, under black at about 45% where the controls are.
     */
    fun backdropOf(average: Color): Color = lerp(Color.Black, average, 0.85f * 0.55f)

    private fun lightenUntil(h: Float, s: Float, from: Float, backdrop: Color, target: Double): Color {
        var l = from
        var colour = fromHsl(h, s, l)
        while (l < LIGHTNESS_TOP && contrast(colour, backdrop) < target) {
            l += STEP
            colour = fromHsl(h, s, l)
        }
        return colour
    }

    /** WCAG's contrast between two colours, 1 to 21. */
    fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** How much colour there is: the most and least of red, green and blue apart, 0 for grey to 1. */
    fun chroma(c: Color): Float = max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue))

    private fun hsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        if (hi == lo) return Triple(0f, 0f, l)
        val d = hi - lo
        val s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
        val h = when (hi) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        return Triple(h, s, l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h * 6f) % 6f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r, g, b) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
    }
}

/** A cover's own colour ([DynamicAccent.seedOf]) and its average, as now playing's colours need them (issue #970). */
private data class CoverTones(val seed: Color?, val average: Color)

private val tonesCache = ConcurrentHashMap<String, CoverTones>()

/**
 * Now playing's colours from [coverId]'s cover (issue #970) while [on]; null when off, before the cover has been
 * looked at, or for a cover without a colour of its own. They fade from song to song.
 */
@Composable
fun rememberDynamicColours(coverId: String?, on: Boolean): DynamicColours? {
    val graph = LocalGraph.current
    val context = LocalContext.current
    var tones by remember(coverId, on) { mutableStateOf(if (on) coverId?.let { tonesCache[it] } else null) }
    LaunchedEffect(coverId, on) {
        if (!on || coverId == null || tonesCache.containsKey(coverId)) return@LaunchedEffect
        val read = withContext(Dispatchers.IO) {
            runCatching {
                val builder = ImageRequest.Builder(context)
                if (coverId.startsWith(LocalPlaylists.FILE_COVER)) builder.data(java.io.File(coverId.removePrefix(LocalPlaylists.FILE_COVER)))
                else builder.data(graph.source.coverUrl(coverId, 300)).diskCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:300")
                val request = builder
                    .memoryCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:palette")
                    .allowHardware(false)
                    .size(120)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
                val bitmap = result.image.toBitmap()
                // Every colour, black and white too (the palette leaves them out at first): a black and white cover is
                // silver, and how much of a cover is black counts against a small bright spot (issue #994).
                val palette = Palette.from(bitmap).maximumColorCount(16).clearFilters().generate()
                val one = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
                CoverTones(DynamicAccent.seedOf(palette.swatches.map { CoverSwatch(Color(it.rgb), it.population) }), Color(one))
            }.onFailure { AppLog.w("Colours", "Couldn't read the colours of cover $coverId: ${it.message}") }.getOrNull()
        }
        if (read != null) {
            tonesCache[coverId] = read
            tones = read
        }
    }
    val colours = tones?.let { t -> t.seed?.let { DynamicAccent.of(it, DynamicAccent.backdropOf(t.average)) } }
    LaunchedEffect(colours) {
        if (colours != null) AppLog.d("Colours", "Now playing in the cover's colours: controls ${hex(colours.controls)}, text ${hex(colours.text)}")
    }
    // While the next cover is read, the last song's colours stay, so the theme's doesn't flash in between; a cover that
    // couldn't be read gives the theme's.
    var last by remember { mutableStateOf<DynamicColours?>(null) }
    androidx.compose.runtime.SideEffect { if (tones != null) last = colours }
    if (!on) return null
    // Fade between songs; until the first cover is read, nothing (the theme's colour).
    val target = (if (tones != null) colours else last) ?: return null
    val controls by animateColorAsState(target.controls, tween(FADE_MS), label = "dynamicControls")
    val text by animateColorAsState(target.text, tween(FADE_MS), label = "dynamicText")
    return DynamicColours(controls, text, target.onControls)
}

private fun hex(c: Color) = "#%06X".format(0xFFFFFF and (((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt()))

private const val FADE_MS = 600
