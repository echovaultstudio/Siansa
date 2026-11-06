package app.siansa.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Siansa's own icons, drawn like Material's rounded ones (24 dp, tinted where they're used). */
object SiansaIcons {
    /**
     * A turntable from above, for DJ mixes (issue #916): the deck, the record with its spindle, and the tone arm resting
     * on the record's edge. Material has no turntable.
     */
    val Turntable: ImageVector by lazy {
        ImageVector.Builder(name = "Siansa.Turntable", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            val line = SolidColor(Color.Black)
            // The deck: a rounded square.
            path(stroke = line, strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6f, 3f)
                lineTo(18f, 3f)
                arcTo(3f, 3f, 0f, false, true, 21f, 6f)
                lineTo(21f, 18f)
                arcTo(3f, 3f, 0f, false, true, 18f, 21f)
                lineTo(6f, 21f)
                arcTo(3f, 3f, 0f, false, true, 3f, 18f)
                lineTo(3f, 6f)
                arcTo(3f, 3f, 0f, false, true, 6f, 3f)
                close()
            }
            // The record.
            path(stroke = line, strokeLineWidth = 2f) {
                moveTo(5.5f, 12.5f)
                arcTo(5f, 5f, 0f, true, true, 15.5f, 12.5f)
                arcTo(5f, 5f, 0f, true, true, 5.5f, 12.5f)
                close()
            }
            // Its spindle.
            path(fill = line) {
                moveTo(9.3f, 12.5f)
                arcTo(1.2f, 1.2f, 0f, true, true, 11.7f, 12.5f)
                arcTo(1.2f, 1.2f, 0f, true, true, 9.3f, 12.5f)
                close()
            }
            // The tone arm, from its pivot in the corner down onto the record's edge.
            path(stroke = line, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(17.8f, 6.2f)
                lineTo(17.8f, 13f)
                lineTo(14.4f, 15.6f)
            }
            path(fill = line) {
                moveTo(16.6f, 6.2f)
                arcTo(1.2f, 1.2f, 0f, true, true, 19f, 6.2f)
                arcTo(1.2f, 1.2f, 0f, true, true, 16.6f, 6.2f)
                close()
            }
        }.build()
    }

    /**
     * A record with AudioMuse's sparkle, for an album's Explore button (issue #965): finding albums by their sound and
     * mixing the album's songs by theirs.
     */
    val RecordSparkle: ImageVector by lazy {
        ImageVector.Builder(name = "Siansa.RecordSparkle", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            val line = SolidColor(Color.Black)
            // The record.
            path(stroke = line, strokeLineWidth = 1.8f) {
                moveTo(3.5f, 13f)
                arcTo(7.5f, 7.5f, 0f, true, true, 18.5f, 13f)
                arcTo(7.5f, 7.5f, 0f, true, true, 3.5f, 13f)
                close()
            }
            // Its label.
            path(stroke = line, strokeLineWidth = 1.6f) {
                moveTo(8.6f, 13f)
                arcTo(2.4f, 2.4f, 0f, true, true, 13.4f, 13f)
                arcTo(2.4f, 2.4f, 0f, true, true, 8.6f, 13f)
                close()
            }
            // The sparkle, top right.
            path(fill = line) {
                moveTo(19f, 1.8f)
                lineTo(19.9f, 4.1f)
                lineTo(22.2f, 5f)
                lineTo(19.9f, 5.9f)
                lineTo(19f, 8.2f)
                lineTo(18.1f, 5.9f)
                lineTo(15.8f, 5f)
                lineTo(18.1f, 4.1f)
                close()
            }
        }.build()
    }
}
