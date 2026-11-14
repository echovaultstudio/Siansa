package app.siansa.tvapp

import app.siansa.data.Album
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Home's rows are there at once when Home is come back to (issue #1074). */
class HomeRowsKeptTest {
    private val rows = listOf(TvRow("Recently added", listOf(TvItem.OfAlbum(Album(id = "1", name = "Album 1")))))

    @Test
    fun nothingKeptAtFirst() {
        assertNull(HomeRowsKept().of(Any()))
    }

    @Test
    fun theRowsShownLastComeBack() {
        val kept = HomeRowsKept()
        val session = Any()
        kept.keep(session, rows)
        assertSame(rows, kept.of(session))
    }

    @Test
    fun anotherSessionSeesNothingOfThem() {
        val kept = HomeRowsKept()
        kept.keep(Any(), rows)
        assertNull(kept.of(Any()))
    }
}
