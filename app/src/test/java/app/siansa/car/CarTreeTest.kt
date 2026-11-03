package app.siansa.car

import android.app.Application
import android.net.Uri
import androidx.media3.session.MediaConstants
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Mix
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.RecentPlay
import app.siansa.data.SearchResults
import app.siansa.data.Song
import app.siansa.mix.MoodRadio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android Auto's browse tree (issue #62): its ids, its lists, and what picking something plays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CarTreeTest {
    private fun song(id: String, album: String = "A", albumId: String = "al1") =
        Song(id = id, title = "Song $id", artist = "Artist", album = album, albumId = albumId, durationSec = 200)

    private fun mix(id: String, selector: String, at: Long, songs: List<Song> = listOf(song("m$id"))) =
        Mix(id, selector, "2026-11-03", "Mix $id", "", "", songs, at, false)

    private val albumSongs = listOf(song("1"), song("2"), song("3"))
    private val chill = MoodRadio("chill", "Chill", "chill", 0L to 0L)

    private inner class Fake(
        var moods: List<MoodRadio> = listOf(chill),
        var recent: List<RecentPlay> = emptyList(),
        var found: SearchResults = SearchResults(emptyList(), emptyList(), emptyList()),
    ) : CarLibrary {
        val mixList = listOf(mix("old", "morning", 1), mix("new", "morning", 5), mix("eve", "evening", 3))
        override suspend fun mixes() = latestMixes(mixList)
        override suspend fun mix(id: String) = mixList.firstOrNull { it.id == id }
        override fun moods() = moods
        override suspend fun moodSongs(id: String) = if (id == "chill") listOf(song("c1"), song("c2")) else emptyList()
        override suspend fun recent() = recent
        override suspend fun playlists() = listOf(Playlist("p:1", "Road trip", songCount = 2, coverArt = "pl-1"))
        override suspend fun playlist(id: String) =
            if (id == "p:1") PlaylistDetail(Playlist("p:1", "Road trip", 2), listOf(song("x"), song("y"))) else null
        override suspend fun albums() = listOf(Album("al1", "First", artist = "Artist", coverArt = "al-1"), Album("al2", "Second"))
        override suspend fun album(id: String) = albums().firstOrNull { it.id == id }
        override suspend fun albumSongs(id: String) = if (id == "al1") albumSongs else emptyList()
        override suspend fun artists() = listOf(Artist("ar1", "Artist"))
        override suspend fun artist(id: String) = artists().firstOrNull { it.id == id }
        override suspend fun artistSongs(id: String) =
            listOf(song("s1", "One", "a1"), song("s2", "Two", "a2"), song("s3", "One", "a1"))
        override suspend fun search(query: String) = found
        override suspend fun anything() = listOf(song("r1"))
    }

    private fun tree(lib: CarLibrary = Fake()) = CarTree(lib) { Uri.parse("content://test.covers/cover/$it") }

    @Test fun idsSurviveTheTripThroughTheCar() {
        val ids = listOf(
            CarId.Root, CarId.Home, CarId.Recent, CarId.Playlists, CarId.Albums, CarId.Artists,
            CarId.Album("al:1/x y"), CarId.Playlist("p:1"), CarId.Artist("ar1"), CarId.Mix("m 1"), CarId.Mood("chill"),
            CarId.Search("a:b c"), CarId.Song(CarId.Album("al:1"), "so:ng"), CarId.Song(CarId.Search("x:y"), "s1"),
        )
        for (id in ids) assertEquals(id, CarId.parse(id.mediaId))
        assertEquals("album:al1", CarId.Album("al1").mediaId)
        assertEquals("root", CarId.Root.mediaId)
    }

    @Test fun theAppsOwnSongIdsAreNotTheCars() {
        assertNull(CarId.parse("8f3a9c0d1e"))
        assertNull(CarId.parse(""))
        assertNull(CarId.parse(null))
        assertNull(CarId.parse("book:123"))
        assertNull(CarId.parse("song:only-one-part"))
    }

    @Test fun theRootHasFourTabs() = runBlocking {
        val tabs = tree().children("root")!!
        assertEquals(listOf("home", "playlists", "albums", "artists"), tabs.map { it.mediaId })
        assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true })
        val albums = tabs[2].mediaMetadata.extras!!
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM, albums.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
        assertEquals(true, tree().root().mediaMetadata.isBrowsable)
    }

    @Test fun homeShowsTheNewestMixOfEachVibeAndTheMoodRadios() = runBlocking {
        val home = tree().children("home")!!
        assertEquals(listOf("mix:new", "mix:eve", "mood:chill"), home.map { it.mediaId })
        assertEquals("Your mixes", home[0].mediaMetadata.extras!!.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        assertEquals(true, home[2].mediaMetadata.isPlayable)
        assertEquals(false, home[2].mediaMetadata.isBrowsable)
    }

    @Test fun withoutAudioMuseThereAreNoMoodRadios() = runBlocking {
        val home = tree(Fake(moods = emptyList())).children("home")!!
        assertTrue(home.none { it.mediaId.startsWith("mood:") })
    }

    @Test fun recentlyPlayedShowsWhatTheCarCanOpen() = runBlocking {
        val lib = Fake(recent = listOf(
            RecentPlay("album/al1", "First", "al-1"),
            RecentPlay("book/b1", "A book"),
            RecentPlay("playlist/p%3A1", "Road trip"),
            RecentPlay("radio/mood/chill", "Chill"),
            RecentPlay("card/c1", "A card"),
        ))
        val home = tree(lib).children("home")!!
        assertEquals("recent", home.first().mediaId)
        val recent = tree(lib).children("recent")!!
        assertEquals(listOf("album:al1", "playlist:p%3A1", "mood:chill"), recent.map { it.mediaId })
        assertEquals(Uri.parse("content://test.covers/cover/al-1"), recent[0].mediaMetadata.artworkUri)
    }

    @Test fun nothingRecentShowsNoFolderForIt() = runBlocking {
        val home = tree(Fake(recent = listOf(RecentPlay("book/b1", "A book")))).children("home")!!
        assertFalse(home.any { it.mediaId == "recent" })
    }

    @Test fun anAlbumOpensToItsSongs() = runBlocking {
        val songs = tree().children("album:al1")!!
        assertEquals(3, songs.size)
        val first = songs.first()
        assertEquals(CarId.Song(CarId.Album("al1"), "1").mediaId, first.mediaId)
        assertEquals(true, first.mediaMetadata.isPlayable)
        assertEquals("Song 1", first.mediaMetadata.title)
        assertEquals(200_000L, first.mediaMetadata.durationMs)
        assertEquals(Uri.parse("content://test.covers/cover/al-al1"), first.mediaMetadata.artworkUri)
    }

    @Test fun anArtistsSongsKeepEachAlbumTogether() = runBlocking {
        val songs = tree().children("artist:ar1")!!
        assertEquals(listOf("s1", "s3", "s2"), songs.map { (CarId.parse(it.mediaId) as CarId.Song).songId })
        assertEquals("One", songs[0].mediaMetadata.extras!!.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        // Playing from the second song of the list starts where the list shows it.
        val queue = tree().queue(songs[1].mediaId)!!
        assertEquals(listOf("s1", "s3", "s2"), queue.songs.map { it.id })
        assertEquals(1, queue.start)
    }

    @Test fun playlistsAlbumsAndArtistsAreListed() = runBlocking {
        assertEquals(listOf("playlist:p%3A1"), tree().children("playlists")!!.map { it.mediaId })
        assertEquals(listOf("album:al1", "album:al2"), tree().children("albums")!!.map { it.mediaId })
        assertEquals(listOf("artist:ar1"), tree().children("artists")!!.map { it.mediaId })
        assertNull(tree().children("mood:chill"))
        assertNull(tree().children("not a list"))
    }

    @Test fun pickingASongPlaysItsListFromThere() = runBlocking {
        val queue = tree().queue(CarId.Song(CarId.Album("al1"), "2").mediaId)!!
        assertEquals(albumSongs, queue.songs)
        assertEquals(1, queue.start)
        assertEquals("album/al1", queue.route)
        assertEquals("First", queue.title)
        val playlist = tree().queue(CarId.Song(CarId.Playlist("p:1"), "y").mediaId)!!
        assertEquals(1, playlist.start)
        assertEquals("playlist/p%3A1", playlist.route)
    }

    @Test fun aSongNoLongerInItsListPlaysNothing() = runBlocking {
        assertNull(tree().queue(CarId.Song(CarId.Album("al1"), "gone").mediaId))
    }

    @Test fun aMoodRadioPlaysItsSongs() = runBlocking {
        val queue = tree().queue("mood:chill")!!
        assertEquals(listOf("c1", "c2"), queue.songs.map { it.id })
        assertEquals("radio/mood/chill", queue.route)
        assertEquals("Chill", queue.title)
    }

    @Test fun searchListsSongsThenAlbumsThenArtists() = runBlocking {
        val lib = Fake(found = SearchResults(listOf(Artist("ar1", "Artist")), listOf(Album("al1", "First")), listOf(song("1"), song("2"))))
        val items = tree(lib).search("first")
        assertEquals(4, items.size)
        assertEquals(CarId.Song(CarId.Search("first"), "1").mediaId, items[0].mediaId)
        assertEquals("Songs", items[0].mediaMetadata.extras!!.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        assertEquals("Albums", items[2].mediaMetadata.extras!!.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        assertEquals("artist:ar1", items[3].mediaId)
        // A found song plays the songs found, from it.
        assertEquals(1, tree(lib).queue(items[1].mediaId)!!.start)
    }

    @Test fun voicePlaysWhatItNames() = runBlocking {
        val lib = Fake(found = SearchResults(listOf(Artist("ar1", "Artist")), listOf(Album("al1", "First")), listOf(song("9"))))
        assertEquals("artist/ar1", tree(lib).voice("artist")!!.route)
        assertEquals("album/al1", tree(lib).voice("First")!!.route)
        assertEquals(listOf("9"), tree(lib).voice("something else")!!.songs.map { it.id })
        // Nothing said: the newest mix.
        assertEquals("mix/new", tree(lib).voice(" ")!!.route)
        assertNull(tree(Fake()).voice("nothing found"))
    }

    @Test fun itemsAreFoundByTheirIds() = runBlocking {
        assertEquals("First", tree().item("album:al1")!!.mediaMetadata.title)
        assertEquals(true, tree().item("home")!!.mediaMetadata.isBrowsable)
        assertEquals(true, tree().item("mood:chill")!!.mediaMetadata.isPlayable)
        assertNotNull(tree().item(CarId.Song(CarId.Album("al1"), "1").mediaId))
        assertNull(tree().item("8f3a9c0d1e"))
    }

    @Test fun longListsArePaged() {
        val items = (1..2500).toList()
        assertEquals((1..10).toList(), CarTree.page(items, 0, 10))
        assertEquals((11..20).toList(), CarTree.page(items, 1, 10))
        assertEquals(CarTree.MAX_ITEMS, CarTree.page(items, 0, Int.MAX_VALUE).size)
        assertTrue(CarTree.page(items, 5, Int.MAX_VALUE).isEmpty())
        assertTrue(CarTree.page(items, 200, 10).isEmpty())
    }

    @Test fun latestMixesKeepsTheNewestOfEachVibe() {
        val mixes = listOf(mix("a", "x", 1), mix("b", "x", 2), mix("c", "y", 3), mix("d", "z", 4, songs = emptyList()))
        assertEquals(listOf("c", "b"), latestMixes(mixes).map { it.id })
    }
}
