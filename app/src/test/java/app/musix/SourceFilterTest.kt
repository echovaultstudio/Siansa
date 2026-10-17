package app.musix

import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.Playlist
import app.musix.offline.OfflineShelf
import app.musix.offline.SourceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Home's source filter: downloads only, and playlists from the users you choose. */
class SourceFilterTest {
    private val shelf = OfflineShelf(
        songs = setOf("s1", "s2"),
        albums = setOf("al1"),
        artists = setOf("ar1"),
        playlists = setOf("p1"),
        liked = false,
    )
    private val playlists = listOf(
        Playlist(id = "p1", name = "Road trip", owner = "Ratty"),
        Playlist(id = "p2", name = "Gym", owner = "kingpin"),
        Playlist(id = "p3", name = "Old", owner = null),
    )

    @Test fun everythingShowsEverything() {
        val f = SourceFilter(shelf = shelf)
        assertEquals(playlists, f.playlists(playlists))
    }

    @Test fun downloadedOnlyKeepsWhatIsOnThePhone() {
        val f = SourceFilter(downloadedOnly = true, shelf = shelf)
        assertEquals(listOf("p1"), f.playlists(playlists).map { it.id })
        val albums = listOf(Album(id = "al1", name = "A"), Album(id = "al2", name = "B"), Album(id = "al3", name = "C", ids = listOf("al3", "al1")))
        assertEquals(listOf("al1", "al3"), f.albums(albums).map { it.id })
        assertEquals(listOf("ar1"), f.artists(listOf(Artist("ar1", "X"), Artist("ar2", "Y"))).map { it.id })
    }

    @Test fun hiddenOwnersArentShown() {
        val f = SourceFilter(hiddenOwners = setOf("kingpin"))
        assertEquals(listOf("p1", "p3"), f.playlists(playlists).map { it.id })
        assertFalse(f.ownerShown("KingPin"))
    }

    @Test fun ownersListYouFirst() {
        val list = playlists + Playlist(id = "p4", name = "More", owner = "alice") + Playlist(id = "p5", name = "Dup", owner = "ratty")
        assertEquals(listOf("Ratty", "alice", "kingpin"), SourceFilter.owners(list, "ratty"))
    }
}
