package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.log.AppLog
import app.siansa.net.int
import app.siansa.net.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Song loudness and fade shapes (issue #60), from wherever Settings › Playback says they come from. Today that
 * is the Siansa plugin on the AudioMuse server; with song tags, there are no profiles and everything falls back
 * to ReplayGain and plain crossfades.
 *
 * Only the songs about to play are asked for, a few at a time, and kept in memory (about 100 bytes each).
 * Songs the plugin hasn't analysed are asked for again after [RETRY_MISSING_MS]; after a failed request,
 * nothing is asked for [BACK_OFF_MS], so a missing or broken plugin costs one request now and then.
 *
 * [get] does a GET of a path and query on the AudioMuse server (see [app.siansa.net.AudioMuseApi.plugin]).
 */
class ProfileStore(
    private val get: suspend (String) -> JsonElement,
    private val settings: () -> AppSettings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, TrackProfile>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackProfile>?) = size > MAX_CACHED
    }
    private val missingUntil = HashMap<String, Long>()
    private var backOffUntil = 0L
    private val fetching = Mutex()

    /** Why the last request failed, or null when it worked. */
    @Volatile var problem: String? = null
        private set

    /** Profiles are used: the switch is on the Siansa plugin and AudioMuse is set up. */
    val active: Boolean get() = settings().let { it.loudnessSource == LoudnessSource.PLUGIN && it.audioMuseReady }

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
                else ids.distinct().filter { it !in cache && (missingUntil[it] ?: 0L) <= time }
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
            fail("The Siansa plugin sent an unexpected answer")
            return false
        }
        val schema = answer.int("schema_version")
        if (schema != null && schema != PluginProfiles.SCHEMA_VERSION) {
            fail("The Siansa plugin answers in a newer layout ($schema) than Siansa reads (${PluginProfiles.SCHEMA_VERSION}); using song tags")
            return false
        }
        val profiles = PluginProfiles.parse(answer)
        val time = now()
        synchronized(lock) {
            profiles.forEach { cache[it.trackId] = it }
            val found = profiles.mapTo(HashSet()) { it.trackId }
            batch.filter { it !in found }.forEach { missingUntil[it] = time + RETRY_MISSING_MS }
            if (missingUntil.size > MAX_CACHED) missingUntil.entries.removeAll { it.value <= time }
        }
        problem = null
        val measured = profiles.count { it.refLufs != null }
        AppLog.i("Loudness", "Siansa plugin profiles: ${profiles.size} of ${batch.size} songs ($measured with loudness)")
        return true
    }

    private fun fail(why: String) {
        synchronized(lock) { backOffUntil = now() + BACK_OFF_MS }
        if (problem != why) AppLog.w("Loudness", "${why.trimEnd('.')}. Trying again in ${BACK_OFF_MS / 60_000} minutes; until then song tags are used")
        problem = why
    }

    /** For the "Test" button: whether the plugin answers, in plain words. Clears any back-off. */
    suspend fun check(): CheckResult {
        if (!settings().audioMuseReady) return CheckResult(false, "Set up AudioMuse in Settings › Servers and services first")
        synchronized(lock) { backOffUntil = 0L }
        return try {
            val health = get(HEALTH_PATH) as? JsonObject
            if (health == null || health.str("plugin") !in PLUGIN_NAMES) {
                CheckResult(false, "AudioMuse answered, but not as the Siansa plugin")
            } else if (health.str("status") != "ok") {
                CheckResult(false, "The Siansa plugin is there but not ready (${health.str("status") ?: "no status"})")
            } else {
                problem = null
                val version = health.str("plugin_version")?.let { " $it" } ?: ""
                AppLog.i("Loudness", "Siansa plugin$version is working")
                CheckResult(true, "Siansa plugin$version is working")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val why = friendly(e.message)
            AppLog.w("Loudness", "Siansa plugin check failed: $why")
            CheckResult(false, why)
        }
    }

    data class CheckResult(val ok: Boolean, val message: String)

    companion object {
        const val PLUGIN_NAME = "siansa_analysis"
        /** The plugin by its name now, or as it was called before the rename (issue #360) until that's replaced. */
        val PLUGIN_NAMES = setOf(PLUGIN_NAME, "musix_analysis")
        const val HEALTH_PATH = "/plugins/siansa_analysis/api/health"
        const val MAX_CACHED = 3_000
        const val RETRY_MISSING_MS = 30 * 60_000L
        const val BACK_OFF_MS = 5 * 60_000L

        /** AudioMuse's errors, said for the plugin. */
        fun friendly(message: String?): String {
            val text = message.orEmpty()
            return when {
                app.siansa.net.AudioMuseErrors.PLUGIN_MISSING in text || "HTTP 404" in text -> app.siansa.net.AudioMuseErrors.PLUGIN_MISSING
                // Without a login, AudioMuse sends its login page instead of an answer.
                "isn't JSON" in text || "needs a login" in text -> "AudioMuse wants a login for the plugin: check the AudioMuse login in Settings › Servers and services"
                text.isBlank() -> "Couldn't reach the Siansa plugin"
                else -> text
            }
        }
    }
}
