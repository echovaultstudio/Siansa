package app.siansa.mix

import app.siansa.ui.surpriseLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Shaping a DJ mix (issue #649): the energy arc, surprises, and how the order follows them. */
class MixShapeTest {
    /** Thirty songs, all alike in tempo and key, that differ only in energy (0 to 1 in steps) and brightness. */
    private val pool = (0..29).map { i ->
        DjTrack("s$i", "Artist $i", likeness = 0.8, tempo = 124.0, key = "C", scale = "major", energy = i / 29.0, lift = if (i == 20) 1.0 else 0.2)
    }
    private val seed = DjTrack("seed", "Seed", 1.0, 124.0, "C", "major", energy = 0.4, lift = 0.2)

    @Test fun theShapesSayWhereTheEnergyGoes() {
        assertNull(MixShape.STEADY.target(0.4, 0, 10))
        assertEquals(0.4 + 0.6 * 0.1, MixShape.BUILD.target(0.4, 0, 10)!!, 1e-9)
        assertEquals(1.0, MixShape.BUILD.target(0.4, 9, 10)!!, 1e-9)
        assertEquals(1.0, MixShape.EUPHORIC.target(0.4, 3, 10)!!, 1e-9)  // at the top after a third of the songs
        assertEquals(0.4 + 0.6 * 0.3, MixShape.EUPHORIC.target(0.4, 0, 10)!!, 1e-9)
        assertEquals(0.0, MixShape.WIND_DOWN.target(0.4, 9, 10)!!, 1e-9)
        assertEquals(0.5 * 0.9, MixShape.WIND_DOWN.target(null, 0, 10)!!, 1e-9)  // unknown energy counts as the middle
        assertEquals(MixShape.STEADY, MixShape.parse("nonsense"))
    }

    @Test fun aBuildClimbsAndAWindDownFalls() {
        val build = djOrder(seed, pool, 6, MixShape.BUILD).map { it.energy!! }
        assertTrue(build.toString(), build.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(build.last() > 0.8)
        val down = djOrder(seed, pool, 6, MixShape.WIND_DOWN).map { it.energy!! }
        assertTrue(down.toString(), down.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(down.last() < 0.2)
    }

    @Test fun euphoricGetsHighFastAndStaysThereFavouringBrightSongs() {
        val order = djOrder(seed, pool, 6, MixShape.EUPHORIC)
        val energy = order.map { it.energy!! }
        assertTrue(energy.toString(), energy.drop(2).all { it >= 0.6 })  // after two songs it's up and stays up
        assertTrue(order.map { it.id }.toString(), order.indexOfFirst { it.id == "s20" } < 3)  // the bright song comes early
    }

    @Test fun steadyIgnoresTheArcAndKeepsTodaysOrder() {
        val near = DjTrack("near", "B", 0.9, 125.0, "G", "major", energy = 0.1)
        val far = DjTrack("far", "C", 0.9, 100.0, "F#", "major", energy = 0.4)
        assertEquals("near", djOrder(seed, listOf(far, near), 1).single().id)
        assertEquals("near", djOrder(seed, listOf(far, near), 1, MixShape.STEADY, 0.0).single().id)
    }

    @Test fun surprisesLetTheMixStrayButKeepTheKey() {
        val near = DjTrack("near", "B", 0.9, 125.0, "C", "major")
        val stray = DjTrack("stray", "C", 0.3, 112.0, "G", "major")  // a neighbour key, other tempo, less alike
        val clash = DjTrack("clash", "D", 0.3, 112.0, "F#", "major")  // a key that doesn't go
        val pool = listOf(near, stray, clash)
        assertEquals("near", djOrder(seed, pool, 1, surprise = 0.0, random = Random(1)).single().id)
        // With everything to play for, the wild card can put the stray song first, but never the clashing key.
        val picks = (1..40).map { djOrder(seed, pool, 1, surprise = 1.0, random = Random(it)).single().id }
        assertTrue(picks.toString(), "stray" in picks)
        assertTrue(picks.toString(), "clash" !in picks)
        // Scores: a surprise doesn't change the order of two equal songs when the wild card is the same.
        assertEquals(transition(seed, near, 0.5, 0.5), transition(seed, near, 0.5, 0.5), 1e-12)
        assertTrue(transition(seed, stray, 1.0, 1.0) > transition(seed, near, 1.0, 0.0))
    }

    @Test fun energyIsRankedAmongTheSongsOnOffer() {
        val ranked = energyByRank(mapOf("a" to 0.02, "b" to 0.3, "c" to null, "d" to 0.1))
        assertEquals(0.0, ranked["a"]!!, 1e-9)
        assertEquals(0.5, ranked["d"]!!, 1e-9)
        assertEquals(1.0, ranked["b"]!!, 1e-9)
        assertNull(ranked["c"])
        assertEquals(0.5, energyByRank(mapOf("only" to 0.2))["only"]!!, 1e-9)
    }

    @Test fun theSurpriseSliderSaysWhatItMeans() {
        assertEquals("None", surpriseLabel(0))
        assertEquals("A few", surpriseLabel(20))
        assertEquals("Some", surpriseLabel(50))
        assertEquals("Lots", surpriseLabel(100))
    }
}
