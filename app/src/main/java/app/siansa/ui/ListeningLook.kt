package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one look of the listening pages (Your listening, the time machine, your sonic fingerprint; the owner, 12 Nov):
 * only the header changes with the music, as a soft picture behind it. Everything under it is the same on every page
 * and every month: the tiles with the big numbers are a faint, see-through white with a thin edge, so they read on
 * the dark page whatever the picture's colour. Washed in the artist's colour they vanished when it was dark (Karnivool,
 * Sleep Token), and amber and violet tiles on one page beside washed ones on another didn't look like one family.
 */
internal val TILE_FILL = Color.White.copy(alpha = 0.10f)
internal val TILE_EDGE = Color.White.copy(alpha = 0.14f)

/** A listening page's tile: [shape]d, faintly filled and edged. The content goes on in white. */
internal fun Modifier.listeningTile(shape: Shape): Modifier = clip(shape).background(TILE_FILL).border(1.dp, TILE_EDGE, shape)

/** The size a header's one picture is asked for. */
internal const val HEADER_PICTURE = 300

/** How long a header's picture and colour take to fade in or change. */
internal const val HEADER_FADE = 220

/**
 * The cover [wanted] behind a header, once it's in memory at [HEADER_PICTURE], or null: without it (none wanted,
 * not loadable, or the page still [loading]) the header stays plain. The picture is read into memory first and only
 * then given, so it fades in whole: put there straight away it showed an empty box, then popped in (the flashing,
 * 12 Nov). Artist pictures are looked up fresh first ([app.siansa.data.freshArtistCover]).
 */
@Composable
internal fun rememberHeaderPicture(wanted: String?, loading: Boolean): String? {
    val graph = LocalGraph.current
    val context = LocalContext.current
    var picture by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(wanted, loading) {
        if (loading) return@LaunchedEffect
        if (wanted == null) { picture = null; return@LaunchedEffect }
        val id = app.siansa.data.freshArtistCover(wanted, graph.artistPhotos.covers.value)
        val there = withContext(Dispatchers.IO) {
            runCatching { coil3.SingletonImageLoader.get(context).execute(coverRequest(context, graph, id, HEADER_PICTURE)) is coil3.request.SuccessResult }.getOrDefault(false)
        }
        picture = if (there) wanted else null
    }
    return picture
}

/**
 * The colour a header takes from its [picture] (only used before Android 12, which can't blur), fading between
 * pictures; a quiet grey without one, so the app's amber and violet never show first and then change.
 */
@Composable
internal fun rememberHeaderTint(picture: String?, label: String): Color {
    val fromPicture = rememberCoverColor(picture)
    val tint by animateColorAsState(if (picture != null) fromPicture else NO_ARTIST_TINT, tween(HEADER_FADE), label = label)
    return tint
}
