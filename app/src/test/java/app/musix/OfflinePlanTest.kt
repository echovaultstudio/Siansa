package app.musix

import app.musix.data.AppJson
import app.musix.data.ArtistRef
import app.musix.data.Song
import app.musix.offline.DownloadList
import app.musix.offline.OfflineItem
import app.musix.offline.OfflineKind
import app.musix.offline.OfflinePlan
import app.musix.offline.OfflineState
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

    // Phone-only playlists (issue #147) are always kept downloaded.
    private fun phone(id: String) = OfflineItem(OfflineKind.PLAYLIST, "local-$id", "Phone $id")

    // Syncs [now] after [before] (the playlists at the last sync; null = the app just started).
    private fun sync(
        list: DownloadList,
        now: List<Pair<OfflineItem, List<app.musix.data.Song>>>,
        before: List<Pair<OfflineItem, List<app.musix.data.Song>>>? = emptyList(),
        at: Long = 1,
    ) = OfflinePlan.syncPhonePlaylists(list, now, before?.associate { (i, s) -> i.key to s.mapTo(HashSet()) { it.id } }, at)

    @Test fun phonePlaylistSongsDownload() {
        val list = sync(DownloadList(), listOf(phone("a") to songs("1", "2")), at = 5)
        assertEquals(listOf("1", "2"), list.waiting.map { it.id })
        assertEquals(5L, list.items.single().addedAt)
    }

    @Test fun phonePlaylistsDownloadWhenTheAppStarts() {
        val list = sync(DownloadList(), listOf(phone("a") to songs("1")), before = null)
        assertEquals(listOf("1"), list.waiting.map { it.id })
    }

    @Test fun songAddedToPhonePlaylistDownloads() {
        val a1 = listOf(phone("a") to songs("1"))
        val first = sync(DownloadList(), a1, at = 5).doneAll()
        val list = sync(first, listOf(phone("a") to songs("1", "2")), before = a1, at = 9)
        assertEquals(listOf("2"), list.waiting.map { it.id })
        assertEquals(5L, list.items.single().addedAt)
    }

    @Test fun songTakenOutOfPhonePlaylistIsDeletedUnlessKeptElsewhere() {
        val start = OfflinePlan.choose(DownloadList(), album("x"), songs("2"))
        val a = listOf(phone("a") to songs("1", "2", "3"))
        val first = sync(start, a).doneAll()
        val list = sync(first, listOf(phone("a") to songs("3")), before = a)
        assertEquals(setOf("2", "3"), list.done.map { it.song.id }.toSet())
    }

    @Test fun deletedPhonePlaylistLosesItsDownloads() {
        val both = listOf(phone("a") to songs("1"), phone("b") to songs("2"))
        val first = sync(DownloadList(), both).doneAll()
        val list = sync(first, both.drop(1), before = both)
        assertEquals(listOf("2"), list.done.map { it.song.id })
        assertEquals(listOf("local-b"), list.items.map { it.id })
    }

    @Test fun emptyPhonePlaylistKeepsNothing() {
        val a = listOf(phone("a") to songs("1"))
        val list = sync(sync(DownloadList(), a), listOf(phone("a") to emptyList()), before = a)
        assertTrue(list.items.isEmpty())
        assertTrue(list.waiting.isEmpty())
    }

    @Test fun phoneSyncLeavesNavidromePlaylistsAlone() {
        val start = OfflinePlan.choose(DownloadList(), playlist("p"), songs("1"))
        assertEquals(start, sync(start, emptyList()))
    }

    @Test fun unchangedPhonePlaylistChangesNothing() {
        val a = listOf(phone("a") to songs("1"))
        val first = sync(DownloadList(), a)
        assertEquals(first, sync(first, a, before = a, at = 2))
    }

    @Test fun songRemovedByHandStaysOutUntilAddedAgain() {
        val a12 = listOf(phone("a") to songs("1", "2"))
        val removed = OfflinePlan.removeSongs(sync(DownloadList(), a12).doneAll(), setOf("2"))
        val renamed = listOf(phone("a").copy(name = "New") to songs("1", "2"))
        val afterRename = sync(removed, renamed, before = a12)
        assertEquals(listOf("1"), afterRename.items.single().songIds)
        assertEquals(listOf("1"), sync(afterRename, renamed, before = null).items.single().songIds)
        // Taken out of the playlist and added back: it downloads again.
        val a1 = listOf(phone("a") to songs("1"))
        val back = sync(sync(afterRename, a1, before = renamed), a12, before = a1)
        assertEquals(listOf("2"), back.waiting.map { it.id })
    }
}
