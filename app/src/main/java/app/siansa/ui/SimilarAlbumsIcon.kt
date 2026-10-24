package app.siansa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter

/**
 * The "Find similar albums" icon (issue #239): the waveform of "Similar songs", with an "A" (for album) cut
 * out of it. Drawn on a 24-unit grid like Material icons, and tinted by [androidx.compose.material3.Icon].
 */
class SimilarAlbumsPainter : Painter() {
    private var filter: ColorFilter? = null

    override val intrinsicSize = Size(GRID, GRID)

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        filter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        val unit = size.minDimension / GRID
        val origin = Offset((size.width - GRID * unit) / 2f, (size.height - GRID * unit) / 2f)
        // A layer of its own, so the "A" can clear the bars beneath it without touching what's behind the icon.
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        scale(unit, unit, pivot = Offset.Zero) {
            val shift = Offset(origin.x / unit, origin.y / unit)
            BARS.forEach { (x, height) ->
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(x - BAR_WIDTH / 2f, CENTRE - height / 2f) + shift,
                    size = Size(BAR_WIDTH, height),
                    cornerRadius = CornerRadius(BAR_WIDTH / 2f),
                    colorFilter = filter,
                )
            }
            val letter = Path().apply {
                moveTo(7.6f + shift.x, 18.2f + shift.y) // left foot
                lineTo(12f + shift.x, 5.8f + shift.y) // apex
                lineTo(16.4f + shift.x, 18.2f + shift.y) // right foot
                moveTo(9.3f + shift.x, 14f + shift.y) // crossbar
                lineTo(14.7f + shift.x, 14f + shift.y)
            }
            drawPath(letter, Color.Black, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
        }
        drawContext.canvas.restore()
    }

    private companion object {
        const val GRID = 24f
        const val CENTRE = 12f
        const val BAR_WIDTH = 3f
        // Five bars like the waveform icon: x of each bar's middle, and its height.
        val BARS = listOf(2.5f to 6f, 7.25f to 12f, 12f to 20f, 16.75f to 12f, 21.5f to 6f)
    }
}

/** One shared painter per composition: it keeps no state besides the tint. */
@Composable
fun rememberSimilarAlbumsIcon(): Painter = remember { SimilarAlbumsPainter() }
