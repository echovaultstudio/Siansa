package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The cover's main colour (for page backgrounds), toned down so white text stays readable. The same colour Play is
 * worked out from ([CoverTones.main]). Known in the page's first frame when the cover has been seen before (its colours
 * are kept on the phone, [CoverToneStore]); otherwise the theme's grey until the cover is read. It fades only from
 * one cover's colour to another's (now playing's next song), never in from the grey a page started with.
 */
@Composable
fun rememberCoverColor(coverId: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    val main = rememberCoverTarget(coverId)?.takeIf { it != Color.Unspecified }
    // Starts at the colour it has in the first frame: no fade when it's known then.
    return animateColorAsState(coverTone(main ?: fallback), tween(350), label = "cover-color").value
}

/** A cover's main colour as a page is painted in it: toned down so white text stays readable. */
internal fun coverTone(main: Color): Color = lerp(main, Color.Black, 0.35f)

/**
 * The cover's main colour ([CoverTones.main]), not fading: null while it's being read, [Color.Unspecified] for a cover
 * that gave none. What a page's colours are worked out from, once, so they don't change while the page is open.
 */
@Composable
internal fun rememberCoverTarget(coverId: String?): Color? = rememberCoverTones(coverId)?.main

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
