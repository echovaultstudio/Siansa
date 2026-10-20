package app.musix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home carousels of 3 or 4 mixes, set in Vibes (issue #253). */
class CarouselTest {
    private val morning = DefaultCarousels.all.first { it.id == "morning" }

    @Test fun theSuggestedCarouselsCoverEveryHourAndAreValid() {
        (0..23).forEach { h -> assertTrue("hour $h", DefaultCarousels.all.any { it.covers(h) }) }
        DefaultCarousels.all.forEach { assertNull(it.id, carouselProblem(it)) }
        assertEquals(listOf("Wake up", "Caffeine kick", "Mellow morning", "Sunny start"), morning.usable.map { it.name })
    }

    @Test fun nightRunsPastMidnight() {
        val night = DefaultCarousels.all.first { it.id == "night" }
        assertTrue(night.covers(23))
        assertTrue(night.covers(3))
        assertFalse(night.covers(12))
    }

    @Test fun eachMixIsItsOwnVibeAndAChangeMakesNewOnes() {
        val ids = morning.usable.indices.map { morning.selectorFor(it).id }
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(morning.selectorFor(0).id, morning.copy(version = 1).selectorFor(0).id)
        val s = morning.selectorFor(1)
        assertEquals("Caffeine kick", s.name)
        assertEquals("upbeat energetic groove, punchy drums, positive", s.baseQuery)
        assertEquals(Carousel.MIX_LENGTH, s.length)
        assertTrue(s.guidance.startsWith("Your morning: Caffeine kick."))
    }

    @Test fun aCarouselNeedsThreeDifferentMixes() {
        assertEquals("Give the carousel a name.", carouselProblem(morning.copy(title = " ")))
        assertTrue(carouselProblem(morning.copy(mixes = morning.mixes.take(2)))!!.contains("at least 3"))
        // A mix without a sound doesn't count.
        assertTrue(carouselProblem(morning.copy(mixes = morning.mixes.take(2) + CarouselMix("Third", "")))!!.contains("at least 3"))
        val twins = morning.mixes.take(3) + CarouselMix("Wake up", "something else")
        assertEquals("Give each mix its own name.", carouselProblem(morning.copy(mixes = twins)))
        val sameSound = morning.mixes.take(3) + CarouselMix("Fourth", morning.mixes[0].sound.uppercase())
        assertEquals("Give each mix its own sound, so they differ.", carouselProblem(morning.copy(mixes = sameSound)))
        assertNull(carouselProblem(morning.copy(mixes = morning.mixes.take(3))))
    }

    @Test fun atMostFourMixesAreUsed() {
        val five = morning.copy(mixes = morning.mixes + CarouselMix("Fifth", "loud"))
        assertEquals(Carousel.MAX, five.usable.size)
    }
}
