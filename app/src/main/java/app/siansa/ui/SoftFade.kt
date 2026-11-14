package app.siansa.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/**
 * A page's colour running down into the page without stripes (the owner, 14 Nov: the album page's fade showed bands
 * all over, beside the album's name). Three things made them: a dark colour has few shades to fade through, the fade
 * had a bend three quarters down, and it was laid over the page half see-through. Here the fade is one smooth curve
 * of solid colours, dithered, under a grain too fine to see that breaks up what stripes are left.
 */
fun Modifier.softFade(top: Color, bottom: Color): Modifier = drawWithCache {
    val colours = IntArray(SoftFade.STEPS + 1) { i -> SoftFade.at(top, bottom, i / SoftFade.STEPS.toFloat()).toArgb() }
    val paint = android.graphics.Paint().apply {
        isDither = true
        shader = android.graphics.LinearGradient(0f, 0f, 0f, size.height.coerceAtLeast(1f), colours, null, android.graphics.Shader.TileMode.CLAMP)
    }
    val grain = ShaderBrush(ImageShader(SoftFade.grain, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        drawRect(grain)
    }
}

internal object SoftFade {
    const val STEPS = 24
    /** How strong the grain is at most, of 255: about one and a half shades either way. */
    const val GRAIN = 5
    private const val GRAIN_SIZE = 128

    /**
     * The colour [t] of the way down (0 top, 1 bottom): all of [top] at the top, a third of it three quarters down
     * (as the fade always had), none at the bottom, on one curve with no bend.
     */
    fun at(top: Color, bottom: Color, t: Float): Color = lerp(bottom, top.copy(alpha = 1f), share(t))

    fun share(t: Float): Float = 1f - t.coerceIn(0f, 1f).pow(1.5f)

    /** Black and white specks, nearly see-through, the same every time (a fixed seed) so a picture of a page doesn't change. */
    val grain: ImageBitmap by lazy {
        val random = java.util.Random(7)
        val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
            val alpha = random.nextInt(GRAIN + 1)
            (alpha shl 24) or (if (random.nextBoolean()) 0xFFFFFF else 0x000000)
        }
        android.graphics.Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
