package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.SimilarArtist
import app.siansa.data.Song
import app.siansa.data.normalizeKey
import app.siansa.log.AppLog
import app.siansa.net.arr
import app.siansa.net.dbl
import app.siansa.net.str
import app.siansa.playback.ProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

/** An album the Siansa plugin finds sounds alike; [trackId] is one of its songs on the music server. */
data class SimilarAlbumHit(val album: String, val artist: String, val trackId: String, val score: Double)

/** An artist the Siansa plugin finds sounds alike; [trackId] is one of their songs on the music server. */
data class SimilarArtistHit(val artist: String, val trackId: String, val score: Double)

/** The Siansa plugin's similar albums and artists (`audiomuse-plugin/`, issue #223). */
object SimilarPlugin {
    const val ALBUMS_PATH = "/plugins/siansa_analysis/api/similar/albums"
    const val ARTISTS_PATH = "/plugins/siansa_analysis/api/similar/artists"

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Albums like the album of [trackId] (any song of it). */
    fun albumsQuery(trackId: String) = "$ALBUMS_PATH?track_id=${enc(trackId)}"

    /** Artists like [artist], by name. */
    fun artistsQuery(artist: String) = "$ARTISTS_PATH?artist=${enc(artist)}"

    fun parseAlbums(root: JsonObject): List<SimilarAlbumHit> = root.arr("similar").mapNotNull { c ->
        val id = c.str("track_id") ?: return@mapNotNull null
        SimilarAlbumHit(c.str("album") ?: return@mapNotNull null, c.str("artist").orEmpty(), id, c.dbl("score") ?: 0.0)
    }

    fun parseArtists(root: JsonObject): List<SimilarArtistHit> = root.arr("similar").mapNotNull { c ->
        val id = c.str("track_id") ?: return@mapNotNull null
        SimilarArtistHit(c.str("artist") ?: return@mapNotNull null, id, c.dbl("score") ?: 0.0)
    }

    /**
     * "Fans also like": the plugin's artists first (they sound alike), then Navidrome's (from its agents)
     * that aren't already there, without the artist itself.
     */
    fun mergeArtists(self: String, plugin: List<SimilarArtist>, navidrome: List<SimilarArtist>, limit: Int = 20): List<SimilarArtist> {
        val names = HashSet<String>().apply { add(normalizeKey(self)) }
        val ids = HashSet<String>()
        return (plugin + navidrome)
            .filter { names.add(normalizeKey(it.name)) && (it.id == null || ids.add(it.id)) }
            .take(limit)
    }
}

/**
 * "Similar songs", "Similar albums" and the artist page's "Fans also like" (issue #223). Songs come from the best
 * sonic route there is ([Sonic]); albums and artists from the Siansa plugin on the AudioMuse server.
 */
class Similar(private val graph: AppGraph) {
    /** Songs that sound like [song], without it, from the libraries you sync. */
    suspend fun songs(song: Song, count: Int = SONGS): List<Song> {
        val hits = graph.sonic.similar(song.id, count + 1)
        val songs = graph.generator.resolveHits(hits.filter { it.id != song.id })
        val kept = graph.library.keepSyncedSongs(songs, "Similar songs").take(count)
        AppLog.i("Similar", "Like \"${song.title}\": ${kept.size} songs")
        return kept
    }

    /** Albums that sound like the album of [trackId]. Throws with a plain message when the plugin can't answer. */
    suspend fun albums(trackId: String): List<Album> {
        val root = ask(SimilarPlugin.albumsQuery(trackId))
        val hits = SimilarPlugin.parseAlbums(root)
        val songs = graph.generator.resolveSongs(hits.map { it.trackId }).associateBy { it.id }
        val albums = hits.mapNotNull { hit ->
            val albumId = songs[hit.trackId]?.albumId ?: return@mapNotNull null
            graph.library.album(albumId) ?: runCatching { graph.source.album(albumId).album }.getOrNull()
        }.distinctBy { it.id }
        val kept = graph.library.keepSyncedAlbums(albums, "Similar albums")
        AppLog.i("Similar", "Albums like the album of $trackId: ${kept.size} of ${hits.size}")
        return kept
    }

    /**
     * The AudioMuse address the Siansa plugin was last found missing on, and when (issue #788): Home asks for
     * similar artists on every visit, which was three failing calls a visit on a server without the plugin.
     * It's asked again after [PLUGIN_RECHECK_MS], or at once when the address changes.
     */
    @Volatile private var pluginMissingOn: Pair<String, Long>? = null

    /** Whether the plugin is known to be missing on the server set now (so a call to it isn't worth making). */
    fun pluginKnownMissing(now: Long = System.currentTimeMillis()): Boolean {
        val (server, at) = pluginMissingOn ?: return false
        return server == graph.settings.current.audioMuseUrl && now - at < PLUGIN_RECHECK_MS
    }

    /** Artists that sound like [name], as "Fans also like" shows them; empty when the plugin isn't there. */
    suspend fun artists(name: String): List<SimilarArtist> {
        if (!graph.settings.current.audioMuseReady || pluginKnownMissing()) return emptyList()
        val hits = try {
            SimilarPlugin.parseArtists(ask(SimilarPlugin.artistsQuery(name)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains(app.siansa.net.AudioMuseErrors.PLUGIN_MISSING) == true) {
                // Said once, then left alone for a while, instead of once per artist per visit.
                AppLog.w("Similar", app.siansa.net.AudioMuseErrors.PLUGIN_MISSING + " Similar artists and albums need it; asking again in an hour.")
                pluginMissingOn = graph.settings.current.audioMuseUrl to System.currentTimeMillis()
            } else {
                AppLog.d("Similar", "No plugin artists like $name: ${e.message}")
            }
            return emptyList()
        }
        val byName = hits.associateWith { graph.library.artistByName(it.artist) }
        // Artists not found by name (a different spelling): their song says who they are.
        val missing = hits.filter { byName[it] == null }
        val songs = if (missing.isEmpty()) emptyMap() else graph.generator.resolveSongs(missing.map { it.trackId }).associateBy { it.id }
        val out = hits.mapNotNull { hit ->
            val local = byName[hit] ?: songs[hit.trackId]?.artistId?.let { graph.library.artist(it) }
            val id = local?.id ?: songs[hit.trackId]?.artistId ?: return@mapNotNull null
            SimilarArtist(id, local?.name ?: hit.artist, local?.coverArt, null)
        }
        AppLog.i("Similar", "Artists like $name: ${out.size} of ${hits.size} from the Siansa plugin")
        return out
    }

    private suspend fun ask(query: String): JsonObject {
        if (!graph.settings.current.audioMuseReady) throw IllegalStateException("Set up AudioMuse in Settings › Servers and services first")
        val answer = try {
            graph.audioMuse.plugin(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Similar", "Siansa plugin: ${e.message}")
            throw IllegalStateException(ProfileStore.friendly(e.message), e)
        }
        return answer as? JsonObject ?: throw IllegalStateException("The Siansa plugin sent an unexpected answer")
    }

    companion object {
        const val SONGS = 50
        /** How long a missing plugin is taken as still missing before asking again. */
        const val PLUGIN_RECHECK_MS = 60L * 60 * 1000
    }
}
