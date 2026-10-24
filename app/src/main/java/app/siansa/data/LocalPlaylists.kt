package app.siansa.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** A playlist kept only on this phone. [picture] is a file in the app's own storage. */
@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val picture: String? = null,
    val createdAt: Long = 0,
    val changedAt: Long = 0,
)

/** A phone-only playlist as the app's screens show playlists; its picture, or else its first song's cover. */
fun LocalPlaylist.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songs.size,
    durationSec = songs.sumOf { it.durationSec },
    coverArt = picture?.let { LocalPlaylists.FILE_COVER + it } ?: songs.firstOrNull()?.cover,
    owner = null,
    changed = java.time.Instant.ofEpochMilli(changedAt).toString(),
    readonly = false,
)

/**
 * Playlists kept only on this phone: they work without Navidrome and never reach it, unless you move
 * one there. Each user of the app has their own.
 */
class LocalPlaylists(private val context: Context) {
    private val json = JsonFile(
        File(context.filesDir, Users.scoped("local_playlists.json")),
        ListSerializer(LocalPlaylist.serializer()),
    ) { emptyList() }

    val state: StateFlow<List<LocalPlaylist>?> = json.state

    suspend fun all(): List<LocalPlaylist> = json.read()

    suspend fun get(id: String): LocalPlaylist? = all().firstOrNull { it.id == id }

    suspend fun create(name: String, songs: List<Song>): LocalPlaylist {
        val now = System.currentTimeMillis()
        val made = LocalPlaylist(PREFIX + UUID.randomUUID().toString(), name, songs, createdAt = now, changedAt = now)
        json.update { listOf(made) + it }
        return made
    }

    suspend fun add(id: String, songs: List<Song>) = change(id) { it.copy(songs = it.songs + songs) }

    suspend fun rename(id: String, name: String) = change(id) { it.copy(name = name) }

    suspend fun remove(id: String, index: Int) = change(id) { p ->
        if (index !in p.songs.indices) p else p.copy(songs = p.songs.filterIndexed { i, _ -> i != index })
    }

    suspend fun delete(id: String) {
        val gone = get(id)
        json.update { list -> list.filterNot { it.id == id } }
        gone?.picture?.let { File(it).delete() }
    }

    /** Keeps a copy of the picture in the app's storage, so it stays when the original is deleted. */
    suspend fun setPicture(id: String, bytes: ByteArray, extension: String) {
        val old = get(id)?.picture
        val dir = File(context.filesDir, "playlist_pictures").apply { mkdirs() }
        // A new name each time, so covers already in memory aren't shown for the new picture.
        val file = File(dir, "$id-${System.currentTimeMillis()}$extension")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        change(id) { it.copy(picture = file.absolutePath) }
        old?.let { File(it).delete() }
    }

    suspend fun removePicture(id: String) {
        val old = get(id)?.picture
        change(id) { it.copy(picture = null) }
        old?.let { File(it).delete() }
    }

    private suspend fun change(id: String, block: (LocalPlaylist) -> LocalPlaylist) {
        var found = false
        json.update { list ->
            list.map { p ->
                if (p.id != id) p else {
                    found = true
                    block(p).copy(changedAt = System.currentTimeMillis())
                }
            }
        }
        if (!found) throw IllegalStateException("That playlist isn't on this phone any more")
    }

    companion object {
        const val PREFIX = "local-"

        /** How a picture file is named where the app expects a cover id. */
        const val FILE_COVER = "file:"

        fun isLocal(id: String): Boolean = id.startsWith(PREFIX)
    }
}
