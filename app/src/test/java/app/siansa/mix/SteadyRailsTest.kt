package app.siansa.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A Steady vibes mix stays steady (the owner, 12 Nov): on its rails only songs near where the mix started may play,
 * and Surprises loosens them a place at a time.
 */
class SteadyRailsTest {
    private fun song(id: String, tempo: Double?, energy: Double? = 0.4, likeness: Double = 0.6, key: String? = "C#", scale: String? = "minor") =
        DjTrack(id, "Artist $id", likeness, tempo, key, scale, energy)

    /** The owner's mix of 11 Nov: "She Loves Me" (133 BPM, mellow) and what its radio had to pick from. */
    private val start = song("she-loves-me", 133.0, energy = 0.40, likeness = 1.0)
    private val pool = listOf(
        song("of-joy", 136.0, 0.45, 0.9),
        song("house-a", 128.0, 0.35, 0.5),
        song("house-b", 131.0, 0.5, 0.45),
        song("house-c", 138.0, 0.3, 0.4),
        song("house-d", 126.5, 0.55, 0.35),
        song("house-e", 140.0, 0.42, 0.3),
        song("too-lively", 134.0, 0.95, 0.85),
        song("water-and-fire", 174.0, 0.5, 0.95),
        song("my-world", 172.0, 0.67, 0.9),
        song("feel-so-high", 175.0, 0.7, 0.88),
        song("slow-down", 174.0, 0.6, 0.86),
        song("downtempo", 66.5, 0.38, 0.8),
    )

    private fun none() = SteadyRails.of(0.0)
    private fun ids(surprise: Double, count: Int = 8) = djOrder(start, pool, count, MixShape.STEADY, surprise, Random(1), start = start).map { it.id }

    @Test fun withNoSurprisesItNeverLeavesTheStartsTempoOrEnergy() {
        val picked = ids(0.0)
        // Before: Of Joy, then Water & Fire at 174, and drum and bass from there on.
        assertEquals(setOf("of-joy", "house-a", "house-b", "house-c", "house-d", "house-e"), picked.toSet())
        assertEquals("of-joy", picked.first())
        // Half time isn't the same tempo in a steady mix: 66.5 is 133's half, and stays out.
        assertFalse("downtempo" in picked)
        // Fewer than asked for come back when the rest are too far off.
        assertEquals(6, picked.size)
    }

    @Test fun eachPlaceOfSurprisesLoosensTheRails() {
        val few = SteadyRails.of(0.25)
        val some = SteadyRails.of(0.55)
        val lots = SteadyRails.of(0.85)
        val at145 = song("x", 145.0)  // 9 % up
        assertFalse(none().allows(start, at145))
        assertTrue(few.allows(start, at145))
        val at152 = song("x", 152.0)  // 14 % up
        assertFalse(few.allows(start, at152))
        assertTrue(some.allows(start, at152))
        // Half and double time come back at Some; any tempo at Lots.
        val half = song("x", 66.5)
        assertFalse(few.allows(start, half))
        assertTrue(some.allows(start, half))
        assertFalse(some.allows(start, song("x", 174.0)))
        assertTrue(lots.allows(start, song("x", 174.0, energy = 1.0)))
        // Energy: near the start at None, further at each place.
        assertFalse(none().allows(start, song("x", 133.0, energy = 0.65)))
        assertTrue(few.allows(start, song("x", 133.0, energy = 0.65)))
        assertFalse(few.allows(start, song("x", 133.0, energy = 0.9)))
        assertTrue(some.allows(start, song("x", 133.0, energy = 0.9)))
        // The places are the queue's: 0, 25, 55 and 85 %.
        assertEquals(listOf(0.06, 0.10, 0.15, null), listOf(0.0, 0.25, 0.55, 0.85).map { SteadyRails.of(it).tempo })
    }

    @Test fun aSongWithNoTempoOrEnergyKnown() {
        // Tempo unknown: only where tempo is free. Energy unknown: never held against it.
        assertFalse(none().allows(start, song("x", null)))
        assertTrue(SteadyRails.of(0.85).allows(start, song("x", null)))
        assertTrue(none().allows(start, song("x", 133.0, energy = null)))
        // A start whose tempo isn't known has no tempo rail.
        assertTrue(none().allows(song("s", null), song("x", 174.0)))
    }

    @Test fun itIsHeldToTheStartNotToItsLastSong() {
        // Step by step, each within 6 % of the one before, a mix could walk from 133 to 150. Not on the rails.
        val near = listOf(song("a", 139.0), song("d", 135.0), song("e", 130.0), song("f", 128.0), song("g", 137.0), song("h", 132.0))
        val steps = near + listOf(song("b", 145.0), song("c", 150.0))
        val picked = djOrder(start, steps, 8, MixShape.STEADY, 0.0, start = start).map { it.id }
        assertEquals(near.map { it.id }.toSet(), picked.toSet())
        // Topped up later, after a song at the rails' edge (139): 145 is within 6 % of it, but not of the start.
        val later = djOrder(song("a", 139.0), steps, 8, MixShape.STEADY, 0.0, start = start).map { it.id }
        assertEquals(setOf("d", "e", "f", "g", "h"), later.toSet())
        // And the start's tempo counts in the order too: from 139, the song at the start's own tempo beats one at 139's.
        val two = listOf(song("at-start", 133.0, likeness = 0.5), song("at-last", 140.5, likeness = 0.5))
        assertEquals("at-start", djOrder(song("a", 139.0), two, 1, MixShape.STEADY, 0.0, start = start).single().id)
    }

    @Test fun whenTooFewFitTheNearestOutsideCarryTheMixOn() {
        val far = listOf(song("a", 150.0), song("b", 174.0), song("c", 143.0), song("d", 100.0))
        val (allowed, outside) = onRails(start, far, 20, none())
        assertEquals(4, allowed.size)
        assertEquals(4, outside)
        // Six are asked for but only five are let in at most, the nearest first; with enough on the rails, none.
        val many = (1..9).map { song("far$it", 150.0 + it) }
        assertEquals(listOf("far1", "far2", "far3", "far4", "far5"), onRails(start, many, 20, none()).first.map { it.id })
        assertEquals(0, onRails(start, pool, 20, none()).second)
        // Asked for two: two are enough.
        assertEquals(2, onRails(start, many, 2, none()).first.size)
    }

    @Test fun onlySteadyWithAStartIsOnRails() {
        // Another shape, or an album's own mix (no start): every song may be picked, as before.
        assertEquals(pool.size, djOrder(start, pool, 20, MixShape.BUILD, 0.0, start = start).size)
        assertEquals(pool.size, djOrder(start, pool, 20, MixShape.STEADY, 0.0).size)
        assertEquals(pool.size, djOrder(start, pool, 20, MixShape.STEADY, 0.85, Random(1), start = start).size)
    }

    @Test fun halfAndDoubleTimeAreTheSameTempoOnlyWhereAllowed() {
        assertEquals(1.0, tempoMatch(87.0, 174.0), 1e-9)
        assertEquals(0.0, tempoMatch(87.0, 174.0, halfDouble = false), 1e-9)
        assertEquals(1.0, tempoMatch(133.0, 133.0, halfDouble = false), 1e-9)
    }

    @Test fun aLiftInKeyCountsOnlyWhereTheRailsSaySo() {
        val a = camelot("A", "minor")!!  // 8A
        val up = camelot("B", "minor")!!  // 10A: a whole tone up
        assertEquals(0.5, keyMatch(a, up), 1e-9)
        assertEquals(0.6, keyMatch(a, up, lift = true), 1e-9)
        assertEquals(0.6, keyMatch(a, camelot("A#", "minor"), lift = true), 1e-9)  // a semitone up
        assertEquals(0.1, keyMatch(a, camelot("G#", "minor"), lift = true), 1e-9)  // down isn't a lift
        assertEquals(1.0, keyMatch(a, a, lift = true), 1e-9)
    }

    @Test fun halftimeDrumAndBassCountsAsDoubleInAMix() {
        fun g(vararg genres: Pair<String, Double>) = genres.map { SongGenre(it.first, it.second) }
        // Alix Perez "Self Control": read at 85, clearly drum and bass and halftime.
        assertEquals(170.36, mixTempo(85.18, g("Drum n Bass" to 0.75, "Halftime" to 0.71))!!, 1e-9)
        assertEquals(174.6, mixTempo(87.3, g("halftime" to 0.77))!!, 1e-9)
        // Aaliyah "More Than a Woman": a trace of drum and bass only. It really is that slow.
        assertEquals(86.99, mixTempo(86.99, g("Drum n Bass" to 0.27, "RnB/Swing" to 0.17))!!, 1e-9)
        // Outside the halftime range, another genre, or genres unknown: as read.
        assertEquals(120.0, mixTempo(120.0, g("Drum n Bass" to 0.9))!!, 1e-9)
        assertEquals(88.0, mixTempo(88.0, g("Hip Hop" to 0.9))!!, 1e-9)
        assertEquals(88.0, mixTempo(88.0, null)!!, 1e-9)
        assertEquals(null, mixTempo(null, g("Drum n Bass" to 0.9)))
    }
}
