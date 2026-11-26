package app.siansa.mix

import app.siansa.mix.SmoothRun.Camelot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smooth run (issue #1389): the same key and tempo rules as Siansa Beats' own. */
class SmoothRunTest {
    @Test fun keysOnTheCamelotWheel() {
        assertEquals(Camelot(8, true), SmoothRun.camelot("A", "minor"))
        assertEquals(Camelot(8, false), SmoothRun.camelot("C", "major"))
        assertEquals(Camelot(9, true), SmoothRun.camelot("E", "minor"))
        assertEquals(Camelot(1, true), SmoothRun.camelot("Ab", "minor"))
        assertEquals(Camelot(12, false), SmoothRun.camelot("E", "major"))
        assertNull(SmoothRun.camelot("H", "minor"))
        assertNull(SmoothRun.camelot("C", null))
    }

    @Test fun movesADjMakesInKey() {
        val am = SmoothRun.camelot("A", "minor")
        assertEquals(0, SmoothRun.keyStep(am, am))
        assertEquals(1, SmoothRun.keyStep(am, SmoothRun.camelot("E", "minor")))
        assertEquals(1, SmoothRun.keyStep(am, SmoothRun.camelot("C", "major")))
        assertNull(SmoothRun.keyStep(am, SmoothRun.camelot("F#", "major")))
        assertEquals(1, SmoothRun.keyStep(Camelot(12, true), Camelot(1, true)))
    }

    @Test fun halfAndDoubleTimeAreTheSameBeat() {
        assertEquals(0.0, SmoothRun.tempoGap(174.0, 87.0)!!, 1e-9)
        assertEquals(0.05, SmoothRun.tempoGap(120.0, 126.0)!!, 1e-9)
        assertNull(SmoothRun.tempoGap(null, 120.0))
    }

    @Test fun everyStepIsInKeyAndInTempoAndItStopsRatherThanJolt() {
        val keys = listOf("A" to "minor", "E" to "minor", "C" to "major", "G" to "major", "F#" to "major")
        val songs = (0 until 30).map { n ->
            val (k, sc) = keys[n % keys.size]
            SmoothRun.Step("s$n", 120.0 + n % 7, SmoothRun.camelot(k, sc), n / 100.0)
        } + SmoothRun.Step("far", 90.0, SmoothRun.camelot("A", "minor")) + SmoothRun.Step("nokey", 121.0, null)
        val run = SmoothRun.order(songs, 20)
        assertTrue(run.size in SmoothRun.FEWEST..20)
        assertTrue("far" !in run && "nokey" !in run)
        val byId = songs.associateBy { it.id }
        run.zipWithNext { a, b ->
            assertNotNull("$a → $b", SmoothRun.keyStep(byId[a]!!.key, byId[b]!!.key))
            assertTrue("$a → $b", SmoothRun.tempoGap(byId[a]!!.bpm, byId[b]!!.bpm)!! <= SmoothRun.TEMPO_LIMIT)
        }
        assertEquals(run, run.distinct())
        // Nothing to step to: a run of one.
        assertEquals(1, SmoothRun.order(listOf(SmoothRun.Step("x", 120.0, SmoothRun.camelot("A", "minor")), SmoothRun.Step("y", 60.5 * 3, SmoothRun.camelot("F#", "major"))), 10).size)
    }
}
