package app.musix.data

import app.musix.log.AppLog
import app.musix.net.LastFmApi
import app.musix.net.SubsonicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Liked songs. Hearts are stored in Navidrome (star) and, when Last.fm is connected, sent there as
 * loves. The screen updates at once; a failed change is undone.
 */
class Favorites(
    private val subsonic: SubsonicApi,
    private val lastFm: LastFmApi,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    // Changes made in the app since the last sync, on top of what the synced songs say.
    private val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val changes: StateFlow<Map<String, Boolean>> = overrides.asStateFlow()

    fun isLiked(song: Song): Boolean {
        overrides.value[song.id]?.let { return it }
        return library.current?.song(song.id)?.starred ?: song.starred
    }

    fun toggle(song: Song) {
        val liked = !isLiked(song)
        overrides.update { it + (song.id to liked) }
        scope.launch {
            val ok = runCatching { if (liked) subsonic.star(song.id) else subsonic.unstar(song.id) }
                .onFailure { AppLog.w("Favorites", "Navidrome ${if (liked) "star" else "unstar"} failed: ${it.message}") }
                .isSuccess
            if (!ok) {
                overrides.update { it + (song.id to !liked) }
                return@launch
            }
            AppLog.i("Favorites", "${if (liked) "Liked" else "Unliked"} ${song.id} ${song.title}")
            val s = settings.current
            if (s.lastFmReady) {
                runCatching { lastFm.love(LastFmApi.Keys(s.lastFmApiKey, s.lastFmSecret, s.lastFmSession), song, liked) }
                    .onFailure { AppLog.w("Favorites", "Last.fm love failed: ${it.message}") }
            }
        }
    }
}
