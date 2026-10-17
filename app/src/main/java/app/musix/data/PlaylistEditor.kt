package app.musix.data

import app.musix.log.AppLog
import app.musix.net.NavidromeNativeApi
import app.musix.net.SubsonicApi
import kotlinx.coroutines.CancellationException

/**
 * Making and changing your playlists. They live on Navidrome: every change goes straight to the server,
 * and nothing is changed on the phone when that fails, so the two never disagree. Removing one only takes
 * it out of musix ([removed]); musix never deletes a playlist on Navidrome.
 */
class PlaylistEditor(
    private val subsonic: SubsonicApi,
    private val native: NavidromeNativeApi,
    private val library: LibraryRepository,
    val removed: RemovedPlaylists,
    private val isOffline: () -> Boolean,
) {
    /** The new playlist, or null when Navidrome didn't send it back (it was still made). */
    suspend fun create(name: String, songIds: List<String> = emptyList()): Result<Playlist?> =
        change("Creating a playlist") {
            val made = subsonic.createPlaylist(name.trim(), songIds)
            AppLog.i("Playlist", "Made playlist ${made?.id} with ${songIds.size} songs")
            made
        }

    suspend fun add(playlistId: String, songIds: List<String>): Result<Unit> =
        change("Adding to a playlist") {
            subsonic.addToPlaylist(playlistId, songIds)
            AppLog.i("Playlist", "Added ${songIds.size} songs to playlist $playlistId")
        }

    suspend fun rename(playlistId: String, name: String): Result<Unit> =
        change("Renaming a playlist") {
            subsonic.renamePlaylist(playlistId, name.trim())
            AppLog.i("Playlist", "Renamed playlist $playlistId")
        }

    /** Removes the song at [index] (from 0, in the playlist's order). */
    suspend fun remove(playlistId: String, index: Int): Result<Unit> =
        change("Removing from a playlist") {
            subsonic.removeFromPlaylist(playlistId, listOf(index))
            AppLog.i("Playlist", "Removed song $index from playlist $playlistId")
        }

    /** Takes a playlist out of musix. It stays on Navidrome, and [restore] brings it back. */
    fun removeFromApp(playlistId: String) {
        removed.remove(playlistId)
        AppLog.i("Playlist", "Removed playlist $playlistId from musix (kept on Navidrome)")
        library.bump()
    }

    /** Brings a removed Navidrome playlist back into musix. */
    fun restore(playlistId: String) {
        removed.restore(playlistId)
        AppLog.i("Playlist", "Imported playlist $playlistId again")
        library.bump()
    }

    suspend fun setPicture(playlistId: String, bytes: ByteArray, mime: String): Result<Unit> =
        change("Setting a playlist picture") {
            native.uploadPlaylistImage(playlistId, bytes, mime)
            AppLog.i("Playlist", "New picture for playlist $playlistId (${bytes.size / 1024} KB)")
        }

    suspend fun removePicture(playlistId: String): Result<Unit> =
        change("Removing a playlist picture") {
            native.removePlaylistImage(playlistId)
            AppLog.i("Playlist", "Removed the picture of playlist $playlistId")
        }

    private suspend fun <T> change(what: String, block: suspend () -> T): Result<T> {
        if (isOffline()) return Result.failure(IllegalStateException(OFFLINE))
        val result = try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Playlist", "$what failed: ${e.message}")
            return Result.failure(e)
        }
        // The phone's copy of the list, for Your Library and offline; a failure here only waits for the next sync.
        runCatching { library.updatePlaylists(subsonic.playlists()) }
            .onFailure { AppLog.w("Playlist", "Playlist list not refreshed: ${it.message}") }
        library.bump()
        return result
    }

    companion object {
        const val OFFLINE = "You're offline. Playlists change on Navidrome, so try again when you're connected."

        /** Longest name the app sends; Navidrome takes more, but it wouldn't fit anywhere. */
        const val MAX_NAME = 100

        /** Whether you can change this playlist: Navidrome says so, or (older servers) it's yours. */
        fun canEdit(playlist: Playlist, user: String): Boolean =
            playlist.readonly?.not() ?: (playlist.owner.isNullOrBlank() || playlist.owner.equals(user, ignoreCase = true))

        /** The name to save, or null when it can't be used. */
        fun cleanName(input: String): String? = input.trim().take(MAX_NAME).takeIf { it.isNotEmpty() }
    }
}
