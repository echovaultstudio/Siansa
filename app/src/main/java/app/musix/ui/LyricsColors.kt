package app.musix.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * The text colours of the lyrics screen and its background (issue #176): the line playing now white, the
 * lines to come a soft white you can read ahead with, the lines already sung dimmer still.
 *
 * The lines to come used to be a fixed dark violet, which all but disappeared on the dark, cover-coloured
 * background most songs get. And on a bright cover (white, yellow, lime) even the white line playing now
 * was hard to read, so the background is darkened, keeping the cover's hue, until white text reads well.
 */
class LyricsColors(val current: Color, val sung: Color, val upcoming: Color)

/** How readable white text must be at the top of the lyrics background (the lightest part). */
internal const val LYRICS_WHITE_CONTRAST = 7.0

val LyricsTextColors = LyricsColors(
    current = Color.White,
    sung = Color.White.copy(alpha = 0.5f),
    // Darker than before (issue #362), so what's still to sing stands apart from the words being sung.
    upcoming = Color.White.copy(alpha = 0.6f),
)

/**
 * The lyrics screen's background, top to bottom: [tint] darkening towards the bottom, and darker overall
 * when the cover's colour is too bright for white text.
 */
fun lyricsBackground(tint: Color): List<Color> {
    var base = tint.copy(alpha = 1f)
    var steps = 0
    while (steps < 30 && ThemeTones.contrast(Color.White.toArgb(), lerp(base, Color.Black, 0.1f).toArgb()) < LYRICS_WHITE_CONTRAST) {
        base = lerp(base, Color.Black, 0.1f)
        steps++
    }
    return listOf(lerp(base, Color.Black, 0.1f), lerp(base, Color.Black, 0.4f))
}

/** How readable [fg] (which may be see-through) is at its worst on the [background] colours. */
fun worstContrast(fg: Color, background: List<Color>): Double =
    background.minOf { bg -> ThemeTones.contrast(fg.compositeOver(bg).toArgb(), bg.toArgb()) }
