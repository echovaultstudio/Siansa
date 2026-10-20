package app.musix.ui

import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController

/**
 * The top of every Your listening page (Your month, Time machine, Other artists you might like), so they
 * look like one family: the colours of its top artists as slow aurora ribbons (issues #325, #327), back, a big title and a line under it, then
 * the page's own controls ([extra]: range chips, the year and months), all on the page's edges.
 */
@Composable
fun ListeningHeader(
    nav: NavController,
    title: String,
    subtitle: String,
    // The pictures whose colours the background is made of: the page's top artists or songs (issue #327).
    covers: List<String?> = emptyList(),
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val found = List(3) { rememberCoverColorOrNull(covers.getOrNull(it)) }
    val target = auroraColours(found)
    val colours = target.mapIndexed { i, c -> animateColorAsState(c, tween(400), label = "aurora-$i").value }
    val drift by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(AURORA_PERIOD_MS, easing = LinearEasing)), label = "aurora-drift",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .auroraBackdrop(colours, drift)
            .statusBarsPadding()
            .padding(bottom = Space.Page),
    ) {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(horizontal = Space.Page),
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = Space.Page).padding(top = Space.Tight),
            )
        }
        extra()
    }
}

/** How long the ribbons take to drift once there and back: slow enough to feel still (issue #327). */
private const val AURORA_PERIOD_MS = 24_000

/** The fixed colours used until (or instead of) the pictures' own: violet, rose and amber. */
internal val AURORA_DEFAULTS = listOf(Color(0xFF5B3FD0), Color(0xFFE5508F), Color(0xFFFFB547))

/**
 * Three colours for the aurora (issue #327): the pictures' colours where known, else the defaults, each toned so
 * white text stays readable on it (not too light, not neon).
 */
internal fun auroraColours(found: List<Color?>): List<Color> =
    List(3) { i -> readableTone(found.getOrNull(i) ?: AURORA_DEFAULTS[i]) }

/** [c] with its lightness kept between 30 % and 50 % and its saturation at most 80 %. */
internal fun readableTone(c: Color): Color {
    val (h, sat, l) = toHsl(c)
    return fromHsl(h, sat.coerceAtMost(0.8f), l.coerceIn(0.3f, 0.5f))
}

internal fun toHsl(c: Color): Triple<Float, Float, Float> {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        c.red -> ((c.green - c.blue) / d + (if (c.green < c.blue) 6f else 0f))
        c.green -> ((c.blue - c.red) / d + 2f)
        else -> ((c.red - c.green) / d + 4f)
    } * 60f
    return Triple(h, s, l)
}

internal fun fromHsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/** A tile of fine noise, drawn once and repeated over the colours so the gradient doesn't show steps. */
private val grain: ImageBitmap by lazy {
    val size = 96
    val random = java.util.Random(7)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        val a = random.nextInt(18)
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The Your listening header's background (issue #327, "aurora"): deep plum crossed by three soft diagonal
 * ribbons in [colours] (the page's top artists'), drifting very slowly with [phase]; fine grain over them, a
 * faint light at the top, and a long fade into the page at the bottom.
 */
fun Modifier.auroraBackdrop(colours: List<Color>, phase: Float): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawRect(Brand.Plum)
    rotate(-28f, pivot = Offset(w / 2f, h / 2f)) {
        colours.forEachIndexed { i, c ->
            val band = w * 0.62f
            val centre = w * (-0.05f + 0.42f * i) + w * 0.05f * sin(phase + i * 2.1f)
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to c.copy(alpha = 0.9f), 1f to Color.Transparent,
                    startX = centre - band / 2f, endX = centre + band / 2f,
                ),
                topLeft = Offset(centre - band / 2f, -h),
                size = Size(band, h * 3f),
            )
        }
    }
    // A soft light from the top left, and the status bar kept a little darker.
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), center = Offset(w * 0.2f, 0f), radius = w * 0.8f))
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.25f to Color.Transparent))
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Brand.Plum))
}
