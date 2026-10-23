package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.log.AppLog
import app.siansa.net.ClapHit

/** Where "sounds like" answers come from (issue #208). */
enum class SonicRoute { NAVIDROME_SONIC, AUDIOMUSE, NAVIDROME_SIMILAR }

/**
 * Navidrome first when it has sonic similarity (OpenSubsonic `sonicSimilarity`, given by the AudioMuse-AI
 * Navidrome plugin): it uses the plugin's settings and Navidrome's own login. Otherwise AudioMuse directly when
 * it's set up in Siansa, otherwise Navidrome's plain getSimilarSongs (its agents decide).
 */
fun sonicRoute(navidromeSonic: Boolean, audioMuseReady: Boolean): SonicRoute = when {
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
        val audioMuse = graph.settings.current.audioMuseReady
        val route = sonicRoute(graph.subsonic.supports("sonicSimilarity"), audioMuse)
        if (route == SonicRoute.NAVIDROME_SONIC) {
            val found = runCatching { graph.subsonic.sonicSimilar(songId, count) }
                .onFailure { AppLog.w("Sonic", "Navidrome's sonic similarity failed: ${it.message}") }
                .getOrDefault(emptyList())
            if (found.isNotEmpty()) {
                AppLog.d("Sonic", "Like $songId: ${found.size} from Navidrome's sonic similarity")
                return found.map { (song, similarity) -> ClapHit(song.id, song.title, song.artist, song.album, similarity) }
            }
        }
        if (audioMuse) {
            val hits = graph.audioMuse.similarTracks(songId, count)
            AppLog.d("Sonic", "Like $songId: ${hits.size} from AudioMuse")
            return hits
        }
        val songs = graph.subsonic.similarSongs(songId, count)
        AppLog.d("Sonic", "Like $songId: ${songs.size} from Navidrome's similar songs")
        return songs.mapIndexed { i, song -> ClapHit(song.id, song.title, song.artist, song.album, 1.0 - i.toDouble() / (songs.size + 1)) }
    }
}
