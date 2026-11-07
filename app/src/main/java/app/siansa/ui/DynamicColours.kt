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

/**
 * The cover's colour made to stand out on now playing (issue #970). The page behind the controls is the cover itself,
 * blurred and darkened ([backdropOf]), so the colour is the cover's liveliest ([of]'s seed, Android's vibrant swatch),
 * lightened until it stands out from that by [CONTROLS_CONTRAST] (the seek bar, Play) and [TEXT_CONTRAST] (the device
 * name), as accessibility asks of controls and text. A cover with no colour of its own (black and white, grey) has
 * none: the theme's colour stays.
 */
object DynamicAccent {
    const val CONTROLS_CONTRAST = 3.0
    const val TEXT_CONTRAST = 4.5

    /** Below this saturation a colour is grey: the theme's colour stays. */
    private const val GREY = 0.08f

    /** A faint colour is given at least this much, so it reads as a colour, not as grey. */
    private const val FAINT = 0.18f
    private const val SOFT_SATURATION = 0.45f
    private const val SATURATION = 0.55f
    private const val LIGHTNESS_FROM = 0.40f
    // Up to white: on the lightest covers, text may need it.
    private const val LIGHTNESS_TOP = 1.0f
    private const val STEP = 0.01f

    /** The play symbol on the button: dark or white, whichever reads better. */
    private val ON_DARK = Color(0xFF120D00)

    /**
     * The colours for a cover whose liveliest colour is [seed], on now playing's [backdrop] (see [backdropOf]); null
     * for a cover without a colour of its own.
     */
    fun of(seed: Color, backdrop: Color): DynamicColours? {
        val (h, s0, l0) = hsl(seed)
        if (hsvSaturation(seed) < GREY) return null
        val s = max(s0, if (s0 < FAINT) SOFT_SATURATION else SATURATION)
        val controls = lightenUntil(h, s, max(l0, LIGHTNESS_FROM), backdrop, CONTROLS_CONTRAST)
        val text = lightenUntil(h, s, max(l0, LIGHTNESS_FROM), backdrop, TEXT_CONTRAST)
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

    private fun hsvSaturation(c: Color): Float {
        val hi = max(c.red, max(c.green, c.blue))
        val lo = min(c.red, min(c.green, c.blue))
        return if (hi <= 0f) 0f else (hi - lo) / hi
    }

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

/** A cover's liveliest colour and its average, as now playing's colours need them (issue #970). */
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
                val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                val one = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
                CoverTones((palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch)?.rgb?.let { Color(it) }, Color(one))
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
    // While the next cover is read, the last song's colours stay, so the theme's doesn't flash in between; a cover read
    // and found without a colour of its own gives the theme's.
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
