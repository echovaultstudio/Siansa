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
    private val house = mapOf("house" to 0.6)
    private val techno = mapOf("techno" to 1.44)

    private fun song(
        id: String, tempo: Double?, level: Double? = 0.10, likeness: Double = 0.6, families: Map<String, Double> = house,
        key: String? = "C#", scale: String? = "minor",
    ) = DjTrack(id, "Artist $id", likeness, tempo, key, scale, energy = 0.5, level = level, families = families)

    /** The owner's mixes of 11 and 12 Nov: a mellow house song at 133 BPM, and what its radio had to pick from. */
    private val start = song("she-loves-me", 133.0, likeness = 1.0)
    private val fits = listOf(
        song("of-joy", 136.0, 0.11, 0.9),
        song("house-a", 128.0, 0.09, 0.8),
        song("house-b", 131.0, 0.12, 0.75),
        song("house-c", 138.0, 0.085, 0.7),
        song("house-d", 126.5, 0.10, 0.65),
        song("house-e", 140.0, 0.105, 0.6),
    )
    private val pool = fits + listOf(
        song("too-lively", 134.0, 0.20, 0.85),
        // 11 Nov: drum and bass two songs in, and from there on.
        song("water-and-fire", 174.0, 0.11, 0.95, mapOf("drum and bass" to 0.9)),
        song("my-world", 172.0, 0.12, 0.9, mapOf("drum and bass" to 0.8)),
        song("downtempo", 66.5, 0.10, 0.8),
        // 12 Nov: the right tempo and energy, and alike by AudioMuse, but minimal techno after melodic house.
        song("limits-of-real", 133.0, 0.10, 0.70, techno),
        song("vert", 132.3, 0.11, 0.62, mapOf("techno" to 1.81)),
        // 12 Nov: of the wider lot, one of the least alike, picked only because its tempo fit.
        song("least-alike", 132.0, 0.10, 0.03),
    )

    private fun none() = SteadyRails.of(0.0)
    private fun few() = SteadyRails.of(0.25)
    private fun some() = SteadyRails.of(0.55)
    private fun lots() = SteadyRails.of(0.85)
    private fun ids(surprise: Double, count: Int = 8) = djOrder(start, pool, count, MixShape.STEADY, surprise, Random(1), start = start).map { it.id }

    @Test fun withNoSurprisesItStaysWhereTheMixStarted() {
        val picked = ids(0.0)
        assertEquals(fits.map { it.id }.toSet(), picked.toSet())
        assertEquals("of-joy", picked.first())
        // Fewer than asked for come back when the rest are too far off: a shorter mix, not one that drifts.
        assertEquals(6, picked.size)
        // Each of the others, and why it's out.
        assertFalse("half time isn't the same tempo in a steady mix", none().allows(start, pool.first { it.id == "downtempo" }))
        assertFalse("drum and bass at 174", none().allows(start, pool.first { it.id == "water-and-fire" }))
        assertFalse("twice the energy", none().allows(start, pool.first { it.id == "too-lively" }))
        assertFalse("techno after house", none().allows(start, pool.first { it.id == "limits-of-real" }))
        assertFalse("techno after house", none().allows(start, pool.first { it.id == "vert" }))
        assertFalse("it doesn't sound alike", none().allows(start, pool.first { it.id == "least-alike" }))
    }

    @Test fun eachPlaceOfSurprisesLoosensTheRails() {
        // Tempo.
        val at145 = song("x", 145.0)  // 9 % up
        assertFalse(none().allows(start, at145))
        assertTrue(few().allows(start, at145))
        val at152 = song("x", 152.0)  // 14 % up
        assertFalse(few().allows(start, at152))
        assertTrue(some().allows(start, at152))
        // Half and double time come back at Some; any tempo at Lots.
        val half = song("x", 66.5)
        assertFalse(few().allows(start, half))
        assertTrue(some().allows(start, half))
        assertFalse(some().allows(start, song("x", 174.0)))
        assertTrue(lots().allows(start, song("x", 174.0, level = 0.9, likeness = 0.0, families = techno)))
        // Energy, on AudioMuse's own scale: a quarter more or less at None, further at each place.
        assertTrue(none().allows(start, song("x", 133.0, level = 0.12)))
        assertTrue(none().allows(start, song("x", 133.0, level = 0.081)))
        assertFalse(none().allows(start, song("x", 133.0, level = 0.13)))
        assertTrue(few().allows(start, song("x", 133.0, level = 0.13)))
        assertFalse(few().allows(start, song("x", 133.0, level = 0.16)))
        assertTrue(some().allows(start, song("x", 133.0, level = 0.16)))
        // How alike it sounds: the most alike half at None.
        assertFalse(none().allows(start, song("x", 133.0, likeness = 0.4)))
        assertTrue(few().allows(start, song("x", 133.0, likeness = 0.4)))
        assertFalse(few().allows(start, song("x", 133.0, likeness = 0.25)))
        assertTrue(some().allows(start, song("x", 133.0, likeness = 0.25)))
        // The kind of music: the start's at None; at A few, one that shares a kind with the start; any from Some.
        val technoWithHouse = song("x", 133.0, families = mapOf("techno" to 0.6, "house" to 0.2))
        assertFalse(none().allows(start, technoWithHouse))
        assertTrue(few().allows(start, technoWithHouse))
        assertFalse(few().allows(start, song("x", 133.0, families = techno)))
        assertTrue(some().allows(start, song("x", 133.0, families = techno)))
        // The places are the queue's: 0, 25, 55 and 85 %.
        assertEquals(listOf(0.06, 0.10, 0.15, null), listOf(0.0, 0.25, 0.55, 0.85).map { SteadyRails.of(it).tempo })
    }

    @Test fun whatIsNotKnownIsNotHeldAgainstASong() {
        // Tempo unknown: only where tempo is free.
        assertFalse(none().allows(start, song("x", null)))
        assertTrue(lots().allows(start, song("x", null)))
        // Energy or the kind of music unknown (no Siansa Beats genres), on either side: allowed.
        assertTrue(none().allows(start, song("x", 133.0, level = null)))
        assertTrue(none().allows(start, song("x", 133.0, families = emptyMap())))
        assertTrue(none().allows(song("s", 133.0, families = emptyMap()), song("x", 133.0, families = techno)))
        // A song that's a bit of everything (nothing at half or more) isn't clearly another kind.
        assertTrue(none().allows(start, song("x", 133.0, families = mapOf("techno" to 0.3, "trance" to 0.2))))
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
        val two = listOf(song("at-start", 133.0), song("at-last", 140.5))
        assertEquals("at-start", djOrder(song("a", 139.0), two, 1, MixShape.STEADY, 0.0, start = start).single().id)
        // So does its energy, on AudioMuse's own scale: of two at the same tempo, the one nearer the start's.
        val levels = listOf(song("livelier", 133.0, level = 0.12), song("as-start", 133.0, level = 0.10))
        assertEquals("as-start", djOrder(song("a", 139.0), levels, 1, MixShape.STEADY, 0.0, start = start).single().id)
    }

    @Test fun whenTooFewFitTheNearestOutsideCarryTheMixOnAndAnotherKindComesLast() {
        val far = listOf(song("a", 150.0), song("techno", 133.0, families = techno), song("c", 143.0), song("d", 100.0))
        val (allowed, outside) = onRails(start, far, 20, none())
        assertEquals(4, allowed.size)
        assertEquals(4, outside)
        // The nearest in tempo first; techno at the very tempo is still last: it's another kind of music.
        assertEquals(listOf("c", "a", "d", "techno"), allowed.map { it.id })
        // Five are let in at most; with enough on the rails, none.
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

    @Test fun energyIsComparedOnItsOwnScale() {
        assertEquals(0.25, energyOff(0.10, 0.125)!!, 1e-9)
        assertEquals(0.25, energyOff(0.125, 0.10)!!, 1e-9)  // the same either way round
        assertEquals(0.0, energyOff(0.3, 0.3)!!, 1e-9)
        assertEquals(null, energyOff(null, 0.1))
        assertEquals(null, energyOff(0.1, 0.0))
    }

    @Test fun theKindOfMusicASongIs() {
        fun g(style: String, score: Double, genre: String = "Electronic") = SongGenre(style, score, genre, style)
        // The owner's songs of 12 Nov, as Siansa Beats hears them.
        val everythingU = soundFamilies(listOf(g("Bassline", 0.15)))
        val limitsOfReal = soundFamilies(listOf(g("Techno", 0.84), g("Deep Techno", 0.43), g("Minimal Techno", 0.17)))
        assertEquals(mapOf("house" to 0.15), everythingU)
        assertEquals(setOf("techno"), limitsOfReal.keys)
        assertEquals(1.44, limitsOfReal.getValue("techno"), 1e-9)
        assertEquals("techno", mainFamily(limitsOfReal))
        assertEquals(null, mainFamily(everythingU))  // Siansa Beats isn't sure of it; what it is still counts as the start's kind
        assertFalse(none().sameKind(song("s", 132.0, families = everythingU), song("x", 133.0, families = limitsOfReal)))
        assertTrue(none().sameKind(song("s", 132.0, families = everythingU), song("x", 126.0, families = soundFamilies(listOf(g("Deep House", 0.6), g("Tech House", 0.2))))))
        // Electronic's styles are grouped; any other genre is its own kind; a style not listed is no kind.
        assertEquals("hard dance", soundFamily(g("Hardcore", 0.5)))
        assertEquals("rock", soundFamily(g("Hardcore", 0.5, genre = "Rock")))
        assertEquals("hip hop", soundFamily(g("Trap", 0.5, genre = "Hip Hop")))
        assertEquals(null, soundFamily(g("Experimental", 0.5)))
        assertEquals("drum and bass", soundFamily(g("Halftime", 0.7)))
        // An older Siansa Beats names the style only.
        assertEquals("trance", soundFamily(SongGenre("Tech Trance", 0.4)))
        assertTrue(soundFamilies(null).isEmpty())
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

    private fun measured(id: String, level: Double, from: String?, tempo: Double = 124.0, likeness: Double = 0.9) =
        DjTrack(id, tempo = tempo, likeness = likeness, level = level, levelFrom = from)

    @Test
    fun `energies measured by different services aren't held against a song`() {
        // The owner's library, 12 Nov: alike house songs at 0.714 (heard by Siansa Beats) and 0.29 (taken over).
        val rails = SteadyRails.of(0.0)
        val start = measured("start", 0.714, "beats")
        assertEquals(null, energyOff(start, measured("taken over", 0.29, "audiomuse")))
        assertTrue(rails.allows(start, measured("taken over", 0.29, "audiomuse")))
        // Within one service the rail holds as before.
        assertEquals(false, rails.allows(start, measured("quiet", 0.29, "beats")))
        assertTrue(rails.allows(start, measured("alike", 0.74, "beats")))
        // An older Siansa Beats says nothing about who measured: compared as before.
        assertEquals(false, rails.allows(measured("start", 0.714, null), measured("quiet", 0.29, null)))
    }

    @Test
    fun `the log can say which rail kept a song out`() {
        val rails = SteadyRails.of(0.0)
        val start = measured("start", 0.7, "beats", tempo = 132.0)
        assertEquals(emptyList<String>(), rails.keptOutBy(start, measured("fits", 0.7, "beats", tempo = 130.0)))
        assertEquals(listOf("tempo"), rails.keptOutBy(start, measured("slow", 0.7, "beats", tempo = 121.0)))
        assertEquals(listOf("tempo", "energy", "likeness"), rails.keptOutBy(start, measured("far", 0.2, "beats", tempo = 100.0, likeness = 0.1)))
    }

    @Test
    fun `energy is ranked among the songs one service measured`() {
        val ranked = energyByRank(
            mapOf("a" to 0.60, "b" to 0.74, "c" to 0.27, "d" to 0.31, "e" to null),
            mapOf("a" to "beats", "b" to "beats", "c" to "audiomuse", "d" to "audiomuse"),
        )
        assertEquals(0.0, ranked["a"]!!, 1e-9)
        assertEquals(1.0, ranked["b"]!!, 1e-9)
        assertEquals(0.0, ranked["c"]!!, 1e-9)
        assertEquals(1.0, ranked["d"]!!, 1e-9)  // the loudest of its own, not under every song of the other
        assertEquals(null, ranked["e"])
    }

    @Test
    fun `with Siansa Beats' arousal a steady mix stays within half a point of the start`() {
        // The owner's trial, 12 Nov: "In My Head" 6.4, "Bad Kingdom" 5.0 (louder, but he hears it as calmer).
        val rails = SteadyRails.of(0.0)
        val start = measured("In My Head", 6.4, "arousal", tempo = 175.0)
        assertTrue(rails.allows(start, measured("Free", 6.6, "arousal", tempo = 175.0)))
        assertEquals(false, rails.allows(start, measured("Bad Kingdom", 5.0, "arousal", tempo = 175.0)))
        assertEquals(0.7, energyOff(start, measured("Bad Kingdom", 5.0, "arousal"))!!, 1e-9)
        // "Some" surprises lets it in (within a point and a half).
        assertTrue(SteadyRails.of(0.5).allows(start, measured("Bad Kingdom", 5.0, "arousal", tempo = 175.0)))
        // A song without an arousal isn't held to it.
        assertEquals(null, energyOff(start, measured("old", 0.8, "beats")))
    }
}
