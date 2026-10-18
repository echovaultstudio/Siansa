package app.musix.ui

import app.musix.mix.SuggestedArtist
import app.musix.movedPreamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

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

    // ---- Names and photos on the map never overlap (the map was messy with many suggestions) ----

    private fun overlap(a: DiscoveryMap.Box, b: DiscoveryMap.Box, gap: Float) =
        kotlin.math.abs(a.cx - b.cx) < (a.w + b.w) / 2 + gap - 0.5f && kotlin.math.abs(a.cy - b.cy) < (a.h + b.h) / 2 + gap - 0.5f

    /** The map's boxes as the screen places them before separating: you, your artists, the suggestions. */
    private fun ringBoxes(w: Float, seeds: Int, suggestions: Int): List<DiscoveryMap.Box> = buildList {
        add(DiscoveryMap.Box(w / 2, w / 2, 56f, 56f, fixed = true))
        repeat(seeds) { i ->
            val a = -PI / 2 + 2 * PI * i / seeds
            add(DiscoveryMap.Box((0.5 + 0.24 * cos(a)).toFloat() * w, (0.5 + 0.24 * sin(a)).toFloat() * w + 34f / 2 - 8f, 80f, 34f))
        }
        repeat(suggestions) { i ->
            val a = -PI / 2 + 0.3 + 2 * PI * i / suggestions
            add(DiscoveryMap.Box((0.5 + 0.40 * cos(a)).toFloat() * w, (0.5 + 0.40 * sin(a)).toFloat() * w + 58f / 2 - 20f, 72f, 58f))
        }
    }

    @Test fun asManyAsFitNeverOverlapAndStayInside() {
        for (w in listOf(264f, 280f, 296f, 311f, 312f, 328f, 360f, 392f, 440f)) {
            val (maxSuggestions, maxSeeds) = DiscoveryMap.limits(w)
            for (seeds in 1..maxSeeds) for (suggestions in 1..maxSuggestions) {
                val placed = DiscoveryMap.separate(ringBoxes(w, seeds, suggestions), w, w, 4f)
                for (i in placed.indices) for (j in i + 1 until placed.size) {
                    assertTrue("map $w dp, $seeds of yours, $suggestions suggested: boxes $i and $j overlap", !overlap(placed[i], placed[j], 4f))
                }
                placed.forEach { b -> assertTrue("inside $w: $b", b.cx - b.w / 2 >= -0.01f && b.cx + b.w / 2 <= w + 0.01f && b.cy - b.h / 2 >= -0.01f && b.cy + b.h / 2 <= w + 0.01f) }
            }
        }
    }

    @Test fun fewerOnNarrowPhones() {
        assertEquals(8 to 6, DiscoveryMap.limits(328f))
        assertEquals(6 to 5, DiscoveryMap.limits(288f))
    }

    @Test fun youStayInTheMiddle() {
        val you = DiscoveryMap.Box(180f, 180f, 56f, 56f, fixed = true)
        val placed = DiscoveryMap.separate(listOf(you, DiscoveryMap.Box(185f, 185f, 72f, 58f)), 360f, 360f, 4f)
        assertEquals(you, placed[0])
        assertTrue(!overlap(placed[0], placed[1], 4f))
    }

    @Test fun labelsAlreadyApartDontMove() {
        val boxes = listOf(DiscoveryMap.Box(60f, 60f, 72f, 58f), DiscoveryMap.Box(300f, 300f, 72f, 58f))
        assertEquals(boxes, DiscoveryMap.separate(boxes, 360f, 360f, 4f))
    }

    @Test fun tooManyOfYourArtistsKeepsTheMostPlayed() {
        val many = listOf(s("X", "A", "B", "C"), s("Y", "D", "E"))
        val map = DiscoveryMap.build(many, mapOf("A" to 1, "B" to 50, "C" to 2, "D" to 40, "E" to 30), maxSeeds = 3)
        assertEquals(listOf("B", "D", "E"), map.seeds.map { it.name })
        assertTrue("no lines to artists left out", map.edges.all { it.from in setOf("B", "D", "E") })
    }
}
