package app.siansa.tvapp

import app.siansa.data.Album
import app.siansa.data.AlbumCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** The TV's artist page shows a row for each release type, as the phone does (issue #982). */
class TvReleaseRowsTest {
    @Test
    fun aRowForEachTypeInThePhonesOrderNewestFirst() {
        val albums = listOf(
            Album(id = "mix", name = "Mix", year = 2020, releaseTypes = listOf("album", "dj-mix")),
            Album(id = "single", name = "Single", year = 2021, releaseTypes = listOf("single")),
            Album(id = "old", name = "Old", year = 2001, releaseTypes = listOf("album")),
            Album(id = "new", name = "New", year = 2024, releaseTypes = listOf("album")),
        )
        val rows = tvReleaseRows(albums)
        assertEquals(listOf(AlbumCategory.ALBUM, AlbumCategory.SINGLE, AlbumCategory.DJMIX), rows.map { it.first })
        assertEquals(listOf("new", "old"), rows.first().second.map { it.id })
    }

    @Test
    fun noneWithoutReleases() {
        assertEquals(emptyList<Any>(), tvReleaseRows(emptyList()))
    }
}
