package app.siansa.playback

import android.os.SystemClock
import app.siansa.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The song change last made, for the "Bad blend" button (the owner, 9 Nov): one tap sends it, with how it was made,
 * to Siansa Beats' list, and writes it to the log. Only while the button is turned on ([enabled], Settings › Help),
 * and only for DJ playback's blends ([Report.dj]).
 */
object LastBlend {
    /** Whether the button starts turned on: in Siansa Dev, the build the owner tests in. Off in the normal app. */
    val onByDefault: Boolean get() = BuildConfig.BUILD_TYPE == "dev"

    /**
     * Whether blends are noted and the button shows: Settings › Help's "Bad blend button" (the owner, 12 Nov), set
     * when the app starts and when the switch is moved.
     */
    @Volatile
    var enabled: Boolean = onByDefault

    /** The button shows from a blend's start until this long after it ends. */
    const val SHOWN_AFTER_MS = 60_000L

    data class Report(
        val fromId: String,
        val toId: String,
        /** "phrase on phrase", "bar on bar", "beat on beat", "short blend", "fade", "cold end"… */
        val technique: String,
        /** Where in the song going out the blend started (ms of its own time). */
        val atMs: Long,
        val overlapMs: Long,
        /** When it started ([SystemClock.elapsedRealtime]). */
        val startedAt: Long,
        val dj: Boolean,
        val why: String,
        val bars: Int? = null,
        val fromBpm: Double? = null,
        val toBpm: Double? = null,
        val fromRhythm: Float? = null,
        val toRhythm: Float? = null,
        val speed: Float? = null,
        val fromLockMs: Double? = null,
        val toLockMs: Double? = null,
    ) {
        /** What goes to Siansa Beats: the plugin's `POST /api/bad-blends` body. */
        fun toJson(): JsonObject = buildJsonObject {
            put("from_id", fromId)
            put("to_id", toId)
            put("technique", technique)
            put("details", buildJsonObject {
                put("at_ms", atMs)
                put("overlap_ms", overlapMs)
                put("why", why)
                bars?.let { put("bars", it) }
                fromBpm?.let { put("from_bpm", JsonPrimitive(Math.round(it * 10) / 10.0)) }
                toBpm?.let { put("to_bpm", JsonPrimitive(Math.round(it * 10) / 10.0)) }
                fromRhythm?.let { put("from_rhythm", it) }
                toRhythm?.let { put("to_rhythm", it) }
                speed?.let { put("speed", it) }
                fromLockMs?.let { put("from_lock_ms", it) }
                toLockMs?.let { put("to_lock_ms", it) }
            })
        }
    }

    private val last = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = last

    /** Notes a blend just begun from [fromId] at [atMs] of it into [toId], as [plan] made it. */
    fun record(fromId: String, toId: String, atMs: Long, plan: FadePlan, dj: Boolean, now: Long = SystemClock.elapsedRealtime()) {
        if (!enabled) return
        last.value = of(fromId, toId, atMs, plan, dj, now)
    }

    /** The report of a blend ([record] without keeping it): for tests. */
    fun of(fromId: String, toId: String, atMs: Long, plan: FadePlan, dj: Boolean, now: Long): Report {
        val beat = plan.beat
        val technique = when {
            plan.technique != null -> plan.technique.label + when {
                beat?.phraseBars != null -> ", phrase on phrase"
                beat?.onBars == true -> ", bar on bar"
                beat != null -> ", beat on beat"
                else -> ""
            }
            beat?.phraseBars != null -> "phrase on phrase"
            beat?.onBars == true -> "bar on bar"
            beat != null -> "beat on beat"
            plan.coldEnd -> "cold end"
            plan.overlapMs <= BeatMatch.CLASH_MS -> "short blend"
            else -> "fade"
        }
        return Report(
            fromId, toId, technique, atMs, plan.overlapMs, now, dj, plan.why,
            bars = beat?.bars, fromBpm = beat?.outgoing?.bpm, toBpm = beat?.incoming?.bpm,
            fromRhythm = beat?.outgoing?.rhythm, toRhythm = beat?.incoming?.rhythm, speed = beat?.speed,
            fromLockMs = beat?.outgoing?.lockMs, toLockMs = beat?.incoming?.lockMs,
        )
    }

    /** Whether the button shows for [report] at [now]: a DJ blend under way or ended less than [SHOWN_AFTER_MS] ago. */
    fun showing(report: Report?, now: Long): Boolean =
        enabled && report != null && report.dj && now - report.startedAt <= report.overlapMs + SHOWN_AFTER_MS

    /** Where the Siansa Beats (dev) plugin takes a bad blend on to Siansa Beats. */
    const val PATH = "/plugins/${ProfileStore.PLUGIN_NAME}/api/bad-blends"

    /**
     * Marks [report] as a bad blend: written to the log, and sent with [post] (the AudioMuse client's plugin POST) to
     * Siansa Beats' list. Null when it was sent, else why not (it's in the log either way).
     */
    suspend fun mark(report: Report, post: suspend (String, String) -> Unit): String? {
        app.siansa.log.AppLog.w(
            "Crossfade",
            "Bad blend marked: ${report.fromId} into ${report.toId} (${report.technique}) · ${report.why} · " +
                "started at ${report.atMs / 1000} s of the old song" +
                (report.fromRhythm?.let { " · confidence out %.2f, in %.2f".format(java.util.Locale.ROOT, it, report.toRhythm ?: 0f) }.orEmpty()),
            event = "crossfade.bad_blend", fields = mapOf("technique" to report.technique, "bars" to report.bars),
        )
        return try {
            post(PATH, report.toJson().toString())
            app.siansa.log.AppLog.i("Crossfade", "Bad blend sent to Siansa Beats")
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            app.siansa.log.AppLog.w("Crossfade", "Couldn't send the bad blend to Siansa Beats: ${e.message}")
            e.message ?: "Couldn't reach the Siansa Beats (dev) plugin"
        }
    }

    /** Forgets the last blend (once it's been marked). */
    fun clear() {
        last.value = null
    }
}
