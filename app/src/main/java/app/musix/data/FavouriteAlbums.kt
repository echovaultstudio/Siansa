package app.musix.data

import android.content.Context
import app.musix.log.AppLog
import app.musix.net.MusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Favourite albums (issue #275), kept in Navidrome as starred albums so other apps see them too, with a copy
 * on the phone for offline and for showing at once. The heart changes straight away; a change Navidrome
 * refuses is undone.
 */
class FavouriteAlbums(
    context: Context,
    private val source: MusicSource,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(Users.scoped("favourite_albums"), Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    /** The Navidrome ids of the favourite albums. */
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** Favourite albums as Navidrome last sent them (also ones not in the synced library). */
    @Volatile var albums: List<Album> = emptyList()
        private set

    fun isFavourite(album: Album): Boolean = isFavouriteAlbum(album, _ids.value)

    fun toggle(album: Album, onError: (String) -> Unit = {}) {
        val on = !isFavourite(album)
        val ids = album.allIds
        set(if (on) _ids.value + ids else _ids.value - ids.toSet())
        scope.launch {
            runCatching { ids.forEach { if (on) source.starAlbum(it) else source.unstarAlbum(it) } }
                .onSuccess { AppLog.i("Favorites", "${if (on) "Favourite" else "No longer favourite"}: album ${album.id} ${album.name}") }
                .onFailure {
                    AppLog.w("Favorites", "Navidrome ${if (on) "star" else "unstar"} for album ${album.id} failed: ${it.message}")
                    set(if (on) _ids.value - ids.toSet() else _ids.value + ids)
                    onError("Couldn't change the favourite in Navidrome: ${it.message}")
                }
        }
    }

    /** Reads the favourite albums from Navidrome; keeps the phone's copy when it can't be reached. */
    suspend fun refresh(): List<Album> {
        val fresh = runCatching { source.starredAlbums() }
            .onFailure { AppLog.w("Favorites", "Favourite albums not read from Navidrome: ${it.message}") }
            .getOrNull() ?: return albums
        albums = fresh
        set(fresh.flatMap { it.allIds }.toSet())
        return fresh
    }

    private fun set(ids: Set<String>) {
        _ids.value = ids
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}

/** Whether [album] (any of its merged copies) is among the favourite [ids]. */
fun isFavouriteAlbum(album: Album, ids: Set<String>): Boolean = album.allIds.any { it in ids }
