package app.siansa.data

import android.content.Context
import app.siansa.log.AppLog
import app.siansa.net.SubsonicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Favourite artists (issue #397), kept in Navidrome as starred artists so other apps see them too, with a copy
 * on the phone for offline and for showing at once. Works like [FavouriteAlbums]: the heart changes straight
 * away, and a change Navidrome refuses is undone.
 */
class FavouriteArtists(
    context: Context,
    private val subsonic: SubsonicApi,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(Users.scoped("favourite_artists"), Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    /** The Navidrome ids of the favourite artists. */
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** Favourite artists as Navidrome last sent them (also ones not in the synced library). */
    @Volatile var artists: List<Artist> = emptyList()
        private set

    fun isFavourite(artist: Artist): Boolean = artist.id in _ids.value

    fun toggle(artist: Artist, onError: (String) -> Unit = {}) {
        val on = !isFavourite(artist)
        set(if (on) _ids.value + artist.id else _ids.value - artist.id)
        scope.launch {
            runCatching { if (on) subsonic.starArtist(artist.id) else subsonic.unstarArtist(artist.id) }
                .onSuccess { AppLog.i("Favorites", "${if (on) "Favourite" else "No longer favourite"}: artist ${artist.id} ${artist.name}") }
                .onFailure {
                    AppLog.w("Favorites", "Navidrome ${if (on) "star" else "unstar"} for artist ${artist.id} failed: ${it.message}")
                    set(if (on) _ids.value - artist.id else _ids.value + artist.id)
                    onError("Couldn't change the favourite in Navidrome: ${it.message}")
                }
        }
    }

    /** Reads the favourite artists from Navidrome; keeps the phone's copy when it can't be reached. */
    suspend fun refresh(): List<Artist> {
        val fresh = runCatching { subsonic.starredArtists() }
            .onFailure { AppLog.w("Favorites", "Favourite artists not read from Navidrome: ${it.message}") }
            .getOrNull() ?: return artists
        artists = fresh
        set(fresh.map { it.id }.toSet())
        AppLog.d("Favorites", "${fresh.size} favourite artists from Navidrome")
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

/**
 * The favourite artists to list (issue #397): those still in [ids] (a heart just taken off goes at once), the
 * synced library's copy when there is one ([local]), once each, by name.
 */
fun favouriteArtistList(fresh: List<Artist>, ids: Set<String>, local: Map<String, Artist>): List<Artist> =
    fresh.filter { it.id in ids }
        .map { local[it.id] ?: it }
        .distinctBy { it.id }
        .sortedBy { normalizeKey(it.name) }
