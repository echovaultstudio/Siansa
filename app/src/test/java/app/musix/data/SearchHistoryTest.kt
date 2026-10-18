package app.musix.data

import app.musix.data.SearchHistory.Companion.withSearch
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #177: recent searches, newest first, without repeats. */
class SearchHistoryTest {
    @Test fun newestFirst() {
        val list = withSearch(withSearch(emptyList(), "bonobo", false), "alt-j", false)
        assertEquals(listOf("alt-j", "bonobo"), list.map { it.query })
    }

    @Test fun sameWordsMoveToTheTop() {
        val list = listOf(RecentSearch("alt-j"), RecentSearch("Bonobo"), RecentSearch("vance joy"))
        assertEquals(listOf("bonobo", "alt-j", "vance joy"), withSearch(list, "  bonobo ", false).map { it.query })
    }

    @Test fun soundAndLibrarySearchesAreKeptApart() {
        val list = withSearch(listOf(RecentSearch("rainy jazz", sound = false)), "rainy jazz", sound = true)
        assertEquals(listOf(RecentSearch("rainy jazz", true), RecentSearch("rainy jazz", false)), list)
    }

    @Test fun tooShortIsNotKept() {
        val list = listOf(RecentSearch("alt-j"))
        assertEquals(list, withSearch(list, " a ", false))
        assertEquals(list, withSearch(list, "", false))
    }

    @Test fun spacesAreTidied() {
        assertEquals("red hot chili", withSearch(emptyList(), "  red   hot\tchili ", false).single().query)
    }

    @Test fun onlyTheNewestAreKept() {
        var list = emptyList<RecentSearch>()
        for (i in 1..15) list = withSearch(list, "search $i", false)
        assertEquals(SearchHistory.MAX, list.size)
        assertEquals("search 15", list.first().query)
        assertEquals("search 6", list.last().query)
    }
}
