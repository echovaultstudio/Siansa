package app.musix

import app.musix.mix.keepInLibrary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Your listening only shows songs and albums that are in your library (issue #65). */
class KeepInLibraryTest {
    private val library = mapOf("a" to 1, "c" to 3, "d" to 4, "f" to 6)

    @Test fun leavesOutWhatIsNotInTheLibraryAndKeepsTheOrder() = runBlocking {
        val kept = keepInLibrary(listOf("a", "b", "c", "d", "e", "f"), 10) { library[it] }
        assertEquals(listOf("a" to 1, "c" to 3, "d" to 4, "f" to 6), kept)
    }

    @Test fun fillsUpToTheLimitFromFurtherDown() = runBlocking {
        val kept = keepInLibrary(listOf("b", "a", "e", "c", "d", "f"), 3) { library[it] }
        assertEquals(listOf("a", "c", "d"), kept.map { it.first })
    }

    @Test fun stopsSearchingOnceItHasEnough() = runBlocking {
        val searched = ArrayList<String>()
        keepInLibrary(listOf("a", "c", "d", "f"), 2) { searched += it; library[it] }
        assertEquals(listOf("a", "c"), searched)
    }

    @Test fun nothingInTheLibraryGivesAnEmptyList() = runBlocking {
        assertEquals(emptyList<Pair<String, Int>>(), keepInLibrary(listOf("x", "y"), 10) { library[it] })
    }
}
