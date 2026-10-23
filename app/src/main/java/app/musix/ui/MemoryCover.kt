package app.musix.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import app.musix.AppGraph
import app.musix.log.AppLog
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The picture of a time machine memory playlist (issue #186): the top albums of that time as a 2 × 2 grid
 * (one big cover when there are fewer than four), with "TIME MACHINE" and the date over a soft shade at the
 * bottom. The space around the grid, between the covers and around the words is even.
 */
object MemoryCover {
    private const val SIZE = MemoryLayout.SIZE
    private const val MARGIN = MemoryLayout.MARGIN
    private const val TEXT_INSET = MemoryLayout.TEXT_INSET
    private const val CORNER = 28f

    /** The picture as a JPEG, or null if it couldn't be made (the playlist is still made, without it). */
    suspend fun make(context: Context, graph: AppGraph, songs: List<app.musix.data.Song>, label: String): ByteArray? = runCatching {
        val ids = MemoryLayout.coverIds(songs)
        val bitmaps = ids.mapNotNull { id -> load(context, graph, id) }
        withContext(Dispatchers.Default) { draw(bitmaps, label) }
    }.onFailure { AppLog.w("TimeMachine", "Couldn't make the memory's picture: ${it.message}") }.getOrNull()

    private suspend fun load(context: Context, graph: AppGraph, id: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(graph.source.coverUrl(id, LARGE_COVER))
            .diskCacheKey("cover:$id:$LARGE_COVER")
            .memoryCacheKey("cover:$id:$LARGE_COVER:memory")
            .allowHardware(false)
            .size(SIZE / 2)
            .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private fun draw(covers: List<Bitmap>, label: String): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val size = SIZE.toFloat()
        // Musix's violet into plum, under everything.
        canvas.drawRect(0f, 0f, size, size, Paint().apply {
            shader = LinearGradient(0f, 0f, size, size, Brand.VioletDeep.toArgb(), Brand.Plum.toArgb(), Shader.TileMode.CLAMP)
        })
        val tiles = MemoryLayout.tiles(covers.size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        tiles.forEachIndexed { i, t ->
            val cover = covers[i]
            val dst = RectF(t.left.toFloat(), t.top.toFloat(), (t.left + t.size).toFloat(), (t.top + t.size).toFloat())
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(dst, CORNER, CORNER, Path.Direction.CW) })
            // Centre-crop to a square.
            val side = minOf(cover.width, cover.height)
            val src = Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            canvas.drawBitmap(cover, src, dst, paint)
            canvas.restore()
        }
        // A shade at the bottom so the words read on any cover.
        canvas.drawRect(0f, size * 0.5f, size, size, Paint().apply {
            shader = LinearGradient(0f, size * 0.5f, 0f, size, 0x00000000, 0xD0000000.toInt(), Shader.TileMode.CLAMP)
        })
        val left = (MARGIN + TEXT_INSET).toFloat()
        val bottom = size - MARGIN - TEXT_INSET
        val width = size - 2 * left
        val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 112f
            while (measureText(label) > width && textSize > 48f) textSize -= 4f
        }
        canvas.drawText(label, left, bottom, date)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Brand.Amber.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 38f
            letterSpacing = 0.25f
        }
        // As far above the date as the date's own top is from its baseline, plus one even step.
        canvas.drawText("TIME MACHINE", left, bottom + date.ascent() - TEXT_INSET / 2f, title)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
