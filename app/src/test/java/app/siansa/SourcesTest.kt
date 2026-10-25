package app.siansa

import app.siansa.data.Album
import app.siansa.data.AppSettings
import app.siansa.data.Artist
import app.siansa.data.GuestMusic
import app.siansa.data.Playlist
import app.siansa.data.Song
import app.siansa.data.UserFiles
import app.siansa.net.ConnectionState
import app.siansa.offline.OfflineShelf
import app.siansa.offline.OnlyDownloaded
import app.siansa.playback.playLimit
import app.siansa.ui.sourceChipLabels
import app.siansa.ui.sourceFilterActive
import app.siansa.ui.canToggleUser
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

    @Test fun aSongsOwnArtistGoesToItsServer() {
        // An Emby song's artist isn't in Emby's album artist list; opening it from the song asked the wrong server.
        val music = GuestMusic(
            songs = listOf(
                Song(id = "s1", title = "a", artistId = "858791", albumId = "al9", artists = listOf(app.siansa.data.ArtistRef("858791", "Skrillex & Bobby Raps"))),
                Song(id = "s2", title = "b", artistId = "mine"),
            ),
        )
        val plan = GuestMusic.plan(music, knownSongs = emptySet(), knownAlbums = emptySet(), knownArtists = setOf("mine"), knownPlaylists = emptySet())
        assertEquals(listOf("s1", "s2", "858791", "al9"), plan.ids)
    }

    @Test fun coverIdsLeadToTheirItem() {
        assertEquals("abc123", GuestMusic.itemOfCover("al-abc123_6543fe"))
        assertEquals("abc123", GuestMusic.itemOfCover("mf-abc123"))
        assertEquals("x", GuestMusic.itemOfCover("pl-x_0"))
        assertEquals("y", GuestMusic.itemOfCover("ar-y_1"))
        assertNull(GuestMusic.itemOfCover("abc123"))
        assertNull(GuestMusic.itemOfCover("xx-abc"))
        // Issue #226: disc covers belong to their album, so they go out with the login of whoever's album it is.
        assertEquals("5jL9UOlXosmr2imPANHgvl", GuestMusic.itemOfCover("dc-5jL9UOlXosmr2imPANHgvl:1"))
        assertEquals("abc", GuestMusic.itemOfCover("dc-abc:12_6543fe"))
        assertNull(GuestMusic.itemOfCover("dc-"))
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
        assertEquals(emptyList<String>(), sourceChipLabels(downloadedOnly = false))
        assertEquals(listOf("Only downloaded"), sourceChipLabels(downloadedOnly = true))
        // Two users shown: no chip, but the filter button stays lit (beta report 5737de2e).
        assertEquals(false, sourceFilterActive(downloadedOnly = false, shownUsers = 1))
        assertEquals(true, sourceFilterActive(downloadedOnly = false, shownUsers = 2))
        assertEquals(true, sourceFilterActive(downloadedOnly = true, shownUsers = 1))
        // Issue #427: the user in use's music can be hidden (no restart) only while another user's shows.
        assertEquals(false, canToggleUser(connected = true, inUse = true, ownHidden = false, shownUsers = 1))
        assertEquals(true, canToggleUser(connected = true, inUse = true, ownHidden = false, shownUsers = 2))
        assertEquals(true, canToggleUser(connected = true, inUse = true, ownHidden = true, shownUsers = 2))
        assertEquals(true, canToggleUser(connected = true, inUse = false, ownHidden = false, shownUsers = 1))
        assertEquals(false, canToggleUser(connected = false, inUse = false, ownHidden = false, shownUsers = 1))
    }

    @Test fun homeShortcutsLeaveOutHiddenAlbums() {
        // Beta #17: an Emby album played earlier stayed on Home after Emby was hidden, and opened on Navidrome.
        val played = listOf(
            app.siansa.data.RecentPlay("album/887226", "Emby album"),
            app.siansa.data.RecentPlay("album/nav1", "Navidrome album"),
            app.siansa.data.RecentPlay("mix/m1", "A mix"),
        )
        assertEquals(listOf("album/nav1", "mix/m1"), app.siansa.ui.shownRecents(played, setOf("nav1")).map { it.route })
        // Before the first sync nothing is known: all stay.
        assertEquals(3, app.siansa.ui.shownRecents(played, null).size)
        assertEquals("887226", app.siansa.ui.recentAlbumId("album/887226"))
        assertNull(app.siansa.ui.recentAlbumId("playlist/p1"))
    }

    @Test fun onlyUsersOnTheSameServerShowTogether() {
        val nav = app.siansa.net.SourceKind.OPENSUBSONIC
        val emby = app.siansa.net.SourceKind.EMBY
        assertEquals(true, UserFiles.sameServer(nav, "http://192.168.1.106:30043", nav, "http://192.168.1.106:30043/"))
        assertEquals(true, UserFiles.sameServer(nav, "https://Music.example.com/nd", nav, "https://music.example.com/nd/"))
        assertEquals(true, UserFiles.sameServer(nav, "https://music.example.com", nav, "https://music.example.com:443"))
        // Another server, or another kind on the same host: switched to, not shown together.
        assertEquals(false, UserFiles.sameServer(nav, "http://192.168.1.106:30043", emby, "http://192.168.1.106:8096"))
        assertEquals(false, UserFiles.sameServer(nav, "http://192.168.1.106:30043", nav, "http://192.168.1.107:30043"))
        assertEquals(false, UserFiles.sameServer(nav, "", nav, ""))
    }

    @Test fun aServerAtAnotherAddressIsKnownByItsSongs() {
        // kids on music.home.example.com and max on 192.168.1.106: one Navidrome, the same song ids.
        val ids = listOf("a", "b", "c", "d")
        assertEquals(true, app.siansa.data.sameServerBySongs(ids, setOf("a", "b", "c", "d")))
        assertEquals(true, app.siansa.data.sameServerBySongs(ids, setOf("a", "b")))
        // Another server's ids aren't in the library.
        assertEquals(false, app.siansa.data.sameServerBySongs(ids, setOf("a")))
        assertEquals(false, app.siansa.data.sameServerBySongs(ids, emptySet()))
        assertEquals(false, app.siansa.data.sameServerBySongs(emptyList(), emptySet()))
    }
}
