package app.musix.covers

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
        return bitmap?.let { DecodeResult(image = it.asImage(), isSampled = true) }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val head = runCatching {
                val peek = result.source.source().peek()
                peek.request(32)
                peek.readByteArray(minOf(32L, peek.buffer.size))
            }.getOrNull() ?: return null
            val image = CoverStore.sniff(head) ?: return null
            return if (image.animated) FirstFrameDecoder(result, options) else null
        }
    }
}
