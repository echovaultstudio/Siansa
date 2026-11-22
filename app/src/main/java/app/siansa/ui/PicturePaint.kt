package app.siansa.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.siansa.AppGraph
import app.siansa.data.PictureColours
import app.siansa.log.AppLog
import app.siansa.net.TrackFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The colours a card's picture is painted in (the owner, 20 Nov: "dull colours with bad fades are my biggest gripe"):
 * two bright ones ([first], [second]) for its light, and [deep], a dark shade of the first, for its ground and for the
 * fade under a name, instead of black.
 */
data class PicturePaint(val first: Color, val second: Color, val deep: Color, val energy: Float = 0.5f) {
    companion object {
        /** From any two colours: each made bright enough ([lift]), the ground a deep shade of the first. */
        fun of(a: Color, b: Color, lifted: Boolean = true): PicturePaint {
            val one = if (lifted) lift(a) else a
            val two = if (lifted) lift(b) else b
            return PicturePaint(one, two, deep(one))
        }
    }
}

/** Where the row being drawn takes its picture colours from (the owner, 20 Nov). */
val LocalPictureColours = androidx.compose.runtime.compositionLocalOf { PictureColours.COVERS }

/**
 * A colour made bright enough for a card (the owner, 20 Nov: "a brown sleeve becomes amber, not mud"): at least so
 * colourful and between mid and light. A grey one (a black and white cover) turns a cool silver, never flat grey.
 */
internal fun lift(c: Color): Color {
    val (h, s, l) = hsl(c)
    if (s < GREY_SATURATION || chroma(c) < GREY_CHROMA) return hslColor(SILVER_HUE, SILVER_SATURATION, max(l, SILVER_LIGHT))
    return hslColor(h, max(s, LIFT_SATURATION), l.coerceIn(LIFT_LIGHT_LOW, LIFT_LIGHT_HIGH))
}

/** A deep shade of [c] (the same colour, dark), for a picture's ground and a name's fade. */
internal fun deep(c: Color): Color {
    val (h, s, _) = hsl(c)
    return hslColor(h, min(s, DEEP_SATURATION), DEEP_LIGHT)
}

/** A set's pair for the card [key]: the same card always takes the same pair, the cards of a row different ones. */
internal fun setPaint(colours: PictureColours, key: String): PicturePaint? {
    val pairs = SETS[colours] ?: return null
    val (a, b) = pairs[Math.floorMod(key.hashCode(), pairs.size)]
    return PicturePaint.of(Color(a), Color(b), lifted = false)
}

private val SETS: Map<PictureColours, List<Pair<Long, Long>>> = mapOf(
    // Siansa's own: amber, frost and black.
    PictureColours.SIANSA to listOf(0xFFFFB547 to 0xFFD8E3EC, 0xFFFFB547 to 0xFFB5793A, 0xFFD8E3EC to 0xFF6F86A0),
    PictureColours.MONO to listOf(0xFFF2F2F3 to 0xFF8A8A90, 0xFFC8C8CC to 0xFF5A5A60),
)

/** A swatch of [colours] for its chip: its first pair, or a wheel of moods for By mood. */
internal fun swatchOf(colours: PictureColours): List<Color> = when (colours) {
    PictureColours.COVERS -> listOf(Color(0xFFE8A04A), Color(0xFFC4583A))
    PictureColours.MOOD -> MOODS.map { it.colour }.take(5)
    else -> SETS[colours].orEmpty().firstOrNull()?.let { listOf(Color(it.first), Color(it.second)) }.orEmpty()
}

/** One mood a song can have, its colour, and the keys Siansa Beats or AudioMuse-AI give its share under. */
internal class MoodColour(val label: String, val colour: Color, val keys: List<String>)

/** Each mood's colour (the owner, 20 Nov: "a colour that suits the mood of the playlist"). */
internal val MOODS = listOf(
    MoodColour("Happy", Color(0xFFFFC861), listOf("happy")),
    MoodColour("Cheerful", Color(0xFFFF9A5C), listOf("mirex_cheerful")),
    MoodColour("Party", Color(0xFFFF6FB0), listOf("party", "danceable")),
    MoodColour("Intense", Color(0xFFFF5A4A), listOf("mirex_intense", "aggressive")),
    MoodColour("Passionate", Color(0xFFD07CFF), listOf("mirex_passionate")),
    MoodColour("Relaxed", Color(0xFF5FE0C4), listOf("relaxed")),
    MoodColour("Sad", Color(0xFF6FA8FF), listOf("sad")),
    MoodColour("Bittersweet", Color(0xFFA9A2E0), listOf("mirex_bittersweet")),
    MoodColour("Quirky", Color(0xFFB6E05A), listOf("mirex_humorous")),
)

/**
 * A mix's colours from its songs' moods: each mood's share averaged over the songs that have one, the two strongest
 * moods' colours, brighter the more energetic the songs are (Siansa Beats' arousal; mid when not known). Null when no
 * song has moods.
 */
internal fun moodPaint(songs: List<TrackFeatures>): PicturePaint? {
    fun share(f: TrackFeatures, m: MoodColour): Double? = m.keys.firstNotNullOfOrNull { f.traits[it] ?: f.moods[it] }
    val heard = songs.filter { f -> MOODS.any { share(f, it) != null } }
    if (heard.isEmpty()) return null
    val strength = MOODS.associateWith { m -> heard.sumOf { share(it, m) ?: 0.0 } / heard.size }
    val top = strength.entries.sortedByDescending { it.value }.filter { it.value > 0.0 }.take(2)
    if (top.isEmpty()) return null
    val arousal = heard.mapNotNull { it.arousal }.takeIf { it.isNotEmpty() }?.average()
    val energy = arousal?.let { ((it - 1.0) / 8.0).coerceIn(0.0, 1.0).toFloat() } ?: 0.5f
    fun bright(c: Color): Color {
        val (h, s, _) = hsl(c)
        return hslColor(h, s * (MOOD_SATURATION_LOW + (1 - MOOD_SATURATION_LOW) * energy), MOOD_LIGHT_LOW + (MOOD_LIGHT_HIGH - MOOD_LIGHT_LOW) * energy)
    }
    val one = bright(top[0].key.colour)
    val two = bright((top.getOrNull(1)?.key ?: top[0].key).colour)
    return PicturePaint(one, two, deep(one), energy)
}

/**
 * Mixes' mood colours ([moodPaint]), kept on the phone so a card has them in its first frame next time. Keyed by its
 * songs ([key]), so a mix made again with other songs is read again.
 */
object MoodColourStore {
    private val paints = ConcurrentHashMap<String, PicturePaint>()
    private val tried = ConcurrentHashMap.newKeySet<String>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> get() = _version
    // A few mixes at a time: Home asks for all its rows at once.
    private val asking = Semaphore(2)
    @Volatile private var file: File? = null

    fun key(ids: List<String>): String = ids.take(SONGS_READ).sorted().joinToString(",").hashCode().toString(16)

    fun get(key: String): PicturePaint? = paints[key]

    fun load(context: Context) {
        val f = File(context.filesDir, "mood-colours-v1.tsv")
        file = f
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 4) paints[p[0]] = PicturePaint(Color(p[1].toInt()), Color(p[2].toInt()), Color(p[3].toInt()), p.getOrNull(4)?.toFloatOrNull() ?: 0.5f)
            }
        }.onFailure { AppLog.w("Colours", "Kept mood colours not readable: ${it.message}") }
    }

    /** Reads the moods of [ids]' songs and works out their colours, once a run per mix; nothing when no service can. */
    suspend fun read(graph: AppGraph, key: String, ids: List<String>) {
        if (paints.containsKey(key) || !tried.add(key)) return
        val s = graph.settings.state.value
        if (!s.audioMuseReady && !s.beatsSoundReady) return
        asking.withPermit {
            val songs = runCatching { graph.audioMuse.features(ids.take(SONGS_READ)) }
                .onFailure { AppLog.w("Colours", "Couldn't read the moods of a mix's songs: ${it.message}") }
                .getOrNull() ?: return
            val paint = moodPaint(songs) ?: return
            AppLog.d("Colours", "A mix's mood colours from ${songs.size} songs")
            paints[key] = paint
            _version.value++
            save()
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".new")
            tmp.bufferedWriter().use { out ->
                paints.forEach { (k, p) -> out.write("$k\t${p.first.toArgb()}\t${p.second.toArgb()}\t${p.deep.toArgb()}\t${p.energy}"); out.newLine() }
            }
            tmp.renameTo(f)
        }.onFailure { AppLog.w("Colours", "Couldn't keep mood colours: ${it.message}") }
    }

    private const val SONGS_READ = 40
}

/** Reads a mix's mood colours ([MoodColourStore.read]); none in screenshot scenes, which have no server. */
val LocalMoodReader = staticCompositionLocalOf<(suspend (String, List<String>) -> Unit)?> { null }

/**
 * The mood colours of the mix of [songIds], read when not known yet. Held as they were when the card came on screen,
 * so it doesn't change colour while it's seen (the owner, 20 Nov: "the colour cards flicker when moving"); colours read
 * meanwhile show the next time it comes on screen.
 */
@Composable
internal fun rememberMoodPaint(songIds: List<String>): PicturePaint? {
    val reader = LocalMoodReader.current
    if (songIds.isEmpty()) return null
    val key = remember(songIds) { MoodColourStore.key(songIds) }
    if (reader != null) LaunchedEffect(key) { reader(key, songIds) }
    return remember(key) { MoodColourStore.get(key) }
}

/** [c] as hue (0 to 360), saturation and lightness (0 to 1). */
internal fun hsl(c: Color): Triple<Float, Float, Float> {
    val hi = max(c.red, max(c.green, c.blue))
    val lo = min(c.red, min(c.green, c.blue))
    val l = (hi + lo) / 2f
    val d = hi - lo
    val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    return Triple(hue(c), s.coerceIn(0f, 1f), l)
}

internal fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = l - c / 2f
    val (r, g, b) = when ((h.mod(360f) / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}

private const val GREY_SATURATION = 0.12f
private const val GREY_CHROMA = 0.06f
private const val SILVER_HUE = 220f
private const val SILVER_SATURATION = 0.3f
private const val SILVER_LIGHT = 0.7f
private const val LIFT_SATURATION = 0.58f
private const val LIFT_LIGHT_LOW = 0.5f
private const val LIFT_LIGHT_HIGH = 0.68f
private const val DEEP_SATURATION = 0.7f
private const val DEEP_LIGHT = 0.12f
private const val MOOD_SATURATION_LOW = 0.6f
private const val MOOD_LIGHT_LOW = 0.48f
private const val MOOD_LIGHT_HIGH = 0.66f
