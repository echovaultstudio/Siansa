package app.musix

import app.musix.ui.LARGE_COVER
import app.musix.ui.SMALL_COVER
import app.musix.ui.coverFallbackKeys
import app.musix.ui.coverMemoryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which already-loaded sizes of a cover can stand in while another size loads. */
class CoverStandInTest {
    @Test fun nowPlayingFindsTheListCover() {
        // A list row (48 dp, size 150) is what's in memory when now playing opens from a list.
        val listKey = coverMemoryKey("al-1", 150)
        assertTrue(listKey in coverFallbackKeys("al-1", LARGE_COVER))
        assertTrue(listKey in coverFallbackKeys("al-1", SMALL_COVER))
    }

    @Test fun biggestStandInComesFirst() {
        val keys = coverFallbackKeys("al-1", 150)
        assertEquals(coverMemoryKey("al-1", LARGE_COVER), keys[1])
        assertEquals(coverMemoryKey("al-1", SMALL_COVER), keys[2])
    }

    @Test fun neverItsOwnSize() {
        listOf(null, 80, 150, SMALL_COVER, LARGE_COVER).forEach { size ->
            val keys = coverFallbackKeys("al-1", size)
            assertFalse(coverMemoryKey("al-1", size) in keys)
            assertEquals(keys.distinct(), keys)
        }
    }

    @Test fun onlyTheSameCover() {
        assertTrue(coverFallbackKeys("al-1", LARGE_COVER).all { it.startsWith("cover:al-1:") })
    }

    @Test fun smallSizesShareOneEntry() {
        // 80 px avatars and 150 px rows decode the same way, so neither stands in for the other.
        assertEquals(coverMemoryKey("al-1", 80), coverMemoryKey("al-1", 150))
        assertFalse(coverMemoryKey("al-1", 150) in coverFallbackKeys("al-1", 80))
    }
}
