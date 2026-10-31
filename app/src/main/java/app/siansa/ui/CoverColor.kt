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
                else builder.data(graph.source.coverUrl(coverId, 300)).diskCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:300")
                val request = builder
                    .memoryCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:palette")
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

/** How bright the cards under now playing may be: dark enough for white text, light enough to stand out (#717). */
internal const val CARD_LIGHT_MIN = 0.085f
internal const val CARD_LIGHT_MAX = 0.11f

/** A colour's brightness as the eye sees it, 0 to 1, from its sRGB channels. */
internal fun lightness(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/**
 * The cards' colour under now playing (#717): a deep shade of the page's cover colour ([pageTint], from
 * [rememberCoverColor]), so they belong to the page instead of being the same purple for every song. Its brightness is
 * kept between [CARD_LIGHT_MIN] and [CARD_LIGHT_MAX]: a red cover gives a dark red card, a black-and-white one a dark
 * grey, and white text stays easy to read on any of them.
 */
fun nowPlayingCardColor(pageTint: Color): Color {
    val deep = lerp(pageTint.copy(alpha = 1f), Color.Black, 0.54f)
    val light = lightness(deep)
    if (light < 0.002f) return NEUTRAL_CARD
    val target = light.coerceIn(CARD_LIGHT_MIN, CARD_LIGHT_MAX)
    val k = target / light
    return Color((deep.red * k).coerceAtMost(1f), (deep.green * k).coerceAtMost(1f), (deep.blue * k).coerceAtMost(1f))
}

/** The page's colour at the bottom of now playing, behind the cards: the cover colour, deeper still (#717). */
fun pageBottomColor(pageTint: Color): Color = lerp(pageTint.copy(alpha = 1f), Color.Black, 0.8f)

/** The card colour for a cover with no colour to take. */
private val NEUTRAL_CARD = Color(0xFF1A1A1C)
