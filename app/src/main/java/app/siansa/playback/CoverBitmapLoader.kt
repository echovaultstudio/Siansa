package app.siansa.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import app.siansa.ui.SMALL_COVER
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Covers for the notification and lock screen, through the app's own image cache (and so through
 * CoverStore for animated covers) instead of Media3's loader, which downloaded the full file on every
 * song change and decoded it at full size. Uses the same cache entry as the lists.
 */
@UnstableApi
class CoverBitmapLoader(private val context: Context, private val scope: CoroutineScope) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= 512) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalArgumentException("Not an image"))
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val id = uri.getQueryParameter("id")
                val builder = ImageRequest.Builder(context).data(uri.toString()).allowHardware(false).size(512)
                if (id != null) {
                    // The same entries the lists use, so it's usually on the phone already.
                    val key = "cover:$id:${uri.getQueryParameter("size") ?: SMALL_COVER}"
                    builder.diskCacheKey(key).memoryCacheKey("$key:notification")
                }
                val result = SingletonImageLoader.get(context).execute(builder.build())
                if (result is SuccessResult) future.set(result.image.toBitmap())
                else future.setException(IllegalStateException("Cover couldn't be loaded"))
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
