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

    /**
     * Issue #786, from the owner's log: a DJ radio from an 89 BPM song in A# minor at energy 0.29, with these seven
     * queued. Whatever the shape, the next song stayed "89 D# e0.71": among the seven it's the only one at a tempo
     * that mixes, so reordering them could never change it. A reshape now also looks at a fresh lot like the song
     * playing; with calm and bright songs near 89 BPM among them, the next song follows the shape.
     */
    private val logSeed = DjTrack("seed", "Seed", 1.0, 89.0, "A#", "minor", energy = 0.29)
    private val logQueued = listOf(
        DjTrack("q1", "A", 0.8, 89.0, "D#", "minor", energy = 0.71),
        DjTrack("q2", "B", 0.8, 94.0, "A#", "minor", energy = 0.86),
        DjTrack("q3", "C", 0.8, 94.0, "A", "minor", energy = 0.14),
        DjTrack("q4", "D", 0.8, 125.0, "F#", "minor", energy = 0.43),
        DjTrack("q5", "E", 0.8, 156.0, "F#", "minor", energy = 0.57),
        DjTrack("q6", "F", 0.8, 156.0, "C", "minor", energy = 1.0),
        DjTrack("q7", "G", 0.8, 117.0, "A#", "minor", energy = 0.0),
    )
    private val fresh = listOf(
        DjTrack("calm", "H", 0.8, 90.0, "A#", "minor", energy = 0.05),
        DjTrack("bright", "I", 0.8, 88.0, "F", "minor", energy = 0.95, lift = 0.9),
    )

    @Test fun theQueuedSevenAloneCouldNeverMoveTheNextSong() {
        for (shape in MixShape.entries) assertEquals(shape.name, "q1", djOrder(logSeed, logQueued, 7, shape).first().id)
    }

    @Test fun withAFreshLotTheNextSongFollowsTheShape() {
        val pool = logQueued + fresh
        assertEquals("calm", djOrder(logSeed, pool, 7, MixShape.WIND_DOWN).first().id)
        // Euphoric climbs over a few songs: the first goes up, and the bright one comes early.
        val up = djOrder(logSeed, pool, 7, MixShape.EUPHORIC)
        assertTrue(up.map { it.id }.toString(), up.first().energy!! > logSeed.energy!!)
        assertTrue(up.map { it.id }.toString(), up.take(3).any { it.id == "bright" })
        // Steady stays as close as can be: the queued song at the same tempo and a neighbouring key.
        assertEquals("q1", djOrder(logSeed, pool, 7, MixShape.STEADY).first().id)
        // As many as were queued; the ones that don't fit are left out.
        assertEquals(7, djOrder(logSeed, pool, 7, MixShape.WIND_DOWN).size)
    }

    @Test fun aReshapeRanksBySoundNotByQueuePlace() {
        // AudioMuse's list of songs like the one playing, the most alike first.
        val like = reshapeLikeness(listOf("calm", "q3", "q1", "bright"))
        assertEquals(1.0, like("calm"), 1e-9)
        assertEquals(0.75, like("q3"), 1e-9)
        assertTrue(like("q1") > like("bright"))
        // A queued song AudioMuse didn't list: neither first nor last on that alone.
        assertEquals(NOT_LISTED_LIKENESS, like("q7"), 1e-9)
        // Songs left out of the queue may come back in a later top-up.
        assertEquals(listOf("q5", "q6"), reshapeLeftOut(listOf("q1", "q5", "q6", "q7"), listOf("calm", "q1", "q7")))
    }

    @Test fun theSurpriseSliderSaysWhatItMeans() {
        assertEquals("None", surpriseLabel(0))
        assertEquals("A few", surpriseLabel(20))
        assertEquals("Some", surpriseLabel(50))
        assertEquals("Lots", surpriseLabel(100))
    }
}
