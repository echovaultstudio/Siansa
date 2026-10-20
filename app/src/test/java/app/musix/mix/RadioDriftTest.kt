package app.musix.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How far a song radio may wander from its first song (issue #256). */
class RadioDriftTest {
    @Test fun stayCloseAlwaysGoesBackToTheFirstSong() {
        (0..5).forEach { assertEquals(RadioAnchor.SEED, radioAnchor(RadioDrift.CLOSE, it)) }
    }

    @Test fun aLittleAlternatesAndStartsFromTheFirstSong() {
        assertEquals(
            listOf(RadioAnchor.SEED, RadioAnchor.CURRENT_NEAR_SEED, RadioAnchor.SEED, RadioAnchor.CURRENT_NEAR_SEED),
            (0..3).map { radioAnchor(RadioDrift.SOME, it) },
        )
    }

    @Test fun wanderFollowsTheSongPlaying() {
        assertEquals(RadioAnchor.CURRENT, radioAnchor(RadioDrift.FREE, 0))
    }

    @Test fun nearSeedKeepsOnlySongsAlsoLikeTheFirst() {
        val likeCurrent = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        val likeSeed = listOf("h", "g", "f", "e", "d", "c", "x")
        assertEquals(listOf("c", "d", "e", "f", "g"), nearSeed(likeCurrent, likeSeed, setOf("h"), count = 5))
        // Too few left near the first song: null, so the radio goes back to it.
        assertNull(nearSeed(likeCurrent, listOf("a", "b"), emptySet(), count = 5))
    }

    @Test fun unknownOrMissingSettingIsALittle() {
        assertEquals(RadioDrift.SOME, RadioDrift.parse(null))
        assertEquals(RadioDrift.SOME, RadioDrift.parse("nope"))
        assertEquals(RadioDrift.CLOSE, RadioDrift.parse("CLOSE"))
    }
}
