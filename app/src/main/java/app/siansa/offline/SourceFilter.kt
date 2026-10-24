package app.siansa.offline

import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Genre
import app.siansa.data.Playlist
import app.siansa.data.Song

/**
 * "Only downloaded music" (issues #56 and #141): every list in the app narrowed down to what's on the phone,
 * for a flight or anywhere without internet. [albumIds] are the downloaded songs' albums together with
 * their copies in other libraries, so an album shows whichever copy the songs came from.
 */
class OnlyDownloaded(val shelf: OfflineShelf, val albumIds: Set<String> = shelf.albums) {
    fun songs(list: List<Song>): List<Song> = list.filter { it.id in shelf.songs }

    fun albums(list: List<Album>): List<Album> = list.filter { a -> a.allIds.any { it in albumIds } }

    fun artists(list: List<Artist>): List<Artist> = list.filter { it.id in shelf.artists }

    fun playlists(list: List<Playlist>): List<Playlist> = list.filter { it.id in shelf.playlists }

    companion object {
        /** Genres of [songs] with how many songs each has, most first (names compared without case). */
        fun genres(songs: List<Song>, limit: Int): List<Genre> = songs
            .mapNotNull { s -> s.genre?.trim()?.takeIf { it.isNotEmpty() } }
            .groupBy { it.lowercase() }
            .map { (_, names) -> Genre(names.first(), names.size) }
            .sortedWith(compareByDescending<Genre> { it.songCount }.thenBy { it.name.lowercase() })
            .take(limit)
    }
}
