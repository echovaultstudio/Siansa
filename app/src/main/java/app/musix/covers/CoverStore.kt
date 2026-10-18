package app.musix.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import app.musix.log.AppLog
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Animated covers that Navidrome can't shrink, kept once on the phone.
 *
 * Navidrome only resizes animated GIFs. An animated WebP (and some GIFs) comes back as the original file
 * whatever size is asked for, often 1000 px and several MB. Before this, every list tile, the album page,
 * now playing, the blurred background, the colour sampling and the notification each downloaded and
 * decoded that file separately, which used most of a slow connection and ran the app out of memory.
 *
 * This sits in front of every cover request (an OkHttp interceptor on the image client):
 * - When a cover comes back animated and bigger than asked for, it's saved once (`<id>.anim`) and a
 *   still of its first frame, at most [STILL_PX] px, is made next to it (`<id>.jpg`).
 * - From then on, every request for that cover is answered from the phone without the network: the
 *   still for lists, backgrounds and colours, the original only when the animation itself is asked for
 *   (header [WANT_ANIMATION]).
 * - Requests for the same cover while it's downloading wait for that one download instead of starting
 *   their own.
 * - A song without its own picture gets its album's cover from Navidrome; when that turns out to be the
 *   same file as the album's saved one, it's linked rather than downloaded again.
 *
 * Files live in the app's files folder, so Android doesn't remove them when it trims caches.
 */
object CoverStore {
    /** Ask for the animated original rather than the still. */
    const val WANT_ANIMATION = "X-Musix-Animation"
    /** Pass straight through (used to read only the first bytes of a cover). */
    const val PEEK = "X-Musix-Peek"

    const val STILL_PX = 1000
    // Apple Music motion covers converted to WebP can be well over 100 MB.
    private const val MAX_BYTES = 400L * 1024 * 1024

    private lateinit var dir: File
    private lateinit var appContext: Context
    private val aliases = ConcurrentHashMap<String, String>()
    /** The `_<last change>` part of the id each kept cover was downloaded under. */
    private val versions = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    /** Finds the album cover id for a song's cover id (mf-…), from the library. Set by the app. */
    @Volatile var albumCoverOfSong: (String) -> String? = { null }

    /** Animated originals downloaded and kept since the app started (for the log and the device tests). */
    val downloads = java.util.concurrent.atomic.AtomicInteger()

    fun init(context: Context) {
        appContext = context.applicationContext
        dir = File(appContext.filesDir, "covers").apply { mkdirs() }
        prefs().all.forEach { (k, v) ->
            val text = v as? String ?: return@forEach
            if (k.startsWith("v:")) versions[k.removePrefix("v:")] = text else aliases[k] = text
        }
    }

    private fun prefs() = appContext.getSharedPreferences("cover_aliases", Context.MODE_PRIVATE)

    /** Navidrome cover ids end in `_<last change>`; the part before it names the album, song or artist. */
    fun canonical(id: String): String = id.substringBefore('_')

    private fun resolve(id: String): String = canonical(id).let { aliases[it] ?: it }

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9-]"), "_")
    private fun originalFile(key: String) = File(dir, safe(key) + ".anim")
    private fun stillFile(key: String) = File(dir, safe(key) + ".jpg")

    class Local(val original: File, val still: File?)

    /** The saved files for this cover, or null when it isn't one of these animated covers. */
    fun local(id: String): Local? {
        val key = resolve(id)
        val original = originalFile(key)
        if (!original.exists()) return null
        // The cover changed on the server since it was kept: drop it so the new one is downloaded.
        val version = version(id)
        val kept = versions[key]
        if (version.isNotEmpty() && !kept.isNullOrEmpty() && version != kept && canonical(id) == key) {
            AppLog.i("Cover", "Cover $key changed on the server; downloading it again")
            original.delete()
            stillFile(key).delete()
            versions.remove(key)
            prefs().edit().remove("v:$key").apply()
            return null
        }
        return Local(original, stillFile(key).takeIf { it.exists() })
    }

    private fun version(id: String) = id.substringAfter('_', "")

    fun has(id: String) = local(id) != null

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        aliases.clear()
        versions.clear()
        prefs().edit().clear().apply()
    }

    // ---- The interceptor --------------------------------------------------------------------------------

    val interceptor = Interceptor { chain -> intercept(chain) }

    private fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.encodedPath.contains("getCoverArt")) return chain.proceed(request)
        if (request.header(PEEK) != null) return chain.proceed(request.newBuilder().removeHeader(PEEK).build())
        val id = url.queryParameter("id") ?: return chain.proceed(request)
        val wantsAnimation = request.header(WANT_ANIMATION) != null
        val clean = request.newBuilder().removeHeader(WANT_ANIMATION).build()
        val size = url.queryParameter("size")?.toIntOrNull()

        // A song without its own picture often turns out to be its album's: if that's downloading, wait.
        if (canonical(id).startsWith("mf-")) {
            runCatching { albumCoverOfSong(id) }.getOrNull()?.let { album -> pending[resolve(album)]?.let { waitFor(it) } }
        }
        // One request per cover at a time: the album page, now playing, the background and the colour
        // often ask together, and if it's an animated original only the first should download it.
        val gateKey = resolve(id)
        var gate = CompletableFuture<Boolean>()
        while (true) {
            local(id)?.let { return serve(clean, it, wantsAnimation) }
            gate = CompletableFuture<Boolean>()
            val other = pending.putIfAbsent(gateKey, gate) ?: break
            waitFor(other)
        }
        try {
            return fetch(chain, clean, id, size, wantsAnimation)
        } finally {
            pending.remove(gateKey, gate)
            gate.complete(true)
        }
    }

    private fun fetch(chain: Interceptor.Chain, clean: Request, id: String, size: Int?, wantsAnimation: Boolean): Response {
        val response = withoutServerError(chain, clean, id, size)
        if (!response.isSuccessful) return response
        val head = runCatching { response.peekBody(32).bytes() }.getOrNull() ?: return response
        val image = sniff(head) ?: return response
        if (!image.animated) return response
        // Shrunk as asked (Navidrome's own conversion of a GIF): nothing to keep. (FirstFrameDecoder shows
        // its first frame in lists; Android's still decoder can't read these files.)
        if (size != null && image.width in 1..(size * 5 / 4) && image.height in 1..(size * 5 / 4)) return response

        val length = response.body?.contentLength() ?: -1L
        if (length > MAX_BYTES) {
            AppLog.w("Cover", "Animated cover $id is ${mb(length)}, too big to keep; passing it on as is")
            return response
        }
        val key = keyFor(id, response)
        local(key)?.let {
            response.close()
            return serve(clean, it, wantsAnimation)
        }
        val started = System.currentTimeMillis()
        try {
            save(key, response)
        } catch (e: Exception) {
            throw if (e is IOException) e else IOException(e)
        }
        downloads.incrementAndGet()
        version(id).takeIf { it.isNotEmpty() && key == canonical(id) }?.let {
            versions[key] = it
            prefs().edit().putString("v:$key", it).apply()
        }
        AppLog.i(
            "Cover",
            "Kept animated cover $id (${image.kind} ${image.width}×${image.height}, ${mb(originalFile(key).length())}) " +
                "after ${System.currentTimeMillis() - started} ms (size ${if (length > 0) "known" else "not sent by the server"}); " +
                "Navidrome sends the original for every size, so it's downloaded once and lists use a still of it. " +
                "Animated covers on the phone: ${mb(sizeBytes())}",
        )
        return local(key)?.let { serve(clean, it, wantsAnimation) } ?: throw IOException("Cover $id couldn't be saved")
    }

    /**
     * Navidrome sometimes answers a cover request with an error message (`{"subsonic-response": … "failed"}`,
     * HTTP 200) instead of a picture, e.g. when making a small copy of a big GIF fails. The image cache kept
     * that message as the cover, so the cover stayed blank until the cache was cleared (issue #162). Such an
     * answer is logged with Navidrome's reason, asked for once more, and if it fails again passed on as a
     * failure, which is never cached, so the next look tries again.
     */
    private fun withoutServerError(chain: Interceptor.Chain, request: Request, id: String, size: Int?): Response {
        var attempt = 0
        while (true) {
            val response = chain.proceed(request)
            val reason = serverError(response) ?: return response
            response.close()
            AppLog.w("Cover", "Navidrome sent an error instead of cover $id (size ${size ?: "original"}): $reason" + if (attempt == 0) "; asking again" else "")
            if (++attempt >= 2) {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(502)
                    .message("Navidrome couldn't send this cover")
                    .body("".toResponseBody(null))
                    .build()
            }
            Thread.sleep(700)
        }
    }

    /** Navidrome's error message when [response] is one instead of a picture, else null. */
    private fun serverError(response: Response): String? {
        if (!response.isSuccessful) return null
        val type = response.header("Content-Type").orEmpty()
        val head = runCatching { response.peekBody(512).string() }.getOrNull() ?: return null
        if (!type.contains("json") && !type.contains("xml") && !head.trimStart().startsWith("{") && !head.trimStart().startsWith("<")) return null
        if (!head.contains("subsonic-response")) return null
        return Regex(""""message"\s*:\s*"([^"]*)"""").find(head)?.groupValues?.get(1)
            ?: head.replace(Regex("\\s+"), " ").take(200)
    }

    private fun waitFor(future: CompletableFuture<Boolean>) {
        runCatching { future.get(3, TimeUnit.MINUTES) }
    }

    /**
     * Which key to save under. A song without its own picture gets its album's file from Navidrome:
     * when the first 64 KB match the album's kept file, the song is linked to it rather than downloaded
     * again (the server often doesn't send a size to compare).
     */
    private fun keyFor(id: String, response: Response): String {
        val own = canonical(id)
        if (!own.startsWith("mf-")) return own
        val album = runCatching { albumCoverOfSong(id) }.getOrNull()?.let { canonical(it) } ?: return own
        val albumKey = aliases[album] ?: album
        val albumFile = originalFile(albumKey)
        if (!albumFile.exists()) return own
        val head = runCatching { response.peekBody(64 * 1024).bytes() }.getOrNull() ?: return own
        val length = response.body?.contentLength() ?: -1L
        if (length > 0 && length != albumFile.length()) return own
        if (!startsWith(albumFile, head)) return own
        link(own, albumKey)
        AppLog.i("Cover", "Song cover $id is its album's animated cover; using the one already on the phone")
        return albumKey
    }

    private fun startsWith(file: File, head: ByteArray): Boolean = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(head.size)
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            read == head.size && buf.contentEquals(head)
        }
    }.getOrDefault(false)

    private fun link(from: String, to: String) {
        if (from == to) return
        aliases[from] = to
        prefs().edit().putString(from, to).apply()
    }

    private fun save(key: String, response: Response) {
        val body = response.body ?: throw IOException("empty response")
        val expected = body.contentLength()
        val tmp = File(dir, safe(key) + ".part")
        var written = 0L
        body.source().use { source ->
            tmp.sink().buffer().use { sink -> written = sink.writeAll(source) }
        }
        if (expected >= 0 && written != expected) {
            tmp.delete()
            throw IOException("cover download cut short ($written of $expected bytes)")
        }
        val original = originalFile(key)
        if (!tmp.renameTo(original)) {
            tmp.delete()
            throw IOException("couldn't save cover")
        }
        makeStill(original, stillFile(key))
        dedupe(key, original)
    }

    /** If an identical file is already kept under another id (album vs song), keep one and link them. */
    private fun dedupe(key: String, file: File) {
        val twin = dir.listFiles()?.firstOrNull {
            it != file && it.name.endsWith(".anim") && it.length() == file.length() && sameBytes(it, file)
        } ?: return
        val twinKey = twin.name.removeSuffix(".anim")
        // Keep the album's copy when one of the two is an album.
        val (keep, drop) = if (key.startsWith("al-")) key to twinKey else twinKey to key
        link(drop, keep)
        aliases.entries.filter { it.value == drop }.forEach { link(it.key, keep) }
        originalFile(drop).takeIf { it.exists() && originalFile(keep).exists() }?.delete()
        stillFile(drop).takeIf { stillFile(keep).exists() }?.delete()
    }

    private fun sameBytes(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        return try {
            a.inputStream().buffered(64 * 1024).use { x ->
                b.inputStream().buffered(64 * 1024).use { y ->
                    var same = true
                    var i = x.read()
                    while (same && i >= 0) {
                        same = i == y.read()
                        i = x.read()
                    }
                    same
                }
            }
        } catch (e: IOException) {
            false
        }
    }

    /** The first frame, shrunk to at most [STILL_PX] px, as a JPEG. Reads the file, never all of it into memory. */
    private fun makeStill(original: File, still: File) {
        val bitmap = firstFrame(original, STILL_PX) ?: return AppLog.w("Cover", "Couldn't make a still of ${original.name}")
        try {
            val tmp = File(still.path + ".part")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tmp.renameTo(still)
        } finally {
            bitmap.recycle()
        }
    }

    fun firstFrame(file: File, maxPx: Int): Bitmap? = firstFrame(
        maxPx,
        source = { if (Build.VERSION.SDK_INT >= 28) ImageDecoder.createSource(file) else null },
        fallback = { opts -> BitmapFactory.decodeFile(file.path, opts) },
    )

    fun firstFrame(bytes: ByteArray, maxPx: Int): Bitmap? = firstFrame(
        maxPx,
        source = { if (Build.VERSION.SDK_INT >= 28) ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)) else null },
        fallback = { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) },
    )

    /**
     * The first frame of an image, at most [maxPx]. Android's still-image decoder rejects some animated
     * WebPs (Navidrome's ffmpeg conversions of GIFs: "Failed to create image decoder"), while its animation
     * decoder reads them, so that's the fallback: decode the animation and draw its first frame.
     */
    private fun firstFrame(maxPx: Int, source: () -> Any?, fallback: (BitmapFactory.Options?) -> Bitmap?): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val target = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = maxPx.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            runCatching {
                return ImageDecoder.decodeBitmap(source() as ImageDecoder.Source, target)
            }.onFailure { AppLog.d("Cover", "Still decoder failed (${it.message}); drawing the animation's first frame instead") }
            runCatching {
                val drawable = ImageDecoder.decodeDrawable(source() as ImageDecoder.Source, target)
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(android.graphics.Canvas(bitmap))
                return bitmap
            }.onFailure { AppLog.w("Cover", "Animation decoder failed too: ${it.message}") }
        }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            fallback(bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            fallback(BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun serve(request: Request, local: Local, wantsAnimation: Boolean): Response {
        var still = local.still
        if (!wantsAnimation && still == null) {
            // Never made (or removed): make it now rather than hand a list the whole original.
            val target = File(local.original.path.removeSuffix(".anim") + ".jpg")
            makeStill(local.original, target)
            still = target.takeIf { it.exists() }
            if (still == null) AppLog.w("Cover", "No still for ${local.original.name}; sending the original")
        }
        val file = if (wantsAnimation || still == null) local.original else still
        val type = when {
            file == still -> "image/jpeg"
            else -> sniff(file.inputStream().use { s -> ByteArray(32).also { s.read(it) } })?.mime ?: "application/octet-stream"
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", type)
            .header("Content-Length", file.length().toString())
            .body(file.source().buffer().asResponseBody(type.toMediaType(), file.length()))
            .build()
    }

    // ---- Recognising images from their first bytes -------------------------------------------------------

    class Sniffed(val kind: String, val mime: String, val animated: Boolean, val width: Int, val height: Int) {
        /** A WebP in the extended format (VP8X): animated, transparent or with a colour profile. */
        val extendedWebP: Boolean get() = mime == "image/webp" && width > 0
    }

    /** Reads the image type, whether it's animated, and its size from the first 30 bytes. */
    fun sniff(b: ByteArray): Sniffed? {
        fun text(from: Int, value: String) = b.size >= from + value.length &&
            value.indices.all { b[from + it] == value[it].code.toByte() }
        fun u(i: Int) = b[i].toInt() and 0xFF
        return when {
            text(0, "GIF8") && b.size >= 10 ->
                // A GIF might be a single frame; treat it as animated, as Navidrome does.
                Sniffed("GIF", "image/gif", true, u(6) or (u(7) shl 8), u(8) or (u(9) shl 8))
            text(0, "RIFF") && text(8, "WEBP") && text(12, "VP8X") && b.size >= 30 -> {
                val animated = (u(20) and 0x02) != 0
                val w = 1 + (u(24) or (u(25) shl 8) or (u(26) shl 16))
                val h = 1 + (u(27) or (u(28) shl 8) or (u(29) shl 16))
                Sniffed(if (animated) "animated WebP" else "WebP", "image/webp", animated, w, h)
            }
            text(0, "RIFF") && text(8, "WEBP") -> Sniffed("WebP", "image/webp", false, 0, 0)
            b.size > 2 && u(0) == 0xFF && u(1) == 0xD8 -> Sniffed("JPEG", "image/jpeg", false, 0, 0)
            text(1, "PNG") -> Sniffed("PNG", "image/png", false, 0, 0)
            else -> null
        }
    }

    private fun mb(bytes: Long) = if (bytes <= 0) "size unknown" else "%.1f MB".format(bytes / 1_048_576.0)
}
