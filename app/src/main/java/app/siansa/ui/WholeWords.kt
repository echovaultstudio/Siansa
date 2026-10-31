package app.siansa.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified

/**
 * A big title in a box of fixed width (tiles, banners, cards) that never breaks a word in two (issue #697): when a
 * word is wider than the box, or the title needs more than [maxLines], the text gets a little smaller, down to
 * [minScale] of its size. "Melancholy" on a radio tile stays one word instead of "Melanchol" / "y".
 */
@Composable
fun WholeWordsText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
    minScale: Float = MIN_TITLE_SCALE,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val fitted = remember(text, style, width, maxLines) {
            if (width == Constraints.Infinity || width <= 0) return@remember style
            val scale = wholeWordsScale(
                // One pixel to spare: a word that fits only to the pixel is still cut on screen ("Melancholy" at 130 %,
                // issue #738).
                widestWord = { s -> words(text).maxOfOrNull { measurer.measure(it, scaled(style, s), maxLines = 1, softWrap = false).size.width + 1 } ?: 0 },
                lines = { s -> measurer.measure(text, scaled(style, s), constraints = Constraints(maxWidth = width)).lineCount },
                width = width,
                maxLines = maxLines,
                minScale = minScale,
            )
            scaled(style, scale)
        }
        Text(text, style = fitted, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** How small a title may get to keep its words whole: below this it's ellipsised instead. */
const val MIN_TITLE_SCALE = 0.6f

/**
 * The largest size, as a share of the title's own (1 down to [minScale] in steps of [STEP]), at which every word fits
 * on a line [width] wide ([widestWord]) and the title takes at most [maxLines] lines ([lines]). [minScale] when even
 * that doesn't fit.
 */
fun wholeWordsScale(widestWord: (Float) -> Int, lines: (Float) -> Int, width: Int, maxLines: Int, minScale: Float = MIN_TITLE_SCALE): Float {
    var scale = 1f
    while (scale > minScale + 1e-3f) {
        if (widestWord(scale) <= width && lines(scale) <= maxLines) return scale
        scale -= STEP
    }
    return minScale
}

private const val STEP = 0.05f

private fun words(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun scaled(style: TextStyle, scale: Float): TextStyle = if (scale >= 1f) style else style.copy(
    fontSize = if (style.fontSize.isSpecified) style.fontSize * scale else style.fontSize,
    lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
)
