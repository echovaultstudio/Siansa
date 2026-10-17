package app.musix

import app.musix.data.Album
import app.musix.data.AppSettings
import app.musix.data.Artist
import app.musix.data.GuestMusic
import app.musix.data.Playlist
import app.musix.data.Song
import app.musix.data.UserFiles
import app.musix.net.ConnectionState
import app.musix.offline.OfflineShelf
import app.musix.offline.OnlyDownloaded
import app.musix.playback.playLimit
import app.musix.ui.sourceChipLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The source filter (issue #141): several users' music at once, and only downloaded music. */
class SourcesTest {
    // ---- Who shows ----

    @Test fun theUserInUseAlwaysShows() {
        assertEquals(setOf(0), UserFiles.parseShown(null, 0, listOf(0, 1)))
        assertEquals(setOf(0, 1), UserFiles.parseShown("1", 0, listOf(0, 1)))
        assertEquals(setOf(1, 2), UserFiles.parseShown("2", 1, listOf(0, 1, 2)))
    }

    @Test fun removedUsersDropOutOfTheShownList() {
        assertEquals(setOf(0), UserFiles.parseShown("0,3", 0, listOf(0, 1)))
        assertEquals(setOf(0, 1), UserFiles.parseShown(" 1 ,x,", 0, listOf(0, 1)))
        assertEquals("0,1,2", UserFiles.formatShown(setOf(2, 0, 1)))
    }

    @Test fun switchingFromOneShownUserShowsOnlyTheNewOne() {
        assertEquals(setOf(1), UserFiles.shownAfterSwitch(setOf(0), old = 0, new = 1))
    }

    @Test fun switchingWhileSeveralShowKeepsThemAll() {
        assertEquals(setOf(0, 1), UserFiles.shownAfterSwitch(setOf(0, 1), old = 0, new = 1))
        assertEquals(setOf(0, 1, 2), UserFiles.shownAfterSwitch(setOf(0, 2), old = 0, new = 1))
    }

    @Test fun hidingTheUserInUseSwitchesToAnotherShownOne() {
        assertEquals(1 to setOf(1), UserFiles.afterHidingActive(setOf(0, 1), active = 0))
        assertEquals(1 to setOf(1, 2), UserFiles.afterHidingActive(setOf(0, 1, 2), active = 0))
        // Someone has to stay.
        assertNull(UserFiles.afterHidingActive(setOf(0), active = 0))
    }

    // ---- Another user's music joining the library ----

    @Test fun whatTheLibraryHasAlreadyStaysTheFirstUsers() {
        val music = GuestMusic(
            artists = listOf(Artist("ar1", "A"), Artist("ar2", "B")),
            albums = listOf(Album(id = "al1", name = "One"), Album(id = "al2", name = "Two"), Album(id = "al2", name = "Two")),
            songs = listOf(Song(id = "s1", title = "a"), Song(id = "s2", title = "b")),
            playlists = listOf(Playlist(id = "p1", name = "Kids"), Playlist(id = "p2", name = "Shared")),
        )
        val plan = GuestMusic.plan(music, knownSongs = setOf("s1"), knownAlbums = setOf("al1"), knownArtists = setOf("ar1"), knownPlaylists = setOf("p2"))
        assertEquals(listOf("ar2"), plan.artists.map { it.id })
        assertEquals(listOf("al2"), plan.albums.map { it.id })
        assertEquals(listOf("s2"), plan.songs.map { it.id })
        assertEquals(listOf("p1"), plan.playlists.map { it.id })
        assertEquals(listOf("ar2", "al2", "s2", "p1"), plan.ids)
    }

    @Test fun coverIdsLeadToTheirItem() {
        assertEquals("abc123", GuestMusic.itemOfCover("al-abc123_6543fe"))
        assertEquals("abc123", GuestMusic.itemOfCover("mf-abc123"))
        assertEquals("x", GuestMusic.itemOfCover("pl-x_0"))
        assertEquals("y", GuestMusic.itemOfCover("ar-y_1"))
        assertNull(GuestMusic.itemOfCover("abc123"))
        assertNull(GuestMusic.itemOfCover("xx-abc"))
    }

    // ---- Only downloaded music ----

    private val shelf = OfflineShelf(songs = setOf("s1", "s3"), albums = setOf("al1"), artists = setOf("ar1"), playlists = setOf("p1"))

    @Test fun onlyWhatIsOnThePhoneShows() {
        val only = OnlyDownloaded(shelf, albumIds = setOf("al1", "al1-copy"))
        assertEquals(listOf("s1", "s3"), only.songs(listOf(Song(id = "s1", title = ""), Song(id = "s2", title = ""), Song(id = "s3", title = ""))).map { it.id })
        // An album shows when any of its copies has downloaded songs.
        val albums = listOf(Album(id = "al1-copy", name = "A"), Album(id = "al2", name = "B"), Album(id = "al3", name = "C", ids = listOf("al3", "al1")))
        assertEquals(listOf("al1-copy", "al3"), only.albums(albums).map { it.id })
        assertEquals(listOf("ar1"), only.artists(listOf(Artist("ar1", "X"), Artist("ar2", "Y"))).map { it.id })
        assertEquals(listOf("p1"), only.playlists(listOf(Playlist(id = "p1", name = "a"), Playlist(id = "p2", name = "b"))).map { it.id })
    }

    @Test fun genresAreCountedFromDownloadedSongs() {
        val songs = listOf("Rock", "rock", "Jazz", " ", null, "Pop", "Pop", "Pop").mapIndexed { i, g -> Song(id = "$i", title = "", genre = g) }
        val genres = OnlyDownloaded.genres(songs, limit = 2)
        assertEquals(listOf("Pop" to 3, "Rock" to 2), genres.map { it.name to it.songCount })
    }

    @Test fun playingSaysWhyOnlyDownloadsPlay() {
        assertEquals("You're offline", playLimit(offline = true, onlyDownloads = true))
        assertEquals("Only downloaded music is on", playLimit(offline = false, onlyDownloads = true))
        assertNull(playLimit(offline = false, onlyDownloads = false))
    }

    @Test fun aiToolsAreOffWithOnlyDownloadedMusic() {
        val s = AppSettings(audioMuseUrl = "http://am", downloadedOnly = true)
        assertEquals("Only downloaded music is on.", ConnectionState(network = true, audioMuse = true).aiProblem(s))
        assertEquals(false, ConnectionState(network = true, audioMuse = true).aiAvailable(s))
    }

    @Test fun homeChipsSayWhatIsFiltered() {
        assertEquals(emptyList<String>(), sourceChipLabels(downloadedOnly = false, shownUsers = 1))
        assertEquals(listOf("Only downloaded", "2 users"), sourceChipLabels(downloadedOnly = true, shownUsers = 2))
    }
}
