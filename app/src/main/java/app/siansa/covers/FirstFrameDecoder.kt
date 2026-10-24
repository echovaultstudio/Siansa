package app.siansa.covers

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension

/**
 * Shows an animated cover (GIF or animated WebP) as its first frame wherever a still picture is wanted:
 * lists, backgrounds, colours, the lock screen. Android's own still decoder fails on some of these
 * (Navidrome's ffmpeg conversions of GIFs), which left those covers blank; this draws the first frame
 * through the animation decoder instead. Requests for the animation itself (album page, now playing)
 * choose their own decoder and never reach this one.
 */
class FirstFrameDecoder(private val result: SourceFetchResult, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val maxPx = listOf(options.size.width, options.size.height)
            .mapNotNull { (it as? Dimension.Pixels)?.px }
            .maxOrNull()
            ?.coerceIn(64, CoverStore.STILL_PX) ?: CoverStore.STILL_PX
        val file = result.source.fileOrNull()?.toFile()
        val bitmap = if (file != null) CoverStore.firstFrame(file, maxPx)
        else CoverStore.firstFrame(result.source.source().readByteArray(), maxPx)
        if (bitmap == null) app.siansa.log.AppLog.w("Cover", "Couldn't read the first frame of a cover with any decoder")
        return bitmap?.let { DecodeResult(image = it.asImage(), isSampled = true) }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val head = runCatching {
                val peek = result.source.source().peek()
                peek.request(32)
                peek.readByteArray(minOf(32L, peek.buffer.size))
            }.getOrNull() ?: return null
            val image = CoverStore.sniff(head)
            if (image == null) {
                if (head.isNotEmpty()) app.siansa.log.AppLog.d("Cover", "Not a cover type Siansa knows (first bytes ${head.take(16).joinToString("") { "%02x".format(it) }}); Android decodes it")
                return null
            }
            // Every GIF and every extended WebP (the format animations, transparency and colour profiles use)
            // goes here, not only those marked animated: Android's still decoder fails on some of these too,
            // which left animated albums blank in lists now and then (issue #162). Stills read as before.
            return if (image.animated || image.extendedWebP || image.mime == "image/gif") FirstFrameDecoder(result, options) else null
        }
    }
}
