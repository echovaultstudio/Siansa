package app.siansa.tvapp

import app.siansa.data.Album
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Home's rows read again keep what didn't change (issue #953). */
class SteadyRowsTest {
    private fun album(id: String) = Album(id = id, name = "Album $id")
    private fun row(title: String, vararg ids: String) = TvRow(title, ids.map { TvItem.OfAlbum(album(it)) })

    @Test
    fun nothingChangedGivesTheSameList() {
        val shown = listOf(row("Recently added", "1", "2"), row(RANDOM_PICKS, "7", "8"))
        val again = listOf(row("Recently added", "1", "2"), row(RANDOM_PICKS, "3", "4"))
        // Random picks keep their albums, the rest is equal: nothing to draw again.
        assertSame(shown, steadyRows(shown, again))
    }

    @Test
    fun onlyTheRowThatChangedIsNew() {
        val shown = listOf(row("Recently added", "1", "2"), row("Your playlists", "p"))
        val again = listOf(row("Recently added", "9", "1", "2"), row("Your playlists", "p"))
        val kept = steadyRows(shown, again)
        assertNotSame(shown, kept)
        assertEquals(again[0], kept[0])
        assertSame(shown[1], kept[1])
    }

    @Test
    fun theFirstReadIsTakenAsItIs() {
        val first = listOf(row(RANDOM_PICKS, "1"))
        assertSame(first, steadyRows(null, first))
    }
}
