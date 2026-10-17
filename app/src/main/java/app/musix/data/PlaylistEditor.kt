package app.musix.data

import app.musix.log.AppLog
import app.musix.net.NavidromeNativeApi
import app.musix.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Making and changing your playlists. Most live on Navidrome: every change goes straight to the server,
 * and nothing is changed on the phone when that fails, so the two never disagree. Removing one only takes
 * it out of Musix ([removed]); Musix never deletes a playlist on Navidrome. Phone-only playlists
 * ([LocalPlaylists]) change on the phone and work offline.
 */
class PlaylistEditor(
    private val subsonic: SubsonicApi,
    private val native: NavidromeNativeApi,
    private val library: LibraryRepository,
    private val local: LocalPlaylists,
    val removed: RemovedPlaylists,
    private val isOffline: () -> Boolean,
    /** Called when a phone playlist moved to Navidrome as the new playlist, to keep its downloads. */
    private val moved: suspend (localId: String, made: Playlist) -> Unit = { _, _ -> },
) {
    /** The new playlist, or null when Navidrome didn't send it back (it was still made). */
    suspend fun create(name: String, songs: List<Song> = emptyList(), onPhone: Boolean = false): Result<Playlist?> =
        if (onPhone) {
            onThePhone("Creating a phone playlist") {
                local.create(name.trim(), songs).also { AppLog.i("Playlist", "Made phone playlist ${it.id} with ${songs.size} songs") }.toPlaylist()
            }
        } else {
            onTheServer("Creating a playlist") {
                val made = subsonic.createPlaylist(name.trim(), songs.map { it.id })
                AppLog.i("Playlist", "Made playlist ${made?.id} with ${songs.size} songs")
                made
            }
        }

    suspend fun add(playlistId: String, songs: List<Song>): Result<Unit> =
        edit(playlistId, "Adding to a playlist", { local.add(playlistId, songs) }) {
            subsonic.addToPlaylist(playlistId, songs.map { it.id })
        }.also { if (it.isSuccess) AppLog.i("Playlist", "Added ${songs.size} songs to playlist $playlistId") }

    suspend fun rename(playlistId: String, name: String): Result<Unit> =
        edit(playlistId, "Renaming a playlist", { local.rename(playlistId, name.trim()) }) {
            subsonic.renamePlaylist(playlistId, name.trim())
        }.also { if (it.isSuccess) AppLog.i("Playlist", "Renamed playlist $playlistId") }

    /** Removes the song at [index] (from 0, in the playlist's order). */
    suspend fun remove(playlistId: String, index: Int): Result<Unit> =
        edit(playlistId, "Removing from a playlist", { local.remove(playlistId, index) }) {
            subsonic.removeFromPlaylist(playlistId, listOf(index))
        }.also { if (it.isSuccess) AppLog.i("Playlist", "Removed song $index from playlist $playlistId") }

    /**
     * Takes a playlist out of Musix. A Navidrome playlist stays on the server, and [restore] brings it
     * back; a phone playlist is gone for good.
     */
    suspend fun removeFromApp(playlistId: String) {
        if (LocalPlaylists.isLocal(playlistId)) {
            local.delete(playlistId)
            AppLog.i("Playlist", "Deleted phone playlist $playlistId")
        } else {
            removed.remove(playlistId)
            AppLog.i("Playlist", "Removed playlist $playlistId from Musix (kept on Navidrome)")
        }
        library.bump()
    }

    /** Brings a removed Navidrome playlist back into Musix. */
    fun restore(playlistId: String) {
        removed.restore(playlistId)
        AppLog.i("Playlist", "Imported playlist $playlistId again")
        library.bump()
    }

    suspend fun setPicture(playlistId: String, bytes: ByteArray, mime: String): Result<Unit> =
        edit(playlistId, "Setting a playlist picture", { local.setPicture(playlistId, bytes, NavidromeNativeApi.extensionOf(mime)) }) {
            native.uploadPlaylistImage(playlistId, bytes, mime)
        }.also { if (it.isSuccess) AppLog.i("Playlist", "New picture for playlist $playlistId (${bytes.size / 1024} KB)") }

    suspend fun removePicture(playlistId: String): Result<Unit> =
        edit(playlistId, "Removing a playlist picture", { local.removePicture(playlistId) }) {
            native.removePlaylistImage(playlistId)
        }.also { if (it.isSuccess) AppLog.i("Playlist", "Removed the picture of playlist $playlistId") }

    /**
     * Moves a phone-only playlist to Navidrome: made there with the same songs (and picture, when the
     * server takes pictures), then taken off the phone. The new playlist, when Navidrome sends it back.
     */
    suspend fun moveToServer(playlistId: String): Result<Playlist?> {
        val playlist = local.get(playlistId) ?: return Result.failure(IllegalStateException("That playlist isn't on this phone any more"))
        return onTheServer("Moving a playlist to Navidrome") {
            val made = subsonic.createPlaylist(playlist.name, playlist.songs.map { it.id })
            val picture = playlist.picture?.let { java.io.File(it) }?.takeIf { it.exists() }
            if (made != null && picture != null) {
                // The playlist is safe on the server by now; a server without pictures only loses the picture.
                runCatching { native.uploadPlaylistImage(made.id, withContext(Dispatchers.IO) { picture.readBytes() }, mimeOf(picture.name)) }
                    .onFailure { AppLog.w("Playlist", "Picture not moved: ${it.message}") }
            }
            if (made != null) runCatching { moved(playlistId, made) }.onFailure { AppLog.w("Playlist", "Downloads not moved: ${it.message}") }
            local.delete(playlistId)
            AppLog.i("Playlist", "Moved phone playlist $playlistId to Navidrome as ${made?.id}")
            made
        }
    }

    private suspend fun edit(playlistId: String, what: String, onPhone: suspend () -> Unit, onServer: suspend () -> Unit): Result<Unit> =
        if (LocalPlaylists.isLocal(playlistId)) onThePhone(what) { onPhone() } else onTheServer(what) { onServer() }

    private suspend fun <T> onThePhone(what: String, block: suspend () -> T): Result<T> {
        val result = attempt(what, block)
        library.bump()
        return result
    }

    private suspend fun <T> onTheServer(what: String, block: suspend () -> T): Result<T> {
        if (isOffline()) return Result.failure(IllegalStateException(OFFLINE))
        val result = attempt(what, block)
        if (result.isFailure) return result
        // The phone's copy of the list, for Your Library and offline; a failure here only waits for the next sync.
        runCatching { library.updatePlaylists(subsonic.playlists()) }
            .onFailure { AppLog.w("Playlist", "Playlist list not refreshed: ${it.message}") }
        library.bump()
        return result
    }

    private suspend fun <T> attempt(what: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("Playlist", "$what failed: ${e.message}")
        Result.failure(e)
    }

    companion object {
        const val OFFLINE = "You're offline. This playlist is on Navidrome, so try again when you're connected."

        /** Longest name the app sends; Navidrome takes more, but it wouldn't fit anywhere. */
        const val MAX_NAME = 100

        /** Whether you can change this playlist: Navidrome says so, or (older servers) it's yours. */
        fun canEdit(playlist: Playlist, user: String): Boolean =
            playlist.readonly?.not() ?: (playlist.owner.isNullOrBlank() || playlist.owner.equals(user, ignoreCase = true))

        /** The name to save, or null when it can't be used. */
        fun cleanName(input: String): String? = input.trim().take(MAX_NAME).takeIf { it.isNotEmpty() }

        fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
