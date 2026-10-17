package app.musix.ui

import app.musix.mix.SuggestedArtist
import app.musix.movedPreamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** "Other artists you might like": the connection map and the reasons on each card. */
class ArtistDiscoveryTest {
    private fun s(name: String, vararg because: String, fans: Int = 0, albums: Int = 0) =
        SuggestedArtist(name, "photo", albums = albums, fans = fans, because = because.toList())

    private val suggestions = listOf(s("Sub Focus", "Chase & Status", "Wilkinson"), s("Dimension", "Chase & Status"), s("Hybrid Minds", "Wilkinson"))
    private val plays = mapOf("Chase & Status" to 120, "Wilkinson" to 30)

    @Test fun yourArtistsOnTheInnerRingSuggestionsOutside() {
        val map = DiscoveryMap.build(suggestions, plays)
        assertEquals(listOf("Chase & Status", "Wilkinson"), map.seeds.map { it.name })
        assertEquals(3, map.suggestions.size)
        map.seeds.forEach { assertEquals(DiscoveryMap.SEED_RING, hypot(it.x - 0.5f, it.y - 0.5f), 0.001f) }
        map.suggestions.forEach { assertEquals(DiscoveryMap.OUTER_RING, hypot(it.x - 0.5f, it.y - 0.5f), 0.001f) }
    }

    @Test fun moreListenedArtistsWeighMore() {
        val map = DiscoveryMap.build(suggestions, plays)
        val chase = map.seeds.first { it.name == "Chase & Status" }
        val wilkinson = map.seeds.first { it.name == "Wilkinson" }
        assertEquals(1f, chase.weight, 0.001f)
        assertTrue(wilkinson.weight < chase.weight)
    }

    @Test fun oneLinePerReason() {
        val map = DiscoveryMap.build(suggestions, plays)
        assertEquals(4, map.edges.size)
        assertTrue(map.edges.any { it.from == "Wilkinson" && it.to == "Sub Focus" })
    }

    @Test fun suggestionsDontOverlap() {
        val map = DiscoveryMap.build((1..12).map { s("A$it", "Only seed") }, mapOf("Only seed" to 5))
        val points = map.suggestions
        for (i in points.indices) for (j in i + 1 until points.size) {
            assertTrue("${points[i].name} and ${points[j].name} overlap", hypot(points[i].x - points[j].x, points[i].y - points[j].y) > 0.1f)
        }
    }

    @Test fun nothingToShow() {
        assertTrue(DiscoveryMap.build(emptyList(), emptyMap()).seeds.isEmpty())
    }

    @Test fun reasonsInPlainWords() {
        val r = DiscoveryMap.reasons(s("Sub Focus", "Chase & Status", "Wilkinson", fans = 1_200_000, albums = 5), plays)
        assertEquals(
            listOf("Similar to Chase & Status and Wilkinson", "You've played Chase & Status 120 times lately", "1.2M people listen to them on Deezer", "5 albums to discover"),
            r,
        )
    }

    @Test fun oldReplayGainPreampMovesToTheEqualiserOnce() {
        assertEquals(3f, movedPreamp(3f, 0f)!!, 0f)
        assertNull(movedPreamp(0f, 0f))
        assertNull(movedPreamp(3f, 2f))
        assertEquals(12f, movedPreamp(20f, 0f)!!, 0f)
    }
}
