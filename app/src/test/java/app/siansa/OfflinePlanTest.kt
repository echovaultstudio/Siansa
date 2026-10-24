package app.siansa

import app.siansa.data.AppJson
import app.siansa.data.ArtistRef
import app.siansa.data.Song
import app.siansa.offline.DownloadList
import app.siansa.offline.OfflineItem
import app.siansa.offline.OfflineKind
import app.siansa.offline.OfflinePlan
import app.siansa.offline.OfflineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Downloads for offline listening: what gets downloaded, kept and deleted as albums and playlists are chosen. */
class OfflinePlanTest {
    private fun song(id: String) = Song(id = id, title = "T$id")
    private fun songs(vararg ids: String) = ids.map { song(it) }
    private fun album(id: String) = OfflineItem(OfflineKind.ALBUM, id, "Album $id")
    private fun playlist(id: String) = OfflineItem(OfflineKind.PLAYLIST, id, "Playlist $id")

    private fun DownloadList.doneAll() = waiting.fold(this) { list, s -> OfflinePlan.finished(list, s, 100, 1) }

    @Test fun choosingQueuesItsSongs() {
        val list = OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2"))
        assertEquals(listOf("1", "2"), list.waiting.map { it.id })
        assertEquals(listOf("1", "2"), list.items.single().songIds)
    }

    @Test fun songsAlreadyHereAreNotDownloadedAgain() {
        val first = OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2")).doneAll()
        val list = OfflinePlan.choose(first, playlist("p"), songs("2", "3"))
        assertEquals(listOf("3"), list.waiting.map { it.id })
        assertEquals(2, list.items.size)
    }

    @Test fun removingKeepsSongsAnotherItemWants() {
        val list = OfflinePlan.choose(OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2")), playlist("p"), songs("2", "3")).doneAll()
        val after = OfflinePlan.drop(list, album("a").key)
        assertEquals(listOf("2", "3"), after.done.map { it.song.id })
        assertEquals(setOf("1"), (list.done.map { it.song.id } - after.done.map { it.song.id }.toSet()).toSet())
    }

    @Test fun removingStopsWaitingSongs() {
        val list = OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2"))
        val after = OfflinePlan.drop(list, album("a").key)
        assertTrue(after.waiting.isEmpty())
        assertTrue(after.items.isEmpty())
    }

    @Test fun playlistUpdateAddsNewSongsAndDropsRemovedOnes() {
        val list = OfflinePlan.choose(DownloadList(), playlist("p"), songs("1", "2")).doneAll()
        val after = OfflinePlan.choose(list, playlist("p"), songs("2", "3"))
        assertEquals(listOf("2"), after.done.map { it.song.id })
        assertEquals(listOf("3"), after.waiting.map { it.id })
        assertEquals(listOf("2", "3"), after.items.single().songIds)
    }

    @Test fun choosingTwiceKeepsWhenItWasFirstChosen() {
        val list = OfflinePlan.choose(DownloadList(), album("a").copy(addedAt = 5), songs("1"))
        val again = OfflinePlan.choose(list, album("a").copy(addedAt = 9), songs("1"))
        assertEquals(5L, again.items.single().addedAt)
    }

    @Test fun buttonState() {
        val ids = listOf("1", "2")
        assertEquals(OfflineState.NONE, OfflinePlan.state(ids, emptySet(), emptySet()))
        assertEquals(OfflineState.WAITING, OfflinePlan.state(ids, setOf("1"), setOf("2")))
        assertEquals(OfflineState.PARTLY, OfflinePlan.state(ids, setOf("1"), emptySet()))
        assertEquals(OfflineState.ALL, OfflinePlan.state(ids, setOf("1", "2"), emptySet()))
        assertEquals(OfflineState.NONE, OfflinePlan.state(emptyList(), setOf("1"), emptySet()))
    }

    @Test fun storageLimit() {
        assertFalse(OfflinePlan.full(10L shl 30, 0))
        assertFalse(OfflinePlan.full(1023L shl 20, 1024))
        assertTrue(OfflinePlan.full(1024L shl 20, 1024))
    }

    @Test fun opusDownloadsMayDifferFromTheirEstimatedLength() {
        assertTrue(OfflinePlan.complete(100, 100, converted = false))
        assertFalse(OfflinePlan.complete(90, 100, converted = false))
        assertTrue(OfflinePlan.complete(90, 100, converted = true))
        assertTrue(OfflinePlan.complete(90, -1, converted = false))
    }

    @Test fun downloadKeepsItsQuality() {
        val list = OfflinePlan.finished(DownloadList(), song("1"), 42, 7, kbps = 160)
        assertEquals(160, list.done.single().kbps)
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("abc123", OfflinePlan.fileName("abc123"))
        assertEquals("______etc", OfflinePlan.fileName("../../etc"))
    }

    @Test fun listSurvivesSavingAndLoading() {
        val list = OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2")).let { OfflinePlan.finished(it, song("1"), 42, 7) }
        val text = AppJson.encodeToString(DownloadList.serializer(), list)
        assertEquals(list, AppJson.decodeFromString(DownloadList.serializer(), text))
    }

    @Test fun removingSongsByHandTakesThemOutEverywhere() {
        val list = OfflinePlan.choose(OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2")), playlist("p"), songs("2", "3")).doneAll()
        val after = OfflinePlan.removeSongs(list, setOf("2", "3"))
        assertEquals(listOf("1"), after.done.map { it.song.id })
        assertEquals(listOf("1"), after.items.single { it.key == album("a").key }.songIds)
        // The playlist had only songs 2 and 3, so it's gone.
        assertTrue(after.items.none { it.key == playlist("p").key })
        assertEquals(setOf("2", "3"), after.skipped)
    }

    @Test fun songsRemovedByHandStayOutWhenAPlaylistIsCheckedAgain() {
        val list = OfflinePlan.choose(DownloadList(), playlist("p"), songs("1", "2", "3")).doneAll()
        val removed = OfflinePlan.removeSongs(list, setOf("2"))
        // The playlist on the server still has song 2, and a new song 4.
        val checked = OfflinePlan.choose(removed, playlist("p"), songs("1", "2", "3", "4"), explicit = false)
        assertEquals(listOf("4"), checked.waiting.map { it.id })
        assertEquals(listOf("1", "3", "4"), checked.items.single().songIds)
    }

    @Test fun choosingAgainBringsRemovedSongsBack() {
        val list = OfflinePlan.removeSongs(OfflinePlan.choose(DownloadList(), playlist("p"), songs("1", "2")).doneAll(), setOf("2"))
        val again = OfflinePlan.choose(list, playlist("p"), songs("1", "2"))
        assertEquals(listOf("2"), again.waiting.map { it.id })
        assertTrue(again.skipped.isEmpty())
    }

    @Test fun oldSavedListsWithoutSkippedStillLoad() {
        val text = """{"items":[],"done":[],"waiting":[]}"""
        assertTrue(AppJson.decodeFromString(DownloadList.serializer(), text).skipped.isEmpty())
    }

    @Test fun shelfShowsWhatIsOnThePhone() {
        val one = Song(id = "1", title = "One", albumId = "al1", artistId = "ar1", artists = listOf(ArtistRef("ar1", "A"), ArtistRef("ar2", "B")))
        val two = Song(id = "2", title = "Two", albumId = "al2", artistId = "ar3")
        val liked = OfflineItem(OfflineKind.LIKED, OfflineItem.LIKED_ID, "Liked songs")
        val list = OfflinePlan.choose(DownloadList(), playlist("p"), listOf(one, two))
            .let { OfflinePlan.choose(it, playlist("waiting"), listOf(Song(id = "3", title = "Three"))) }
            .let { OfflinePlan.choose(it, liked, listOf(Song(id = "4", title = "Four"))) }
            .let { OfflinePlan.finished(it, one, 100, 1) }
        val shelf = OfflinePlan.shelf(list)
        assertEquals(setOf("1"), shelf.songs)
        assertEquals(setOf("al1"), shelf.albums)
        assertEquals(setOf("ar1", "ar2"), shelf.artists)
        // Only playlists with something already downloaded; liked songs aren't here yet.
        assertEquals(setOf("p"), shelf.playlists)
        assertFalse(shelf.liked)
        assertTrue(shelf.hasAll(listOf("1")))
        assertFalse(shelf.hasAll(listOf("1", "2")))
        assertFalse(shelf.hasAll(emptyList()))
    }

    @Test fun emptyShelf() {
        assertTrue(OfflinePlan.shelf(DownloadList()).isEmpty)
    }

    // Phone-only playlists (issue #147): only once chosen for download are they kept up to date (issue #235).
    private fun phone(id: String) = OfflineItem(OfflineKind.PLAYLIST, "local-$id", "Phone $id")

    // Syncs [now] after [before] (the playlists at the last sync; null = the app just started).
    private fun sync(
        list: DownloadList,
        now: List<Pair<OfflineItem, List<app.siansa.data.Song>>>,
        before: List<Pair<OfflineItem, List<app.siansa.data.Song>>>? = emptyList(),
    ) = OfflinePlan.syncPhonePlaylists(list, now, before?.associate { (i, s) -> i.key to s.mapTo(HashSet()) { it.id } })

    // The playlists chosen for download with their download button, at [at].
    private fun downloaded(playlists: List<Pair<OfflineItem, List<app.siansa.data.Song>>>, at: Long = 1) =
        playlists.fold(DownloadList()) { l, (item, songs) -> OfflinePlan.choose(l, item.copy(addedAt = at), songs) }

    @Test fun aNewPhonePlaylistDoesNotDownloadByItself() {
        val a = listOf(phone("a") to songs("1", "2"))
        assertEquals(DownloadList(), sync(DownloadList(), a))
        assertEquals(DownloadList(), sync(DownloadList(), a, before = null))
        // Songs added later don't start it either.
        assertEquals(DownloadList(), sync(DownloadList(), listOf(phone("a") to songs("1", "2", "3")), before = a))
    }

    @Test fun aChosenPhonePlaylistKeepsItsSongs() {
        val list = downloaded(listOf(phone("a") to songs("1", "2")), at = 5)
        assertEquals(listOf("1", "2"), list.waiting.map { it.id })
        assertEquals(list, sync(list, listOf(phone("a") to songs("1", "2")), before = null))
    }

    @Test fun songAddedToPhonePlaylistDownloads() {
        val a1 = listOf(phone("a") to songs("1"))
        val first = downloaded(a1, at = 5).doneAll()
        val list = sync(first, listOf(phone("a") to songs("1", "2")), before = a1)
        assertEquals(listOf("2"), list.waiting.map { it.id })
        assertEquals(5L, list.items.single().addedAt)
    }

    @Test fun songTakenOutOfPhonePlaylistIsDeletedUnlessKeptElsewhere() {
        val a = listOf(phone("a") to songs("1", "2", "3"))
        val first = OfflinePlan.choose(downloaded(a), album("x"), songs("2")).doneAll()
        val list = sync(first, listOf(phone("a") to songs("3")), before = a)
        assertEquals(setOf("2", "3"), list.done.map { it.song.id }.toSet())
    }

    @Test fun deletedPhonePlaylistLosesItsDownloads() {
        val both = listOf(phone("a") to songs("1"), phone("b") to songs("2"))
        val first = downloaded(both).doneAll()
        val list = sync(first, both.drop(1), before = both)
        assertEquals(listOf("2"), list.done.map { it.song.id })
        assertEquals(listOf("local-b"), list.items.map { it.id })
    }

    @Test fun emptyPhonePlaylistKeepsNothing() {
        val a = listOf(phone("a") to songs("1"))
        val list = sync(downloaded(a), listOf(phone("a") to emptyList()), before = a)
        assertTrue(list.items.isEmpty())
        assertTrue(list.waiting.isEmpty())
    }

    @Test fun phoneSyncLeavesNavidromePlaylistsAlone() {
        val start = OfflinePlan.choose(DownloadList(), playlist("p"), songs("1"))
        assertEquals(start, sync(start, emptyList()))
    }

    @Test fun unchangedPhonePlaylistChangesNothing() {
        val a = listOf(phone("a") to songs("1"))
        val first = downloaded(a)
        assertEquals(first, sync(first, a, before = a))
    }

    @Test fun songRemovedByHandStaysOutUntilAddedAgain() {
        val a12 = listOf(phone("a") to songs("1", "2"))
        val removed = OfflinePlan.removeSongs(downloaded(a12).doneAll(), setOf("2"))
        val renamed = listOf(phone("a").copy(name = "New") to songs("1", "2"))
        val afterRename = sync(removed, renamed, before = a12)
        assertEquals(listOf("1"), afterRename.items.single().songIds)
        assertEquals(listOf("1"), sync(afterRename, renamed, before = null).items.single().songIds)
        // Taken out of the playlist and added back: it downloads again.
        val a1 = listOf(phone("a") to songs("1"))
        val back = sync(sync(afterRename, a1, before = renamed), a12, before = a1)
        assertEquals(listOf("2"), back.waiting.map { it.id })
    }

    // ---- Stop all downloads (issue #235) ----

    @Test fun stopAllDropsWhatIsWaitingAndKeepsWhatIsDownloaded() {
        val list = OfflinePlan.choose(OfflinePlan.choose(DownloadList(), album("x"), songs("1", "2")).doneAll(), album("y"), songs("3", "4"))
        val stopped = OfflinePlan.stopAll(list)
        assertTrue(stopped.waiting.isEmpty())
        assertEquals(setOf("1", "2"), stopped.done.map { it.song.id }.toSet())
        // Album y had nothing yet, so it goes; the songs don't come back when it's checked again.
        assertEquals(listOf("album:x"), stopped.items.map { it.key }.map { it.lowercase() })
        assertEquals(setOf("3", "4"), stopped.skipped)
    }

    @Test fun stopAllWithNothingWaitingChangesNothing() {
        val list = OfflinePlan.choose(DownloadList(), album("x"), songs("1")).doneAll()
        assertEquals(list, OfflinePlan.stopAll(list))
    }
}
