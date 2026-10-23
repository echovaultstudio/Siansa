package app.siansa.data

import android.content.Context

/**
 * Navidrome playlists you removed from siansa. Removing never deletes a playlist on Navidrome: siansa
 * just stops showing it until you import it again. Every other Navidrome playlist shows up by itself.
 */
class RemovedPlaylists(context: Context) {
    private val prefs = context.getSharedPreferences(Users.scoped("removed_playlists"), Context.MODE_PRIVATE)

    fun ids(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun remove(id: String) = save(ids() + id)

    fun restore(id: String) = save(ids() - id)

    /** [playlists] without the ones you removed. */
    fun visible(playlists: List<Playlist>): List<Playlist> = hide(playlists, ids())

    private fun save(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    companion object {
        private const val KEY = "ids"

        fun hide(playlists: List<Playlist>, removed: Set<String>): List<Playlist> =
            if (removed.isEmpty()) playlists else playlists.filterNot { it.id in removed }
    }
}
