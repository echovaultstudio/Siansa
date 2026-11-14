package app.siansa.mix

import app.siansa.net.BeatsSound
import app.siansa.net.soundReady
import app.siansa.AppGraph
import app.siansa.log.AppLog
import app.siansa.net.ClapHit

/** Where "sounds like" answers come from (issue #208). */
enum class SonicRoute { BEATS, NAVIDROME_SONIC, AUDIOMUSE, NAVIDROME_SIMILAR }

/**
 * Navidrome first when it has sonic similarity (OpenSubsonic `sonicSimilarity`, given by the AudioMuse-AI
 * Navidrome plugin): it uses the plugin's settings and Navidrome's own login. Otherwise AudioMuse directly when
 * it's set up in Siansa, otherwise Navidrome's plain getSimilarSongs (its agents decide). Emby has no sonic plugin
 * (issue #113), so with Emby it's AudioMuse directly, or Emby's Instant Mix without AudioMuse.
 */
fun sonicRoute(navidromeSonic: Boolean, audioMuseReady: Boolean, beatsSound: Boolean = false): SonicRoute = when {
    // Siansa Beats first once it has the songs' sound (the owner, 11 Nov): the app asks it, and the music server
    // only plays. Navidrome's sonic similarity is the AudioMuse-AI plugin's, and is asked when Siansa Beats can't.
    beatsSound -> SonicRoute.BEATS
    navidromeSonic -> SonicRoute.NAVIDROME_SONIC
    audioMuseReady -> SonicRoute.AUDIOMUSE
    else -> SonicRoute.NAVIDROME_SIMILAR
}

/**
 * Songs that sound like a song, from the best place the setup offers ([sonicRoute]). Siansa keeps its own
 * AudioMuse login for what Navidrome doesn't offer (text search, moods, paths of an exact length, alchemy,
 * Your sound), and falls back to it when Navidrome's answer fails.
 */
class Sonic(private val graph: AppGraph) {
    suspend fun similar(songId: String, count: Int): List<ClapHit> {
        val settings = graph.settings.current
        val audioMuse = settings.soundReady
        val fromBeats = settings.beatsReady && BeatsSound.answers("/api/similar_tracks")
        val route = sonicRoute(graph.source.hasSonicMatch(), audioMuse, fromBeats)
        if (route == SonicRoute.BEATS) {
            // (AudioMuse itself is asked in the same call when Siansa Beats can't answer and it's set up.)
            val hits = runCatching { graph.audioMuse.similarTracks(songId, count) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    AppLog.w("Sonic", "Siansa Beats couldn't say what's like $songId: ${it.message}")
                }
                .getOrDefault(emptyList())
            if (hits.isNotEmpty()) {
                AppLog.d("Sonic", "Like $songId: ${hits.size} from Siansa Beats")
                return hits
            }
            if (!graph.source.hasSonicMatch() && !settings.audioMuseReady) {
                val songs = graph.source.similarSongs(songId, count)
                AppLog.d("Sonic", "Like $songId: ${songs.size} from the server's similar songs")
                return songs.mapIndexed { i, song -> ClapHit(song.id, song.title, song.artist, song.album, 1.0 - i.toDouble() / (songs.size + 1)) }
            }
        }
        if (route == SonicRoute.NAVIDROME_SONIC || (route == SonicRoute.BEATS && graph.source.hasSonicMatch())) {
            val found = runCatching { graph.source.sonicSimilar(songId, count) }
                .onFailure { AppLog.w("Sonic", "Navidrome's sonic similarity failed: ${it.message}") }
                .getOrDefault(emptyList())
            if (found.isNotEmpty()) {
                AppLog.d("Sonic", "Like $songId: ${found.size} from Navidrome's sonic similarity")
                return found.map { (song, similarity) -> ClapHit(song.id, song.title, song.artist, song.album, similarity) }
            }
        }
        if (settings.audioMuseReady || (audioMuse && route != SonicRoute.BEATS)) {
            val hits = graph.audioMuse.similarTracks(songId, count)
            AppLog.d("Sonic", "Like $songId: ${hits.size} from AudioMuse")
            return hits
        }
        val songs = graph.source.similarSongs(songId, count)
        AppLog.d("Sonic", "Like $songId: ${songs.size} from Navidrome's similar songs")
        return songs.mapIndexed { i, song -> ClapHit(song.id, song.title, song.artist, song.album, 1.0 - i.toDouble() / (songs.size + 1)) }
    }
}
