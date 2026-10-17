package app.musix.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.musix.log.AppLog
import app.musix.playback.PlayerState
import app.musix.playback.Spectrogram
import app.musix.playback.SpectrumFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Now playing → tap the format badge: a live spectrogram of the song, to check whether a "lossless" file
 * really is. Only listens to the player while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrogramSheet(state: PlayerState, onDismiss: () -> Unit) {
    var spectrogram by remember { mutableStateOf<Spectrogram?>(null) }
    var drawn by remember { mutableIntStateOf(0) }
    val bitmap = remember { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Spectrogram.BLACK) } }

    DisposableEffect(Unit) {
        onDispose { SpectrumFeed.listening = false }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.current?.id) {
        SpectrumFeed.clear()
        spectrogram = null
        bitmap.eraseColor(Spectrogram.BLACK)
        drawn++
        // Only while musix is on screen: with the screen off or the app in the background it rests.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            SpectrumFeed.clear()
            SpectrumFeed.listening = true
            try {
                while (isActive) {
                    delay(50)
                    val samples = SpectrumFeed.take()
                    val rate = SpectrumFeed.sampleRate
                    if (samples.isEmpty() || rate <= 0) continue
                    val current = spectrogram?.takeIf { it.sampleRate == rate }
                        ?: Spectrogram(rate, bitmap.width, bitmap.height).also {
                            spectrogram = it
                            AppLog.d("Spectrogram", "Started at $rate Hz")
                        }
                    withContext(Dispatchers.Default) { current.add(samples) }
                    bitmap.setPixels(current.pixels, 0, current.width, 0, 0, current.width, current.height)
                    drawn++
                }
            } finally {
                SpectrumFeed.listening = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sound check", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val s = spectrogram
            val rate = s?.sampleRate?.let { " · ${"%.1f".format(it / 1_000.0)} kHz" } ?: ""
            Text(
                (state.codec ?: "Unknown format") + rate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpectrogramImage(bitmap, drawn, s?.sampleRate ?: 44_100)
            // Re-judged about once a second as columns arrive.
            val verdict = remember(s, drawn / 20, state.codec, state.transcodeKbps, state.isPlaying) {
                when {
                    s == null -> if (state.isPlaying) "Listening…" else "Play a song to see its sound."
                    else -> Spectrogram.verdictText(s.cutoff.verdict(), s.cutoff.cutoffHz(), state.codec, state.transcodeKbps)
                }
            }
            Text(verdict, style = MaterialTheme.typography.bodyLarge)
            Hint(
                "Lossy formats (MP3, AAC, Opus) throw away the highest sounds, which shows as a flat ceiling: " +
                    "about 16 kHz for a 128k MP3, 19 to 20 kHz for 320k. Lossless usually reaches 21 to 22 kHz. " +
                    "It catches the usual fakes, but can't prove a file is untouched. Not available while casting.",
            )
        }
    }
}

/** The spectrogram, stretched to the sheet's width, with a kHz scale on the left. */
@Composable
private fun SpectrogramImage(bitmap: Bitmap, drawn: Int, sampleRate: Int) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val label = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f).toArgb()
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Canvas(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))) {
        // Read so the canvas redraws when a new column arrives.
        drawn.hashCode()
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
        val nyquist = sampleRate / 2.0
        val paint = Paint().apply { color = label; textSize = 11.sp.toPx(); isAntiAlias = true }
        var khz = 5
        while (khz * 1_000 < nyquist) {
            val y = (size.height * (1 - khz * 1_000 / nyquist)).toFloat()
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            drawIntoCanvas { it.nativeCanvas.drawText("$khz kHz", 6.dp.toPx(), y - 3.dp.toPx(), paint) }
            khz += 5
        }
    }
}
