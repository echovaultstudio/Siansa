package app.siansa.tvapp

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.siansa.log.AppLog
import app.siansa.ui.LocalGraph
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Now playing's background (issue #860): the cover, blurred until only its colours are left, filling the screen and
 * darkened so the text over it reads, as on the phone's player.
 *
 * The phone blurs with Android 12's blur, which many TVs don't have (an NVIDIA SHIELD is on Android 11, so it showed
 * the cover only dimmed, sharp). Here the cover is shrunk to [BACKDROP_PX] and blurred in that small size, then drawn
 * across the screen: the same look on every TV, and drawing it costs nothing after the first time.
 */
@Composable
fun TvBackdrop(coverId: String?, bottom: Color, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, coverId) {
        if (coverId == null) {
            value = null
            return@produceState
        }
        value = try {
            withContext(Dispatchers.Default) {
                val builder = ImageRequest.Builder(context)
                // A phone playlist's own picture is a file; everything else comes from the server.
                if (coverId.startsWith(app.siansa.data.LocalPlaylists.FILE_COVER)) builder.data(java.io.File(coverId.removePrefix(app.siansa.data.LocalPlaylists.FILE_COVER)))
                else builder.data(graph.source.coverUrl(coverId, BACKDROP_SOURCE_PX))
                    .diskCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:$BACKDROP_SOURCE_PX")
                val request = builder
                    .memoryCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:tv-backdrop")
                    .allowHardware(false)
                    .size(BACKDROP_PX)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withContext null
                blurredBitmap(result.image.toBitmap()).asImageBitmap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Tv", "Backdrop for $coverId: ${e.message}")
            null
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(picture, animationSpec = tween(BACKDROP_FADE_MS), label = "backdrop") { image ->
            if (image != null) {
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, alpha = BACKDROP_ALPHA)
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.6f to Color.Black.copy(alpha = 0.55f),
                    1f to bottom.copy(alpha = 0.9f),
                ),
            ),
        )
    }
}

/** [source] (any size) as a small, heavily blurred copy. */
private fun blurredBitmap(source: Bitmap): Bitmap {
    val small = Bitmap.createScaledBitmap(source, BACKDROP_PX, BACKDROP_PX, true)
    val pixels = IntArray(BACKDROP_PX * BACKDROP_PX)
    small.getPixels(pixels, 0, BACKDROP_PX, 0, 0, BACKDROP_PX, BACKDROP_PX)
    val out = boxBlur(pixels, BACKDROP_PX, BACKDROP_PX, BLUR_RADIUS, BLUR_PASSES)
    return Bitmap.createBitmap(out, BACKDROP_PX, BACKDROP_PX, Bitmap.Config.ARGB_8888)
}

/**
 * Blurs [pixels] (ARGB, [width] × [height]) with a box of [radius] pixels each side, across and then down, [passes]
 * times (three passes look like a soft, even blur). Edges repeat the edge pixel.
 */
fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int): IntArray {
    require(pixels.size == width * height)
    val current = pixels.copyOf()
    val next = IntArray(current.size)
    repeat(passes) {
        blurLine(current, next, width, height, radius, across = true)
        blurLine(next, current, width, height, radius, across = false)
    }
    return current
}

private fun blurLine(from: IntArray, to: IntArray, width: Int, height: Int, radius: Int, across: Boolean) {
    val lines = if (across) height else width
    val length = if (across) width else height
    val span = 2 * radius + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (across) from[line * width + c] else from[c * width + line]
        }
        var a = 0; var r = 0; var g = 0; var b = 0
        for (i in -radius..radius) {
            val p = at(i)
            a += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (i in 0 until length) {
            val index = if (across) line * width + i else i * width + line
            to[index] = ((a / span) shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
            val leaving = at(i - radius)
            val coming = at(i + radius + 1)
            a += (coming ushr 24) - (leaving ushr 24)
            r += ((coming shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            g += ((coming shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            b += (coming and 0xFF) - (leaving and 0xFF)
        }
    }
}

/** The cover file the backdrop is made from: the same one the phone's player colour uses, usually already cached. */
private const val BACKDROP_SOURCE_PX = 300
/** The backdrop's size before it's drawn across the screen: small enough that only the colours are left. */
private const val BACKDROP_PX = 48
private const val BLUR_RADIUS = 4
private const val BLUR_PASSES = 3
private const val BACKDROP_ALPHA = 0.9f
private const val BACKDROP_FADE_MS = 600
