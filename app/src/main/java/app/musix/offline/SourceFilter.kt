package app.musix.offline

import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.Playlist

/**
 * Home's source filter (issue #56): only what's downloaded, and without the playlists of the Navidrome
 * users you've hidden. Libraries are chosen with the sync setting, so they're left out before this.
 * Home keeps all its rows and buttons (Liked songs, mixes, radios); only the albums, artists and
 * playlists in them are narrowed down.
 */
data class SourceFilter(
    val downloadedOnly: Boolean = false,
    /** Playlist owners to leave out, in lower case. */
    val hiddenOwners: Set<String> = emptySet(),
    val shelf: OfflineShelf = OfflineShelf(),
) {
    fun ownerShown(owner: String?): Boolean = owner.isNullOrBlank() || owner.lowercase() !in hiddenOwners

    fun playlists(list: List<Playlist>): List<Playlist> =
        list.filter { p -> ownerShown(p.owner) && (!downloadedOnly || p.id in shelf.playlists) }

    fun albums(list: List<Album>): List<Album> =
        if (!downloadedOnly) list else list.filter { a -> a.allIds.any { it in shelf.albums } }

    fun artists(list: List<Artist>): List<Artist> =
        if (!downloadedOnly) list else list.filter { it.id in shelf.artists }

    companion object {
        /** Everyone who owns one of [playlists], you first, then by name. */
        fun owners(playlists: List<Playlist>, me: String): List<String> {
            val all = playlists.mapNotNull { it.owner?.takeIf(String::isNotBlank) }.distinctBy { it.lowercase() }
            return all.sortedWith(compareBy<String> { !it.equals(me, ignoreCase = true) }.thenBy { it.lowercase() })
        }
    }
}
