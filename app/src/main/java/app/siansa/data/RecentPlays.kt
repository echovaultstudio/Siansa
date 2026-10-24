package app.siansa.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Something played as a whole, as Home's shortcuts show it (issue #271): its page, name and cover. */
@Serializable
data class RecentPlay(val route: String, val title: String, val cover: String? = null, val at: Long = 0)

/** [list] with [play] at the top, once, at most [max] long. */
fun notedPlay(list: List<RecentPlay>, play: RecentPlay, max: Int = RecentPlays.MAX): List<RecentPlay> =
    (listOf(play) + list.filter { it.route != play.route }).take(max)

/**
 * The albums, playlists, mixes and other pages last played from (issue #271), newest first, for the shortcuts at
 * the top of Home. Noted when Play, Shuffle or a song on such a page starts playback.
 */
class RecentPlays(context: Context) {
    private val json = JsonFile(File(context.filesDir, Users.scoped("recent_plays.json")), ListSerializer(RecentPlay.serializer())) { emptyList() }

    val state: StateFlow<List<RecentPlay>?> = json.state

    suspend fun all(): List<RecentPlay> = json.read()

    suspend fun note(route: String, title: String, cover: String?) {
        if (route.isBlank()) return
        json.update { notedPlay(it, RecentPlay(route, title, cover, System.currentTimeMillis())) }
    }

    companion object {
        const val MAX = 20
    }
}
