package app.musix.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
 * Now playing → tap the format badge: the cover makes way for a live spectrogram of the song, to check
 * whether a "lossless" file really is. Listens to the player only while it's showing and musix is on screen.
 */
@Composable
fun SpectrogramView(state: PlayerState, casting: Boolean) {
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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        val s = spectrogram
        val rate = s?.sampleRate?.let { " · ${"%.1f".format(it / 1_000.0)} kHz" } ?: ""
        Text(
            "Sound check · " + (state.codec ?: "unknown format") + rate,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        SpectrogramImage(bitmap, drawn, s?.sampleRate ?: 44_100, Modifier.weight(1f, fill = false).fillMaxWidth().aspectRatio(1.3f))
        // Re-judged about once a second as columns arrive.
        val verdict = remember(s, drawn / 20, state.codec, state.transcodeKbps, state.isPlaying, casting) {
            when {
                casting -> "Not available while casting: the TV or speaker plays the song, not this phone."
                s == null -> if (state.isPlaying) "Listening…" else "Play a song to see its sound."
                else -> Spectrogram.verdictText(s.cutoff.verdict(), s.cutoff.cutoffHz(), state.codec, state.transcodeKbps)
            }
        }
        Text(verdict, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Text(
            "Lossy formats cut off the highest sounds: about 16 kHz for a 128k MP3, 19 to 20 kHz for 320k. " +
                "Lossless usually reaches 21 to 22 kHz. Tap the format badge again for the cover.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

/** The spectrogram, stretched to the sheet's width, with a kHz scale on the left. */
@Composable
private fun SpectrogramImage(bitmap: Bitmap, drawn: Int, sampleRate: Int, modifier: Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val label = Color.White.copy(alpha = 0.8f).toArgb()
    val grid = Color.White.copy(alpha = 0.25f)
    Canvas(modifier.clip(RoundedCornerShape(8.dp))) {
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
