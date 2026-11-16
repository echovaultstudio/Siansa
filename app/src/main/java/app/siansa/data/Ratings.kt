package app.siansa.data

import android.content.Context
import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Songs' star ratings (issue #1043), kept on the server (Navidrome and other Subsonic servers, `setRating`) so other
 * apps see them too, with a copy on the phone for showing at once and offline. Works like [FavouriteArtists]: the stars
 * change straight away, and a change the server refuses is undone.
 */
class Ratings(
    context: Context,
    private val source: MusicSource,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(Users.scoped("ratings"), Context.MODE_PRIVATE)
    private val _stars = MutableStateFlow(prefs.all.mapNotNull { (id, v) -> (v as? Int)?.takeIf { it in 1..StarRatings.MOST }?.let { id to it } }.toMap())

    /** Stars by song id; a song that isn't here isn't rated (or wasn't asked about yet). */
    val stars: StateFlow<Map<String, Int>> = _stars.asStateFlow()

    /** Whether the server in use keeps ratings (Emby and Jellyfin have none). */
    val kept: Boolean get() = source.keepsRatings

    fun of(id: String): Int = _stars.value[id] ?: 0

    /** What the server says a song's rating is (from its details). */
    fun learn(id: String, stars: Int?) = set(id, stars ?: 0)

    /** Asks the server for a song's rating; keeps the phone's copy when it can't be reached. */
    suspend fun refresh(id: String) {
        if (!kept) return
        runCatching { source.songDetails(id).userRating }
            .onSuccess { learn(id, it) }
            .onFailure { AppLog.d("Ratings", "Rating of $id not read: ${it.message}") }
    }

    fun rate(id: String, stars: Int, onError: (String) -> Unit = {}) {
        val before = of(id)
        val next = stars.coerceIn(0, StarRatings.MOST)
        if (next == before) return
        set(id, next)
        scope.launch {
            runCatching { source.setRating(id, next) }
                .onSuccess { AppLog.i("Ratings", "Song $id: ${StarRatings.words(next).lowercase()}") }
                .onFailure {
                    AppLog.w("Ratings", "Rating $id with $next stars failed: ${it.message}")
                    set(id, before)
                    onError("Couldn't save the rating on your server: ${it.message}")
                }
        }
    }

    private fun set(id: String, stars: Int) {
        if (of(id) == stars) return
        _stars.value = if (stars > 0) _stars.value + (id to stars) else _stars.value - id
        prefs.edit().apply { if (stars > 0) putInt(id, stars) else remove(id) }.apply()
    }
}
