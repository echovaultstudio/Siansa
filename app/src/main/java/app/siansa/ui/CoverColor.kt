package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import app.siansa.data.LocalPlaylists
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val colorCache = ConcurrentHashMap<String, Int>()

/**
 * The cover's main colour (as Spotify uses for page backgrounds), toned down so white text stays
 * readable. Fades in once the cover has been looked at; remembered for the rest of the session.
 */
@Composable
fun rememberCoverColor(coverId: String?): Color {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    var target by remember(coverId) { mutableStateOf(coverId?.let { colorCache[it] }?.let { Color(it) } ?: fallback) }
    LaunchedEffect(coverId) {
        if (coverId == null || colorCache.containsKey(coverId)) return@LaunchedEffect
        val argb = withContext(Dispatchers.IO) {
            runCatching {
                val builder = ImageRequest.Builder(context)
                if (coverId.startsWith(LocalPlaylists.FILE_COVER)) builder.data(java.io.File(coverId.removePrefix(LocalPlaylists.FILE_COVER)))
                else builder.data(graph.source.coverUrl(coverId, 300)).diskCacheKey("cover:$coverId:300")
                val request = builder
                    .memoryCacheKey("cover:$coverId:palette")
                    .allowHardware(false)
                    .size(120)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
                val palette = Palette.from(result.image.toBitmap()).maximumColorCount(16).generate()
                (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb
            }.getOrNull()
        }
        if (argb != null) {
            colorCache[coverId] = argb
            target = Color(argb)
        }
    }
    val tone = lerp(target, Color.Black, 0.35f)
    return animateColorAsState(tone, tween(350), label = "cover-color").value
}
