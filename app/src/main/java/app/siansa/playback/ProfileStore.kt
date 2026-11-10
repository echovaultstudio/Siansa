package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.log.AppLog
import app.siansa.net.BeatsApi
import app.siansa.net.SourceKind
import app.siansa.net.int
import app.siansa.net.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Song loudness and fade shapes (issue #60), from wherever Settings › Playback says they come from: Siansa Beats when
 * it's set up in Settings › Connections (Siansa Dev), else the Siansa plugin on the AudioMuse server; with song tags,
 * there are no profiles and everything falls back to ReplayGain and plain crossfades.
 *
 * Only the songs about to play are asked for, a few at a time, and kept in memory (about 100 bytes each).
 * Songs the plugin hasn't analysed are asked for again after [RETRY_MISSING_MS]; after a failed request,
 * nothing is asked for [BACK_OFF_MS], so a missing or broken plugin costs one request now and then.
 *
 * [get] does a GET of the plugin's path and query, answered by Siansa Beats or the plugin
 * ([app.siansa.AppGraph.siansaBeats]); [beats] asks Siansa Beats what it can do, for [check].
 */
class ProfileStore(
    private val get: suspend (String) -> JsonElement,
    private val settings: () -> AppSettings,
    private val now: () -> Long = System::currentTimeMillis,
    private val beats: (suspend () -> BeatsApi.Capabilities)? = null,
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, TrackProfile>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackProfile>?) = size > MAX_CACHED
    }
    private val missingUntil = HashMap<String, Long>()
    /** When each cached profile is to be asked for again ([refreshAfter]). */
    private val freshUntil = HashMap<String, Long>()
    private var backOffUntil = 0L
    private val fetching = Mutex()

    /** Why the last request failed, or null when it worked. */
    @Volatile var problem: String? = null
        private set

    /** Profiles are used: the switch is on measured loudness, and Siansa Beats or AudioMuse is set up. */
    val active: Boolean get() = settings().let { it.loudnessSource == LoudnessSource.PLUGIN && (it.beatsReady || it.audioMuseReady) }

    /** Who answers, for the log and messages. */
    private val source: String get() = if (settings().beatsReady) "Siansa Beats" else "The Siansa plugin"

    /** The profile already fetched for [id], or null (none yet, or profiles aren't used). */
    fun cached(id: String): TrackProfile? = if (!active) null else synchronized(lock) { cache[id] }

    /**
     * Fetches the profiles of [ids] not already here, and returns all of [ids]' profiles that are known.
     * Never throws: a failure is logged, kept in [problem], and the songs simply have no profile.
     */
    suspend fun fetch(ids: List<String>): Map<String, TrackProfile> {
        if (!active || ids.isEmpty()) return emptyMap()
        fetching.withLock {
            val time = now()
            val need = synchronized(lock) {
                if (time < backOffUntil) emptyList()
                else ids.distinct().filter {
                    (it !in cache || (freshUntil[it] ?: 0L) <= time) && (missingUntil[it] ?: 0L) <= time
                }
            }
            if (need.isNotEmpty()) {
                val base = settings().audioMuseUrl + PluginProfiles.PATH
                for (batch in PluginProfiles.batches(need, base)) {
                    if (!fetchBatch(batch)) break
                }
            }
        }
        return synchronized(lock) { ids.mapNotNull { id -> cache[id]?.let { id to it } }.toMap() }
    }

    private suspend fun fetchBatch(batch: List<String>): Boolean {
        val answer = try {
            get(PluginProfiles.PATH + PluginProfiles.query(batch)) as? JsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(friendly(e.message))
            return false
        }
        if (answer == null) {
            fail("$source sent an unexpected answer")
            return false
        }
        val schema = answer.int("schema_version")
        if (schema != null && schema != PluginProfiles.SCHEMA_VERSION) {
            fail("$source answers in a newer layout ($schema) than Siansa reads (${PluginProfiles.SCHEMA_VERSION}); using song tags")
            return false
        }
        val profiles = PluginProfiles.parse(answer)
        val time = now()
        synchronized(lock) {
            profiles.forEach {
                cache[it.trackId] = it
                freshUntil[it.trackId] = time + refreshAfter(it)
            }
            if (freshUntil.size > 2 * MAX_CACHED) freshUntil.keys.retainAll(cache.keys)
            val found = profiles.mapTo(HashSet()) { it.trackId }
            batch.filter { it !in found }.forEach { missingUntil[it] = time + RETRY_MISSING_MS }
            if (missingUntil.size > MAX_CACHED) missingUntil.entries.removeAll { it.value <= time }
        }
        problem = null
        val measured = profiles.count { it.refLufs != null }
        AppLog.i("Loudness", "$source profiles: ${profiles.size} of ${batch.size} songs ($measured with loudness)")
        return true
    }

    private fun fail(why: String) {
        synchronized(lock) { backOffUntil = now() + BACK_OFF_MS }
        if (problem != why) AppLog.w("Loudness", "${why.trimEnd('.')}. Trying again in ${BACK_OFF_MS / 60_000} minutes; until then song tags are used")
        problem = why
    }

    /** For the "Test" button: whether Siansa Beats or the plugin answers, in plain words. Clears any back-off. */
    suspend fun check(): CheckResult {
        val s = settings()
        if (s.beatsReady) {
            synchronized(lock) { backOffUntil = 0L }
            return checkBeats(s)
        }
        if (!s.audioMuseReady) return CheckResult(false, "Set up Siansa Beats or AudioMuse in Settings › Connections first")
        synchronized(lock) { backOffUntil = 0L }
        return try {
            val health = get(HEALTH_PATH) as? JsonObject
            if (health == null || health.str("plugin") !in PLUGIN_NAMES) {
                CheckResult(false, "AudioMuse answered, but not as Siansa Beats (dev): install it from the dev catalog")
            } else if (health.str("status") != "ok") {
                CheckResult(false, "The Siansa plugin is there but not ready (${health.str("status") ?: "no status"})")
            } else {
                problem = null
                val version = health.str("plugin_version")?.let { " $it" } ?: ""
                // Siansa Beats (dev) says whether Siansa Beats answers it.
                val beats = health["beats"] as? JsonObject
                val reachable = beats?.get("reachable")?.toString() == "true"
                val beatsNote = when {
                    beats == null -> ""
                    reachable -> " · Siansa Beats ${beats.str("version") ?: ""} answers".trimEnd()
                    else -> " · Siansa Beats doesn't answer: ${beats.str("problem") ?: "check its address on the plugin's page"}"
                }
                AppLog.i("Loudness", "Siansa Beats (dev)$version is working$beatsNote")
                CheckResult(beats == null || reachable, "Siansa Beats (dev)$version is working$beatsNote")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val why = friendly(e.message)
            AppLog.w("Loudness", "Siansa plugin check failed: $why")
            CheckResult(false, why)
        }
    }

    private suspend fun checkBeats(s: AppSettings): CheckResult = try {
        val caps = (beats ?: error("no Siansa Beats here")).invoke()
        val result = beatsResult(caps, s.sourceKind)
        if (result.ok) problem = null
        AppLog.i("Loudness", "Siansa Beats check: ${result.message}")
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val why = e.message ?: "Couldn't reach Siansa Beats"
        AppLog.w("Loudness", "Siansa Beats check failed: $why")
        CheckResult(false, why)
    }

    data class CheckResult(val ok: Boolean, val message: String)

    companion object {
        // Siansa Dev's experiment: profiles from Siansa Beats (dev), next to the Siansa plugin (see PluginProfiles.PATH).
        const val PLUGIN_NAME = "siansa_beats"
        /** The plugin by its name now, or as it was called before the rename (issue #360) until that's replaced. */
        val PLUGIN_NAMES = setOf(PLUGIN_NAME)
        const val HEALTH_PATH = "/plugins/siansa_beats/api/health"
        const val MAX_CACHED = 3_000

        /**
         * How long a profile is kept before it's asked for again. One without Siansa Beats' grid ([TrackProfile.fromBeats])
         * is asked for again after [RETRY_MISSING_MS], as Siansa Beats may have analysed the song since (the owner's bad
         * blends, 9 Nov: India in Me was blended on a profile fetched before Siansa Beats had it, kept all morning);
         * one with it after [REFRESH_MS], in case it was worked out again.
         */
        fun refreshAfter(profile: TrackProfile): Long = if (profile.fromBeats) REFRESH_MS else RETRY_MISSING_MS
        const val REFRESH_MS = 6 * 60 * 60_000L
        const val RETRY_MISSING_MS = 30 * 60_000L
        const val BACK_OFF_MS = 5 * 60_000L

        /**
         * What Siansa Beats' [caps] say for the Test button and the DJ check: working when it speaks this app's API and
         * has beat grids, for songs of this kind of server ([kind]: it reads Navidrome, Emby and Jellyfin since 0.23,
         * the ones added on its page).
         */
        fun beatsResult(caps: BeatsApi.Capabilities, kind: SourceKind): CheckResult = when {
            caps.api < BeatsApi.API -> CheckResult(false, "This Siansa Beats (${caps.version}) is too old for the app: update it to 0.20 or newer")
            caps.musicServer != null && caps.musicServer != BeatsApi.kindName(kind) ->
                CheckResult(false, "Siansa Beats doesn't read this ${kind.label} server yet: add it on its page (Setup › Music servers)")
            caps.beatGrids == 0 -> CheckResult(false, "Siansa Beats ${caps.version} answers, but hasn't analysed any songs yet: choose libraries on its page")
            else -> CheckResult(true, "Siansa Beats ${caps.version} is working · %,d songs with beat grids, %,d measured for loudness".format(caps.beatGrids, caps.loudness))
        }

        /** AudioMuse's errors, said for the plugin. */
        fun friendly(message: String?): String {
            val text = message.orEmpty()
            return when {
                app.siansa.net.AudioMuseErrors.PLUGIN_MISSING in text || "HTTP 404" in text -> app.siansa.net.AudioMuseErrors.PLUGIN_MISSING
                // Without a login, AudioMuse sends its login page instead of an answer.
                "isn't JSON" in text || "needs a login" in text -> "AudioMuse wants a login for the plugin: check the AudioMuse login in Settings › Connections"
                text.isBlank() -> "Couldn't reach the Siansa plugin"
                else -> text
            }
        }
    }
}
