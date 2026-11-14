package app.siansa.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import app.siansa.AppGraph
import app.siansa.data.LocalPlaylists
import app.siansa.log.AppLog
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * What a cover's colours are worked out from: its own colour ([DynamicAccent.pick]) and its average. One reading per
 * cover gives both a page's header colour ([main]) and its Play button's ([DynamicAccent.of]), so they always agree
 * (the owner, 14 Nov: an artist page's Play in the blue of the photo's eyes under a grey header).
 */
data class CoverTones(val seed: Color?, val average: Color) {
    /**
     * The colour a page's header is painted in: the cover's own colour, or for a cover in black, white and grey its
     * average grey. [Color.Unspecified] for a cover that couldn't be read.
     */
    val main: Color
        get() = when {
            seed == null -> Color.Unspecified
            DynamicAccent.chroma(seed) < DynamicAccent.GREY -> average
            else -> seed
        }

    companion object {
        /** A cover that couldn't be read: no colours of its own. Never kept, so another visit tries again. */
        val UNREADABLE = CoverTones(null, Color.Unspecified)

        /** One line of the kept file: `id`, tab, seed (or `-`), tab, average, as ARGB. */
        fun line(id: String, t: CoverTones): String? {
            val seed = t.seed ?: return null
            return "$id\t${seed.toArgb()}\t${t.average.toArgb()}"
        }

        fun parse(line: String): Pair<String, CoverTones>? {
            val parts = line.split('\t')
            if (parts.size != 3) return null
            val seed = parts[1].toIntOrNull() ?: return null
            val average = parts[2].toIntOrNull() ?: return null
            return parts[0] to CoverTones(Color(seed), Color(average))
        }
    }
}

/**
 * Every cover's [CoverTones], kept on the phone (the owner, 14 Nov: "it should show the correct colour from the get go"):
 * a page opened again, in this run or another, has its colours in its first frame, so nothing on it changes colour while
 * it's open. Covers are read ahead too: as they show in lists ([warm], from [Cover]) and by Download all covers, so a
 * page opened for the first time usually has them as well.
 *
 * The file is replaced, not mixed, when the way colours are picked changes ([VERSION]).
 */
object CoverToneStore {
    /** Raised whenever [DynamicAccent.pick] or [CoverTones.main] change: the colours kept before are read again. */
    const val VERSION = 1

    private val tones = ConcurrentHashMap<String, CoverTones>()
    private val reading = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // One cover at a time: reading ahead never competes with what's on screen.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ahead = Dispatchers.IO.limitedParallelism(1)
    @Volatile private var file: File? = null
    @Volatile private var dirty = false

    private fun key(coverId: String) = app.siansa.covers.CoverVersions.cacheId(coverId)

    fun get(coverId: String): CoverTones? = tones[key(coverId)]

    fun has(coverId: String?): Boolean = coverId != null && tones.containsKey(key(coverId))

    /** Reads the kept colours (once, at start). */
    fun load(context: Context) {
        val f = File(context.filesDir, "cover-tones-v$VERSION.tsv")
        file = f
        context.filesDir.listFiles { it.name.startsWith("cover-tones-v") && it.name != f.name }?.forEach { it.delete() }
        if (!f.exists()) return
        runCatching {
            var n = 0
            f.forEachLine { line -> CoverTones.parse(line)?.let { (id, t) -> tones.putIfAbsent(id, t); n++ } }
            AppLog.d("Colours", "Colours of $n covers kept on the phone")
        }.onFailure { AppLog.w("Colours", "Kept cover colours not readable: ${it.message}") }
    }

    fun put(coverId: String, t: CoverTones) {
        if (t.seed == null) return
        if (tones.put(key(coverId), t) == t) return
        if (!dirty) {
            dirty = true
            scope.launch {
                delay(SAVE_AFTER_MS)
                save()
            }
        }
    }

    private fun save() {
        val f = file ?: return
        dirty = false
        runCatching {
            val tmp = File(f.parentFile, f.name + ".new")
            tmp.bufferedWriter().use { out -> tones.forEach { (id, t) -> CoverTones.line(id, t)?.let { out.write(it); out.newLine() } } }
            tmp.renameTo(f)
        }.onFailure { AppLog.w("Colours", "Couldn't keep cover colours: ${it.message}") }
    }

    /** Works [coverId]'s colours out in the background, when they aren't known yet: a page opened from it then has them. */
    fun warm(context: Context, graph: AppGraph, coverId: String?) {
        if (coverId == null || has(coverId) || !reading.add(key(coverId))) return
        scope.launch(ahead) {
            try {
                read(context, graph, coverId)?.let { put(coverId, it) }
            } finally {
                reading.remove(key(coverId))
            }
        }
    }

    /** Reads [coverId]'s colours from its small cover (on the phone after it has shown once). Null when it can't be read. */
    suspend fun read(context: Context, graph: AppGraph, coverId: String): CoverTones? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = ImageRequest.Builder(context)
            if (coverId.startsWith(LocalPlaylists.FILE_COVER)) builder.data(File(coverId.removePrefix(LocalPlaylists.FILE_COVER)))
            else builder.data(graph.source.coverUrl(coverId, SMALL_COVER)).diskCacheKey(app.siansa.covers.CoverCache.diskKey(coverId, SMALL_COVER))
            val request = builder
                .memoryCacheKey("cover:${key(coverId)}:palette")
                .allowHardware(false)
                .size(120)
                .build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
            val bitmap = result.image.toBitmap()
            // Every colour, black and white too (the palette leaves them out at first): a black and white cover is
            // silver, and how much of a cover is black counts against a small bright spot (issue #994).
            val palette = Palette.from(bitmap).maximumColorCount(16).clearFilters().generate()
            val one = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
            // The pixels too: a small title's true colour is in them, not in the palette's blend of it (issue #1027).
            val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            CoverTones(DynamicAccent.pick(palette.swatches.map { CoverSwatch(Color(it.rgb), it.population) }, pixels), Color(one))
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            AppLog.w("Colours", "Couldn't read the colours of cover $coverId: ${it.message}")
        }.getOrNull()?.takeIf { it.seed != null }
    }

    private const val SAVE_AFTER_MS = 5_000L
}

/**
 * [coverId]'s [CoverTones]: at once when they're known (kept on the phone, or read ahead), else read now. Null while
 * they're being read, [CoverTones.UNREADABLE] for a cover that couldn't be read, null for no cover.
 */
@Composable
fun rememberCoverTones(coverId: String?): CoverTones? {
    val graph = LocalGraph.current
    val context = LocalContext.current
    var tones by remember(coverId) { mutableStateOf(coverId?.let { CoverToneStore.get(it) }) }
    LaunchedEffect(coverId) {
        if (coverId == null || tones != null) return@LaunchedEffect
        val read = CoverToneStore.read(context, graph, coverId)
        if (read != null) CoverToneStore.put(coverId, read)
        tones = read ?: CoverTones.UNREADABLE
    }
    return tones
}
