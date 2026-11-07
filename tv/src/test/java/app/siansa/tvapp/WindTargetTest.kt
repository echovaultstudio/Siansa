package app.siansa.tvapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Winding on the TV's now playing stays inside the song (issue #984). */
class WindTargetTest {
    @Test
    fun staysInsideTheSong() {
        assertEquals(40_000L, windTarget(30_000, 10_000, 200_000))
        assertEquals(0L, windTarget(5_000, -10_000, 200_000))
        // Short of the end, so letting go doesn't skip to the next song.
        assertEquals(199_000L, windTarget(195_000, 10_000, 200_000))
        // A stream with no length yet: forward as far as asked.
        assertEquals(15_000L, windTarget(5_000, 10_000, 0))
    }
}
