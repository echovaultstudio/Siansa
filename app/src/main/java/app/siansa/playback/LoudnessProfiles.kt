package app.siansa.playback

import app.siansa.data.AppJson
import app.siansa.net.arr
import app.siansa.net.dbl
import app.siansa.net.int
import app.siansa.net.str
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.util.Base64

/**
 * Where song loudness and fade-in/fade-out shapes come from (issue #60): song tags, or the Siansa plugin on the
 * AudioMuse server (`audiomuse-plugin/` in this repository, issue #188).
 */
enum class LoudnessSource(val label: String, val detail: String) {
    TAGS("Song tags", "ReplayGain tags in the files; plain crossfades"),
    PLUGIN("Siansa plugin", "Measured by the Siansa plugin on your AudioMuse server");

    companion object {
        /** The stored choice. */
        fun parse(value: String?): LoudnessSource = entries.firstOrNull { it.name == value } ?: TAGS
    }
}

/** One level on a ramp: the song is at [db] relative to its own loudness at [ms] (see [TrackProfile]). */
data class RampPoint(val db: Int, val ms: Long)

/**
 * The beat grid at one end of a song, measured by the Siansa plugin from its first or last half minute (issue
 * #646): [bpm] to a fraction, [beatMs] a beat near that end and [barMs] a downbeat (both in ms from the start of
 * the song; the downbeat only when the bars are clear), [rhythm] how beat-driven the end is (0 to 1: a drum
 * outro near 1, a pad or a spoken intro near 0) and [steady] how well the beats keep to one tempo (0 to 1).
 */
data class BeatEnd(val bpm: Double, val beatMs: Double, val barMs: Double?, val rhythm: Float, val steady: Float) {
    val beatLengthMs: Double get() = 60_000.0 / bpm

    /** A beat-driven, steady end: one a blend can be laid on, beat for beat. */
    val matchable: Boolean get() = bpm > 0 && rhythm >= BeatMatch.MIN_RHYTHM && steady >= BeatMatch.MIN_STEADY

    /** For the log (issue #744): "128.0 BPM, beat 0.82, steady 0.91, bars found". */
    fun describe(): String = "%.1f BPM, beat %.2f, steady %.2f, %s".format(
        java.util.Locale.ROOT, bpm, rhythm, steady, if (barMs != null) "bars found" else "no bars found",
    )
}

/**
 * A song's measured loudness and how it starts and ends (the MixRamp idea, from the MPD player):
 * - [refLufs]: overall loudness in LUFS, measured from the audio, so it works without ReplayGain tags;
 * - [startRamp]: for each level (dB relative to [refLufs], −90 up to +6), how long after the start the song
 *   first reaches it. A hard hit reaches loud levels at once; a slow intro takes seconds;
 * - [endRamp]: for each level, how long before the end the song is last at it. A long fade-out is quiet for
 *   seconds before the end.
 */
data class TrackProfile(
    val trackId: String,
    val refLufs: Double?,
    val startRamp: List<RampPoint>,
    val endRamp: List<RampPoint>,
    val durationMs: Long = 0,
    /** The beat grid at the start, once the plugin has measured it (issue #646); null without a beat. */
    val intro: BeatEnd? = null,
    /** The beat grid at the end. */
    val outro: BeatEnd? = null,
) {
    /** How long after the start the song first gets up to [db] (relative), or null if it never does. */
    fun reachesAfterMs(db: Int): Long? = startRamp.firstOrNull { it.db >= db }?.ms

    /** How long before the end the song is last at [db] (relative) or louder, or null if it never is. */
    fun quietForLastMs(db: Int): Long? = endRamp.firstOrNull { it.db >= db }?.ms

    /**
     * How long before the end the song is last at exactly the measured level [db], or null when the ramp doesn't
     * have that level: unlike [quietForLastMs], never a louder level standing in, which would make a quiet tail look
     * like silence (issue #628).
     */
    fun lastAtExactlyMs(db: Int): Long? = endRamp.firstOrNull { it.db == db }?.ms
}

/** The Siansa plugin's ramp blobs: up to 15 entries of (int8 dB, little-endian uint16 index of 100 ms chunks). */
object RampCodec {
    const val CHUNK_MS = 100L

    fun decode(base64: String?): List<RampPoint> {
        if (base64.isNullOrBlank()) return emptyList()
        val bytes = runCatching { Base64.getDecoder().decode(base64.trim()) }.getOrNull() ?: return emptyList()
        if (bytes.size % 3 != 0) return emptyList()
        return (bytes.indices step 3).map { i ->
            val db = bytes[i].toInt() // signed int8
            val chunk = (bytes[i + 1].toInt() and 0xFF) or ((bytes[i + 2].toInt() and 0xFF) shl 8)
            RampPoint(db, chunk * CHUNK_MS)
        }.sortedBy { it.db }
    }
}

/** Reading the Siansa plugin's `GET /plugins/siansa_analysis/api/profiles?ids=…` answers. */
object PluginProfiles {
    const val PATH = "/plugins/siansa_analysis/api/profiles"

    /** The AudioMuse host rejects request lines over ~4 KB, and the plugin reads at most 500 ids. */
    const val URL_BUDGET_BYTES = 3_500
    const val MAX_IDS = 500

    /** Splits [ids] into batches whose query (after [base]) stays within the byte budget and id limit. */
    fun batches(ids: List<String>, base: String, budget: Int = URL_BUDGET_BYTES, maxIds: Int = MAX_IDS): List<List<String>> {
        val out = ArrayList<List<String>>()
        var current = ArrayList<String>()
        val start = base.length + "?ids=".length
        var length = start
        for (id in ids.distinct()) {
            val encoded = URLEncoder.encode(id, "UTF-8").length
            var add = encoded + if (current.isEmpty()) 0 else 1 // the comma between ids
            if (current.isNotEmpty() && (length + add > budget || current.size >= maxIds)) {
                out += current
                current = ArrayList()
                length = start
                add = encoded
            }
            current += id
            length += add
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** The query for one batch. */
    fun query(ids: List<String>): String = "?ids=" + ids.joinToString(",") { URLEncoder.encode(it, "UTF-8") }

    /** The answer's layout this reads; a newer one is skipped (falling back to tags) rather than misread. */
    const val SCHEMA_VERSION = 1

    /** The profiles in one answer; songs still being analysed or that failed are left out. */
    fun parse(body: JsonObject): List<TrackProfile> {
        val schema = body.int("schema_version")
        if (schema != null && schema != SCHEMA_VERSION) return emptyList()
        return body.arr("profiles").mapNotNull { p -> profile(p) }
    }

    private fun profile(p: JsonObject): TrackProfile? {
        val id = p.str("track_id") ?: return null
        return TrackProfile(
            trackId = id,
            refLufs = p.dbl("ref_lufs")?.takeIf { it.isFinite() },
            startRamp = RampCodec.decode(p.str("start_ramp")),
            endRamp = RampCodec.decode(p.str("end_ramp")),
            durationMs = p.int("duration_ms")?.toLong() ?: 0,
            intro = beatEnd(p, "intro"),
            outro = beatEnd(p, "outro"),
        )
    }

    /** The `<prefix>_bpm`, `_beat_ms`, `_bar_ms`, `_rhythm` and `_steady` fields of one end, or null without them. */
    private fun beatEnd(p: JsonObject, prefix: String): BeatEnd? {
        val bpm = p.dbl("${prefix}_bpm")?.takeIf { it.isFinite() && it > 0 } ?: return null
        val beat = p.dbl("${prefix}_beat_ms")?.takeIf { it.isFinite() } ?: return null
        return BeatEnd(
            bpm = bpm,
            beatMs = beat,
            barMs = p.dbl("${prefix}_bar_ms")?.takeIf { it.isFinite() },
            rhythm = (p.dbl("${prefix}_rhythm") ?: 0.0).toFloat().coerceIn(0f, 1f),
            steady = (p.dbl("${prefix}_steady") ?: 0.0).toFloat().coerceIn(0f, 1f),
        )
    }

    fun parse(text: String): List<TrackProfile> =
        (runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.let(::parse) ?: emptyList()
}
