package app.musix

import app.musix.data.AppJson
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

    @Test fun fileNamesAreSafe() {
        assertEquals("abc123", OfflinePlan.fileName("abc123"))
        assertEquals("______etc", OfflinePlan.fileName("../../etc"))
    }

    @Test fun listSurvivesSavingAndLoading() {
        val list = OfflinePlan.choose(DownloadList(), album("a"), songs("1", "2")).let { OfflinePlan.finished(it, song("1"), 42, 7) }
        val text = AppJson.encodeToString(DownloadList.serializer(), list)
        assertEquals(list, AppJson.decodeFromString(DownloadList.serializer(), text))
    }
}
