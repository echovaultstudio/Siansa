package app.siansa.data

import app.siansa.log.AppLog
import app.siansa.net.LastFmApi
import app.siansa.net.MusicSource
import app.siansa.net.SubsonicApi
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
/**
 * Liked songs as a list shows them: the songs liked in the app first ([likedHere], newest first) when the
 * server doesn't list them yet, then the server's, without any unliked in the app since.
 */
internal fun mergeLikedSongs(fromServer: List<Song>, likedHere: List<Song>, overrides: Map<String, Boolean>): List<Song> {
    val listed = fromServer.mapTo(HashSet()) { it.id }
    val added = likedHere.filter { it.id !in listed && overrides[it.id] == true }
    return (added + fromServer).filter { overrides[it.id] ?: true }.distinctBy { it.id }
}

class Favorites(
    private val subsonic: SubsonicApi,
    /** The source in use (issue #113); another shown user's song still goes through [subsonic] with their login. */
    private val source: MusicSource,
    private val lastFm: LastFmApi,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    // Changes made in the app since the last sync, on top of what the synced songs say.
    private val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val changes: StateFlow<Map<String, Boolean>> = overrides.asStateFlow()

    // Bumped once Navidrome has answered a change (issue #250): lists that ask Navidrome reload then, not
    // the moment the heart is tapped, when Navidrome doesn't have the like yet.
    private val _saved = MutableStateFlow(0)
    val saved: StateFlow<Int> = _saved.asStateFlow()

    // Songs liked in the app since it started, newest first, so a list can show them before Navidrome lists them.
    private val likedHere = MutableStateFlow<List<Song>>(emptyList())

    fun isLiked(song: Song): Boolean {
        return overrides.value[song.id] ?: song.starred
    }

    /**
     * [fromServer] (Navidrome's liked songs, newest first) with the songs liked in the app that it doesn't
     * list yet put first, and the ones unliked in the app left out.
     */
    fun mergeLiked(fromServer: List<Song>): List<Song> = mergeLikedSongs(fromServer, likedHere.value, overrides.value)

    fun toggle(song: Song) {
        val liked = !isLiked(song)
        overrides.update { it + (song.id to liked) }
        likedHere.update { list -> (if (liked) listOf(song) else emptyList()) + list.filter { it.id != song.id } }
        scope.launch {
            // Another shown user's song is liked with their login, in their account (issue #141).
            val login = subsonic.loginFor(song.id)
            val who = login?.let { "user ${it.navidromeUser} (their song)" } ?: "user ${settings.current.navidromeUser}"
            val api: MusicSource = if (login != null) subsonic else source
            val ok = runCatching { if (liked) api.star(song.id) else api.unstar(song.id) }
                .onFailure { AppLog.w("Favorites", "Navidrome ${if (liked) "star" else "unstar"} for $who failed: ${it.message}") }
                .isSuccess
            if (!ok) {
                overrides.update { it + (song.id to !liked) }
                likedHere.update { list -> list.filter { it.id != song.id } }
                _saved.update { it + 1 }
                return@launch
            }
            AppLog.i("Favorites", "${if (liked) "Liked" else "Unliked"} ${song.id} ${song.title} for $who")
            // Read it back: Navidrome answered OK, but say so in the log if the song still isn't (un)liked there.
            runCatching { api.song(song.id).starred }
                .onSuccess { now ->
                    if (now != liked) AppLog.w("Favorites", "Navidrome answered OK but ${song.id} is ${if (now) "still" else "not"} liked there for $who")
                    else AppLog.d("Favorites", "Checked: ${song.id} is ${if (now) "liked" else "not liked"} in Navidrome for $who")
                }
            runCatching { library.setStarred(song.id, liked) }
            _saved.update { it + 1 }
            val s = settings.current
            if (s.lastFmReady) {
                runCatching { lastFm.love(LastFmApi.Keys(s.lastFmApiKey, s.lastFmSecret, s.lastFmSession), song, liked) }
                    .onSuccess { AppLog.i("Favorites", "Last.fm: ${if (liked) "loved" else "unloved"} ${song.artist} – ${song.title}") }
                    .onFailure { AppLog.w("Favorites", "Last.fm love failed: ${it.message}") }
            } else {
                AppLog.i(
                    "Favorites",
                    "Not sent to Last.fm: " + when {
                        !s.lastFmEnabled -> "Last.fm scrobbling is switched off"
                        s.lastFmSession.isBlank() -> "not signed in to Last.fm (Settings → Scrobbling → Sign in)"
                        else -> "API key or shared secret missing"
                    },
                )
            }
        }
    }
}
