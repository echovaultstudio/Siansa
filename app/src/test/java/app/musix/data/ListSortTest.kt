package app.musix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sorting Liked songs and playlists (issue #257). */
class ListSortTest {
    private val songs = listOf(
        Song("1", "beta", artist = "Zed", album = "B"),
        Song("2", "Alpha", artist = "abba", album = "A", track = 2),
        Song("3", "gamma", artist = "Abba", album = "A", track = 1),
    )
    private val dates = mapOf(
        "1" to SongDates(created = "2026-01-05T10:00:00Z", played = "2026-10-01T08:00:00.5Z"),
        "2" to SongDates(created = "2026-03-01T10:00:00Z"),
        "3" to SongDates(created = null, played = "2026-09-01T08:00:00Z"),
    )

    private fun sort(by: ListSort, heard: Map<String, Long> = emptyMap()) =
        sortSongs(songs, by, { it }, { dates[it] }, { heard[it] }).map { it.id }

    @Test fun defaultKeepsTheListsOwnOrder() = assertEquals(listOf("1", "2", "3"), sort(ListSort.DEFAULT))

    @Test fun titleAndArtistIgnoreCase() {
        assertEquals(listOf("2", "1", "3"), sort(ListSort.TITLE))
        // Same artist: by album, then track.
        assertEquals(listOf("3", "2", "1"), sort(ListSort.ARTIST))
    }

    @Test fun dateAddedNewestFirstAndUndatedLast() = assertEquals(listOf("2", "1", "3"), sort(ListSort.ADDED))

    @Test fun lastPlayedTakesTheLaterOfNavidromeAndMusix() {
        assertEquals(listOf("1", "3", "2"), sort(ListSort.PLAYED))
        val heardInMusix = mapOf("2" to isoMillis("2026-10-10T00:00:00Z")!!)
        assertEquals(listOf("2", "1", "3"), sort(ListSort.PLAYED, heardInMusix))
    }

    @Test fun datesAreRead() {
        assertEquals(1_767_225_600_000L, isoMillis("2026-01-01T00:00:00Z"))
        assertEquals(1_767_225_600_000L, isoMillis("2026-01-01T01:00:00+01:00"))
        assertNull(isoMillis("yesterday"))
        assertNull(isoMillis(null))
    }

    @Test fun unknownSavedSortIsTheDefault() {
        assertEquals(ListSort.DEFAULT, ListSort.parse("gone"))
        assertEquals(ListSort.ARTIST, ListSort.parse("ARTIST"))
    }
}
