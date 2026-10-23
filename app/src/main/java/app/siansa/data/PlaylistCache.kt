package app.siansa.data

import android.content.Context
import app.siansa.log.AppLog
import app.siansa.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** A Navidrome playlist's songs as last read, with when Navidrome said it last changed (issue #251). */
@Serializable
data class CachedPlaylist(
    val id: String,
    val name: String,
    val changed: String? = null,
    val owner: String? = null,
    val coverArt: String? = null,
    val comment: String? = null,
    val readonly: Boolean? = null,
    val songs: List<Song> = emptyList(),
    /** The songs' dates, so a playlist from the phone still sorts by date added or last played (issue #257). */
    val dates: Map<String, SongDates> = emptyMap(),
) {
    fun toDetail(): PlaylistDetail {
        dates.forEach { (id, d) -> if (SongDatesCache[id] == null) SongDatesCache.remember(id, d) }
        return detail()
    }

    private fun detail() = PlaylistDetail(Playlist(id, name, songs.size, songs.sumOf { it.durationSec }, coverArt, owner, comment, changed, readonly), songs)

    companion object {
        fun of(d: PlaylistDetail) = CachedPlaylist(
            d.playlist.id, d.playlist.name, d.playlist.changed, d.playlist.owner, d.playlist.coverArt, d.playlist.comment, d.playlist.readonly, d.songs,
            d.songs.mapNotNull { s -> SongDatesCache[s.id]?.let { s.id to it } }.toMap(),
        )
    }
}

/**
 * Still right: Navidrome's list of playlists (synced with the library) says the playlist hasn't changed since
 * its songs were kept, and has as many songs. Without a date to compare, it's asked for again.
 */
fun isFresh(kept: CachedPlaylist, listed: Playlist?): Boolean =
    listed != null && kept.changed != null && kept.changed == listed.changed && kept.songs.size == listed.songCount

/**
 * Every Navidrome playlist's songs kept on the phone (issue #251), so opening or playing one needs no request
 * while Navidrome's playlist list says it hasn't changed. Changes made in Siansa still go to Navidrome first
 * ([PlaylistEditor]); then the kept copy is read again. Phone playlists ([LocalPlaylists]) don't need this.
 */
class PlaylistCache(context: Context, private val subsonic: SubsonicApi, private val library: LibraryRepository) {
    private val json = JsonFile(
        File(context.filesDir, Users.scoped("playlist-songs.json")),
        MapSerializer(String.serializer(), CachedPlaylist.serializer()),
    ) { emptyMap() }

    /** The kept copy, whatever its age (to show at once, or when Navidrome can't be reached). */
    suspend fun kept(id: String): PlaylistDetail? = json.read()[id]?.toDetail()

    /**
     * The playlist's songs: the kept copy when Navidrome's playlist list says it's unchanged, otherwise asked
     * for and kept. When Navidrome can't be reached, the kept copy, however old.
     */
    suspend fun get(id: String, force: Boolean = false): PlaylistDetail {
        val kept = json.read()[id]
        val listed = runCatching { library.playlists().firstOrNull { it.id == id } }.getOrNull()
        if (!force && kept != null && isFresh(kept, listed)) {
            AppLog.d("Playlist", "Playlist $id from the phone: ${kept.songs.size} songs, unchanged since ${kept.changed}")
            return kept.toDetail()
        }
        return try {
            refresh(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (kept == null) throw e
            AppLog.w("Playlist", "Playlist $id: Navidrome not reached (${e.message}); using the ${kept.songs.size} songs kept on the phone")
            kept.toDetail()
        }
    }

    /** Asks Navidrome for the playlist and keeps it; after a change made in Siansa, or when it changed on Navidrome. */
    suspend fun refresh(id: String): PlaylistDetail {
        val fresh = subsonic.playlist(id)
        json.update { it + (id to CachedPlaylist.of(fresh)) }
        AppLog.i("Playlist", "Playlist $id read from Navidrome and kept on the phone: ${fresh.songs.size} songs")
        return fresh
    }

    /** Forgets a playlist taken out of Siansa. */
    suspend fun forget(id: String) {
        json.update { it - id }
    }
}
