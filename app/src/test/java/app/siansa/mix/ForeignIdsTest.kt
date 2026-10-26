package app.siansa.mix

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AudioMuse that analysed another server: its songs aren't asked for one by one (beta testers #32, #35). */
class ForeignIdsTest {
    @Test fun anotherServersSongsAreNotAllAskedFor() = runBlocking {
        val asked = mutableListOf<String>()
        val ids = (1..150).map { "navidrome-$it" }
        val result = fetchUnlessForeign(ids) { id -> asked += id; null as String? }
        assertTrue(result.foreign)
        assertTrue(result.songs.isEmpty())
        assertEquals(PROBE, asked.size)
    }

    @Test fun thisServersSongsAreAllFetchedInOrder() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e", "b")
        val result = fetchUnlessForeign(ids) { id -> id.uppercase() }
        assertFalse(result.foreign)
        assertEquals(listOf("A", "B", "C", "D", "E"), result.songs)
    }

    @Test fun aFewMissingSongsDontStopTheRest() = runBlocking {
        // The first two were deleted since AudioMuse's analysis; the rest are there.
        val gone = setOf("a", "b")
        val asked = mutableListOf<String>()
        val result = fetchUnlessForeign(listOf("a", "b", "c", "d", "e")) { id ->
            synchronized(asked) { asked += id }
            if (id in gone) null else id
        }
        assertFalse(result.foreign)
        assertEquals(listOf("c", "d", "e"), result.songs)
        assertEquals(5, asked.size)
    }

    @Test fun aShortListIsAlwaysAskedInFull() = runBlocking {
        val result = fetchUnlessForeign(listOf("x", "y", "z")) { null as String? }
        assertFalse(result.foreign)
        assertTrue(result.songs.isEmpty())
    }
}
