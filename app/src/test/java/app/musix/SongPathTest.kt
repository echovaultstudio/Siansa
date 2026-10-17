package app.musix

import app.musix.mix.songPathIds
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order of a song path's songs (issue #63). */
class SongPathTest {
    @Test fun startsAndEndsWithTheChosenSongs() {
        assertEquals(listOf("a", "x", "y", "b"), songPathIds("a", "b", listOf("a", "x", "y", "b"), 10))
    }

    @Test fun addsTheEndsWhenAudioMuseLeavesThemOut() {
        assertEquals(listOf("a", "x", "y", "b"), songPathIds("a", "b", listOf("x", "y"), 10))
    }

    @Test fun eachSongOnce() {
        assertEquals(listOf("a", "x", "y", "b"), songPathIds("a", "b", listOf("a", "x", "x", "b", "y", "a"), 10))
    }

    @Test fun longPathsAreThinnedEvenlyToTheChosenLength() {
        val steps = (1..18).map { "s$it" }
        val ids = songPathIds("a", "b", steps, 10)
        assertEquals(10, ids.size)
        assertEquals("a", ids.first())
        assertEquals("b", ids.last())
        assertEquals(listOf("s1", "s3", "s5", "s7", "s10", "s12", "s14", "s16"), ids.subList(1, 9))
    }

    @Test fun noStepsGivesJustTheTwoSongs() {
        assertEquals(listOf("a", "b"), songPathIds("a", "b", emptyList(), 20))
    }
}
