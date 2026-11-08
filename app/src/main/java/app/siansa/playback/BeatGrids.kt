package app.siansa.playback

import app.siansa.net.dbl
import app.siansa.net.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Where the beat grids of beat-matched blends come from (Siansa Dev, an experiment): the Siansa plugin measures them
 * itself; Siansa Beats reads them with a model (Beat This!), with bars and phrases checked against the sound. Loudness
 * and fade shapes always come from the plugin.
 */
enum class BeatGridSource(val label: String) {
    /** The plugin's beat grids, as Siansa has always used. */
    PLUGIN("Siansa plugin"),
    /** Siansa Beats' grid where it has the song, the plugin's for the rest. */
    BEATS("Siansa Beats first"),
    /** Siansa Beats' grids only: the plugin's beat readings are ignored, so a song Siansa Beats hasn't done has none. */
    BEATS_ONLY("Siansa Beats only");

    companion object {
        fun parse(value: String?): BeatGridSource = entries.firstOrNull { it.name == value } ?: PLUGIN
    }
}

/** One song's grid from Siansa Beats: the start, the end, and its last beat (ms from the start). */
data class BeatsGrid(val intro: BeatEnd?, val outro: BeatEnd?, val beatsUntilMs: Long?)

/** Reading Siansa Beats' `GET /api/grids?ids=…`, and laying its grids over the plugin's profiles. */
object BeatsGrids {
    const val PATH = "/api/grids"
    const val HEALTH_PATH = "/api/health"
    /** Siansa Beats reads up to 2000 ids a request; kept to the plugin's address budget all the same. */
    const val MAX_IDS = 500

    /** `{"grids": [{"track_id", "intro_bpm", …}]}` → the grids by song id; the same field names as the plugin's. */
    fun parse(answer: JsonObject): Map<String, BeatsGrid> {
        val grids = answer["grids"] as? JsonArray ?: return emptyMap()
        return grids.mapNotNull { element ->
            val g = element as? JsonObject ?: return@mapNotNull null
            val id = g.str("track_id") ?: return@mapNotNull null
            val until = g.dbl("beats_until_ms")
            id to BeatsGrid(
                intro = PluginProfiles.beatEnd(g, "intro"),
                outro = PluginProfiles.beatEnd(g, "outro"),
                beatsUntilMs = until?.takeIf { it.isFinite() && it > 0 }?.toLong(),
            )
        }.toMap()
    }

    /**
     * [profile] (the plugin's, or null when it hasn't measured the song) with the beat grid [source] says: the
     * plugin's own; Siansa Beats' [grid] where there is one; or Siansa Beats' only, none without it. Null when
     * there's nothing for the song at all.
     */
    fun merge(id: String, profile: TrackProfile?, grid: BeatsGrid?, source: BeatGridSource): TrackProfile? = when (source) {
        BeatGridSource.PLUGIN -> profile
        BeatGridSource.BEATS -> if (grid == null) profile else (profile ?: empty(id)).copy(intro = grid.intro, outro = grid.outro, beatsUntilMs = grid.beatsUntilMs)
        BeatGridSource.BEATS_ONLY -> if (grid == null && profile == null) null
            else (profile ?: empty(id)).copy(intro = grid?.intro, outro = grid?.outro, beatsUntilMs = grid?.beatsUntilMs)
    }

    private fun empty(id: String) = TrackProfile(id, refLufs = null, startRamp = emptyList(), endRamp = emptyList())
}
