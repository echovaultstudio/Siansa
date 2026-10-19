package app.musix.mix

import app.musix.AppGraph
import app.musix.data.AppJson
import app.musix.log.AppLog
import app.musix.net.TrackFeatures
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

/**
 * Your sonic fingerprint (issue #216): what the music you play most sounds like, from AudioMuse's analysis of
 * the songs its sonic fingerprint picks (the songs closest to the sound of your most-played ones).
 */
@Serializable
data class FingerprintProfile(
    val date: String,
    /** AudioMuse's six moods, 0..1, in a fixed order. */
    val moods: List<Pair<String, Double>>,
    /** The strongest genres, strongest first. */
    val genres: List<String>,
    /** The typical tempo, in BPM, or null when unknown. */
    val bpm: Int?,
    /** "major" or "minor" when one clearly leads, else null. */
    val scale: String?,
    /** The songs, to play. */
    val songIds: List<String>,
) {
    companion object {
        val MOODS = listOf("danceable", "happy", "party", "relaxed", "sad", "aggressive")

        /** The profile of [tracks]: average moods and genres, the median tempo and the leading scale. */
        fun of(date: String, tracks: List<TrackFeatures>, songIds: List<String>): FingerprintProfile {
            fun mean(values: List<Double>) = if (values.isEmpty()) 0.0 else values.sum() / values.size
            val moods = MOODS.map { m -> m to mean(tracks.mapNotNull { it.moods[m] }).coerceIn(0.0, 1.0) }
            val genres = tracks.flatMap { it.genres.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, v) -> v.sum() / tracks.size.coerceAtLeast(1) }
                .entries.sortedByDescending { it.value }
                .take(5).map { it.key }
            val tempos = tracks.mapNotNull { it.tempo }.filter { it > 0 }.sorted()
            val bpm = if (tempos.isEmpty()) null else tempos[tempos.size / 2].let { kotlin.math.round(it).toInt() }
            val scales = tracks.mapNotNull { it.scale?.lowercase() }.groupingBy { it }.eachCount()
            val total = scales.values.sum()
            val scale = scales.maxByOrNull { it.value }?.takeIf { total > 0 && it.value * 3 >= total * 2 }?.key
            return FingerprintProfile(date, moods, genres, bpm, scale, songIds)
        }
    }
}

/** Today's fingerprint, made once a day and kept on the phone. */
class SonicFingerprint(private val graph: AppGraph) {
    private val file get() = File(graph.app.filesDir, "fingerprint.json")

    fun saved(): FingerprintProfile? = runCatching { AppJson.decodeFromString(FingerprintProfile.serializer(), file.readText()) }.getOrNull()

    suspend fun today(force: Boolean = false): FingerprintProfile {
        val date = LocalDate.now().toString()
        if (!force) saved()?.takeIf { it.date == date }?.let { return it }
        val s = graph.settings.current
        if (!s.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        val hits = graph.audioMuse.sonicFingerprint(FINGERPRINT_SONGS, s.navidromeUser, s.navidromePassword)
        if (hits.isEmpty()) throw MixException("AudioMuse found no fingerprint yet. Play some more and try again later.")
        val ids = hits.map { it.id }
        val features = runCatching { graph.audioMuse.features(ids) }
            .onFailure { AppLog.w("Fingerprint", "No analysis for the fingerprint songs: ${it.message}") }
            .getOrDefault(emptyList())
        val profile = FingerprintProfile.of(date, features, ids)
        AppLog.i(
            "Fingerprint",
            "${ids.size} songs, ${features.size} analysed · moods ${profile.moods.joinToString { "${it.first} ${"%.2f".format(it.second)}" }} · " +
                "genres ${profile.genres.joinToString()} · ${profile.bpm ?: "?"} BPM · ${profile.scale ?: "mixed keys"}",
        )
        runCatching { file.writeText(AppJson.encodeToString(FingerprintProfile.serializer(), profile)) }
        return profile
    }

    private companion object {
        const val FINGERPRINT_SONGS = 50
    }
}
