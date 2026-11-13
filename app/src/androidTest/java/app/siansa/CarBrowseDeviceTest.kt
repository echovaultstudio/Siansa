package app.siansa

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.car.CarLibrary
import app.siansa.car.CarLibraryOrStandIn
import app.siansa.car.CarTree
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Mix
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.RecentPlay
import app.siansa.data.SearchResults
import app.siansa.data.Song
import app.siansa.mix.MoodRadio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Browsing a large library as Android Auto does (issues #1040, #1041), through the real playback service
 * ([CarConnection]). A long list reaches the car cut short, so its later letters were grey in "Jump to letter";
 * albums and artists are handed over by letter instead, and every one of them must reach the car.
 */
@RunWith(AndroidJUnit4::class)
class CarBrowseDeviceTest {
    private val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    // Ids as long as Navidrome's (22 characters), and covers named as it names them.
    private fun id(n: Int) = "%022d".format(n)
    private val artists = letters.flatMapIndexed { l, c -> (0 until 120).map { Artist(id(l * 1000 + it), "$c artist number $it", coverArt = "ar-${id(l * 1000 + it)}_0") } }
    private val albums = letters.flatMapIndexed { l, c -> (0 until 60).map { Album(id(l * 1000 + it), "$c album number $it", artist = "$c artist number $it", coverArt = "al-${id(l * 1000 + it)}_665f1a2b") } }
    private val playlists = (0 until 3000).map { Playlist(id(it), "Playlist number $it", songCount = 20, coverArt = "pl-${id(it)}_665f1a2b") }

    private inner class Large : CarLibrary {
        override suspend fun mixes(): List<Mix> = emptyList()
        override suspend fun mix(id: String): Mix? = null
        override fun moods(): List<MoodRadio> = emptyList()
        override suspend fun moodSongs(id: String): List<Song> = emptyList()
        override suspend fun recent(): List<RecentPlay> = emptyList()
        override suspend fun playlists(): List<Playlist> = playlists
        override suspend fun playlist(id: String): PlaylistDetail? = null
        override suspend fun albums(): List<Album> = albums
        override suspend fun album(id: String): Album? = albums.firstOrNull { it.id == id }
        override suspend fun albumSongs(id: String): List<Song> = emptyList()
        override suspend fun artists(): List<Artist> = artists
        override suspend fun artist(id: String): Artist? = artists.firstOrNull { it.id == id }
        override suspend fun artistSongs(id: String): List<Song> = emptyList()
        override suspend fun search(query: String) = SearchResults(emptyList(), emptyList(), emptyList())
        override suspend fun anything(): List<Song> = emptyList()
    }

    @Test fun everyAlbumAndArtistOfALargeLibraryReachesTheCar() {
        CarLibraryOrStandIn.standIn = Large()
        try {
            CarConnection().open().use { car ->
                assertEquals(listOf("home", "playlists", "albums", "artists"), car.children(car.root).map { it.mediaId })

                // Why the letters were grey: a list handed over whole arrives cut short, well before its 1,000th entry.
                val whole = car.children("playlists")
                println("Car test setup: a list of ${playlists.size} reached the car as its first ${whole.size}")
                assertTrue("A list of ${playlists.size} reached the car as ${whole.size}: the cut this test is about is gone", whole.size < CarTree.MAX_ITEMS)
                assertEquals(playlists.take(whole.size).map { "playlist:" + it.id }, whole.map { it.mediaId })

                for ((tab, all) in listOf("albums" to albums.map { "album:" + it.id }, "artists" to artists.map { "artist:" + it.id })) {
                    val folders = car.children(tab)
                    assertEquals("$tab: a folder for each letter", letters.map { it.toString() }, folders.map { it.description.title.toString() })
                    assertTrue("$tab: letters open, they don't play", folders.all { it.isBrowsable && !it.isPlayable })
                    val reached = folders.flatMap { car.children(it.mediaId!!) }.map { it.mediaId }
                    assertEquals("$tab that reached the car", all.size, reached.size)
                    assertEquals(all, reached)
                }
                assertEquals("Q", car.children("albums")[16].description.title.toString())
                assertEquals(60, car.children(car.children("albums")[16].mediaId!!).size)
            }
        } finally {
            CarLibraryOrStandIn.standIn = null
        }
    }
}
