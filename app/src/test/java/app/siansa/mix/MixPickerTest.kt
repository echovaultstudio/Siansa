package app.siansa.mix

import app.siansa.net.ClapHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Today's mix with less bias and more surprise (issue #236). */
class MixPickerTest {
    private fun hits(prefix: String, artists: List<String>, sim: Double = 0.9) =
        artists.mapIndexed { i, a -> ClapHit("$prefix$i", "Song $i", a, null, sim - i * 0.001) }

    private val core = hits("c", List(60) { "Core ${it % 12}" })
    private val surprise = hits("s", List(40) { "New ${it % 20}" }, sim = 0.5)

    private fun picker(
        length: Int = 40,
        surprisePercent: Int = 30,
        leaveOut: Set<String> = emptySet(),
        recent: Set<String> = emptySet(),
        familiar: (String) -> Double = { 0.0 },
        last: Set<String> = emptySet(),
    ) = MixPicker(length, surprisePercent, leaveOut, recent, emptySet(), familiar, last, Random(1))

    @Test fun aShareComesFromTheSurprisePool() {
        val picked = picker().pick(core, surprise)
        assertEquals(40, picked.size)
        assertEquals(12, picked.count { it.id.startsWith("s") }) // 30 % of 40
        assertEquals(picked.size, picked.map { it.id }.toSet().size)
    }

    @Test fun mostPlayedSongsStayOut() {
        val leaveOut = setOf("c0", "c1", "c2", "s0")
        val picked = picker(leaveOut = leaveOut).pick(core, surprise).map { it.id }
        assertTrue(picked.none { it in leaveOut })
    }

    @Test fun oneArtistAtMostAFewTimes() {
        val picked = picker().pick(core, surprise)
        val cap = picker().perArtistCap
        assertEquals(3, cap)
        assertTrue(picked.groupingBy { it.artist }.eachCount().values.all { it <= cap })
    }

    @Test fun artistsYouPlayALotAreKeptOutOfTheSurprise() {
        val familiar = { a: String -> if (a == "New 1" || a == "New 2") 0.8 else 0.0 }
        val picked = picker(familiar = familiar).pick(core, surprise)
        assertFalse(picked.any { it.id.startsWith("s") && it.artist in setOf("New 1", "New 2") })
    }

    @Test fun yesterdaysArtistsAndFavouritesWeighLess() {
        // Two equal songs: the one by yesterday's artist ranks lower over many draws.
        val a = ClapHit("a", "A", "Yesterday", null, 0.8)
        val b = ClapHit("b", "B", "Fresh", null, 0.8)
        var bFirst = 0
        repeat(200) { seed ->
            val p = MixPicker(1, 0, emptySet(), emptySet(), emptySet(), { 0.0 }, setOf("yesterday"), Random(seed))
            if (p.pick(listOf(a, b), emptyList()).single().id == "b") bFirst++
        }
        assertTrue("fresh artist first in $bFirst of 200", bFirst > 150)
    }

    @Test fun recentSongsOnlyWhenNothingElseIsLeft() {
        val recent = core.take(10).mapTo(HashSet()) { it.id }
        val picked = picker(surprisePercent = 0, recent = recent).pick(core, emptyList())
        assertTrue(picked.none { it.id in recent }) // 50 others are enough for 40
        val small = picker(length = 40, surprisePercent = 0, recent = core.mapTo(HashSet()) { it.id }).pick(core.take(20), emptyList())
        assertEquals(20, small.size) // all heard lately, but better than an empty mix
    }

    @Test fun aSmallLibraryStillFillsTheMix() {
        val few = hits("f", List(10) { "Only" })
        assertEquals(10, picker(length = 10, surprisePercent = 0).pick(few, emptyList()).size)
    }

    @Test fun genericOrRepeatedTitlesAreRejected() {
        assertTrue(MixTitles.problem("Unwind with mellow electronic beats", emptyList())!!.contains("unwind"))
        assertTrue(MixTitles.problem("Late night neon drive", listOf("Neon drive home"))!!.contains("neon drive"))
        assertNull(MixTitles.problem("Coffee before the storm", listOf("Neon puddles after midnight")))
        assertNull(MixTitles.problem("Rain on the tin roof", listOf("The roof garden")))
    }
}
