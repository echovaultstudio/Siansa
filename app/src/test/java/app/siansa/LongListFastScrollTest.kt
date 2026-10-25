package app.siansa

import app.siansa.data.ListSort
import app.siansa.data.Song
import app.siansa.data.SongDates
import app.siansa.ui.AlphabetIndex
import app.siansa.ui.FastScroll
import app.siansa.ui.showFastScroll
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The quick-scroll handle on every long list: liked songs, playlists, albums, Home's full lists (beta testers #24). */
class LongListFastScrollTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val now = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val song = Song(id = "1", title = "Émergence", artist = "Sleep Token", album = "Even in Arcadia")

    @Test fun onlyLongListsGetTheHandle() {
        assertFalse(showFastScroll(AlphabetIndex.MIN_ITEMS - 1))
        assertTrue(showFastScroll(AlphabetIndex.MIN_ITEMS))
    }

    @Test fun theLabelFollowsTheSortShown() {
        assertEquals("E", FastScroll.songLabel(ListSort.TITLE, song, null, null, now, zone))
        assertEquals("S", FastScroll.songLabel(ListSort.ARTIST, song, null, null, now, zone))
        assertEquals("Mar 2025", FastScroll.songLabel(ListSort.ADDED, song, SongDates(created = "2025-03-14T10:00:00Z"), null, now, zone))
        assertEquals("No date", FastScroll.songLabel(ListSort.ADDED, song, null, null, now, zone))
        // Last played: the later of the server's date and this phone's.
        val heard = ZonedDateTime.of(2026, 10, 2, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("This month", FastScroll.songLabel(ListSort.PLAYED, song, SongDates(played = "2024-01-01T00:00:00Z"), heard, now, zone))
        assertEquals("Not played", FastScroll.songLabel(ListSort.PLAYED, song, null, null, now, zone))
        // The list's own order is labelled by the page.
        assertNull(FastScroll.songLabel(ListSort.DEFAULT, song, null, null, now, zone))
    }

    @Test fun positionsSkipTheHeaderAndDiscHeadings() {
        assertArrayEquals(intArrayOf(1, 2, 3), FastScroll.positions(3))
        // Disc 2 starts at the third song: its heading takes a row.
        assertArrayEquals(intArrayOf(2, 3, 5, 6), FastScroll.positions(4, headers = 1, headed = setOf(0, 2)))
        assertArrayEquals(intArrayOf(), FastScroll.positions(0))
    }

    @Test fun theRowAtTheTopIsFoundFromItsPosition() {
        val positions = intArrayOf(2, 3, 5, 6)
        assertEquals(0, FastScroll.itemAt(positions, 0))
        assertEquals(0, FastScroll.itemAt(positions, 2))
        assertEquals(1, FastScroll.itemAt(positions, 3))
        // A disc heading belongs with the song above it until its own song shows.
        assertEquals(1, FastScroll.itemAt(positions, 4))
        assertEquals(3, FastScroll.itemAt(positions, 40))
        assertEquals(0, FastScroll.itemAt(intArrayOf(), 5))
    }

    @Test fun lettersOnlyWhenTheListRunsAToZ() {
        assertTrue(FastScroll.inLetterOrder(listOf("Àbba", "beatles", "Coldplay", "Daft Punk")))
        assertFalse(FastScroll.inLetterOrder(listOf("Coldplay", "Abba", "Daft Punk")))
        // One or two letters don't help.
        assertFalse(FastScroll.inLetterOrder(listOf("Abba", "Air", "Beck")))
        assertEquals("C", FastScroll.nameLabel(2, 4, listOf("A", "B", "Coldplay", "D")))
        assertEquals("3 of 40", FastScroll.nameLabel(2, 40, null))
    }
}
