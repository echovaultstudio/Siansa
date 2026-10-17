package app.musix.offline

import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.HomeSection
import app.musix.data.Mix
import app.musix.data.Playlist

/**
 * Home's source filter (issue #56): only what's downloaded, and without the playlists of the Navidrome
 * users you've hidden. Libraries are chosen with the sync setting, so they're left out before this.
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

    /** A mix only plays offline when all of it is here. */
    fun mixes(list: List<Mix>): List<Mix> =
        if (!downloadedOnly) list else list.filter { m -> shelf.hasAll(m.songs.map { it.id }) }

    val likedShown: Boolean get() = !downloadedOnly || shelf.liked

    /** Rows that need the server (making mixes, radios, genres) are hidden while only downloads show. */
    fun shows(section: HomeSection): Boolean = !downloadedOnly || section !in NEEDS_SERVER

    companion object {
        val NEEDS_SERVER = setOf(
            HomeSection.TODAY, HomeSection.MOOD, HomeSection.MADE_FOR_YOU, HomeSection.RADIOS,
            HomeSection.ARTIST_RADIOS, HomeSection.SUGGESTED_ARTISTS, HomeSection.GENRES,
        )

        /** Everyone who owns one of [playlists], you first, then by name. */
        fun owners(playlists: List<Playlist>, me: String): List<String> {
            val all = playlists.mapNotNull { it.owner?.takeIf(String::isNotBlank) }.distinctBy { it.lowercase() }
            return all.sortedWith(compareBy<String> { !it.equals(me, ignoreCase = true) }.thenBy { it.lowercase() })
        }
    }
}
