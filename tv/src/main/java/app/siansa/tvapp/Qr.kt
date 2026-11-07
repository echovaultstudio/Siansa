package app.siansa.tvapp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code's squares: true for dark, row by row. */
class QrGrid(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    companion object {
        /** [text] as a QR code with a quiet zone of [margin] squares round it. */
        fun of(text: String, margin: Int = 2): QrGrid {
            val matrix = QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to margin, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
            val n = matrix.width
            return QrGrid(n, BooleanArray(n * n) { matrix.get(it % n, it / n) })
        }
    }
}

/** A QR code, black on white so every phone camera reads it, drawn square by square. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val grid = remember(text) { QrGrid.of(text) }
    Canvas(modifier) {
        val cell = size.minDimension / grid.size
        drawRect(Color.White, size = Size(cell * grid.size, cell * grid.size))
        for (y in 0 until grid.size) for (x in 0 until grid.size) {
            if (grid[x, y]) drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell, cell))
        }
    }
}
