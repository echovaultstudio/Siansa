package app.siansa.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the queue was played from, for now playing's top (the owner, 18 Nov: playing a mix, of Siansa Beats or of the
 * phone, showed "Playing from album" and the song's album): a mix, a playlist, an artist, a genre, with its name and
 * the page it opens. Noted when something is played from its page or from Home, with the player's list it started
 * ([PlayerConnection.queueGeneration]); a new list played from anywhere else ends it, and the song's album shows again.
 */
class PlayingFrom(private val generation: () -> Long) {
    data class From(val kind: Kind, val title: String, val route: String, val generation: Long)

    /** What it was played from, as now playing says it ("PLAYING FROM MIX"). */
    enum class Kind(val label: String) {
        MIX("PLAYING FROM MIX"),
        PLAYLIST("PLAYING FROM PLAYLIST"),
        RADIO("PLAYING FROM RADIO"),
        ARTIST("PLAYING FROM ARTIST"),
        GENRE("PLAYING FROM GENRE"),
        OTHER("NOW PLAYING FROM"),
    }

    private val from = MutableStateFlow<From?>(null)
    val state: StateFlow<From?> = from.asStateFlow()

    /** The list just played came from [route] ("mix/…", "playlist/…") named [title]; an album's page needs nothing. */
    fun note(route: String, title: String) {
        val kind = kindOf(route) ?: return run { from.value = null }
        if (title.isBlank()) return
        from.value = From(kind, title, route, generation())
    }

    /** What's playing came from, while it's still the list that was played from there. */
    fun live(hasMedia: Boolean, value: From? = from.value): From? =
        value?.takeIf { hasMedia && it.generation == generation() }

    companion object {
        /** What a page's [route] is, for the top of now playing; null for an album, whose name shows as before. */
        fun kindOf(route: String): Kind? = when (route.substringBefore('/').substringBefore('?')) {
            "album" -> null
            "mix", "vibe", "selector", "carousel" -> Kind.MIX
            "playlist", "liked" -> Kind.PLAYLIST
            "radio", "mood", "blend" -> Kind.RADIO
            "artist" -> Kind.ARTIST
            "genre" -> Kind.GENRE
            else -> Kind.OTHER
        }
    }
}
