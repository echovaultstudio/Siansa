package app.siansa.mix

import org.junit.Assert.assertEquals
import org.junit.Test

/** Whose artists Home's Artist radios are (the owner, 26 Nov: "replace artists radios with ones beats suggest?"). */
class ArtistsFromTest {
    private val mine = (1..10).map { "m$it" }
    private val beats = listOf("b1", "b2", "b3", "m2")

    private fun pick(from: ArtistsFrom, mine: List<String> = this.mine, beats: List<String> = this.beats, count: Int = 10) =
        ArtistsFrom.pick(from, mine, beats, count) { it }

    @Test fun yourListeningIsYoursAlone() {
        assertEquals(mine.map { it to false }, pick(ArtistsFrom.MINE))
    }

    @Test fun siansaBeatsIsItsAloneAndYoursWhileItHasNone() {
        assertEquals(beats.map { it to true }, pick(ArtistsFrom.BEATS))
        assertEquals(mine.map { it to false }, pick(ArtistsFrom.BEATS, beats = emptyList()))
    }

    /** Both: yours first, then Beats' (at most half), each artist once, as many as asked. */
    @Test fun bothIsYoursThenBeatsEachOnce() {
        val both = pick(ArtistsFrom.BOTH)
        assertEquals(10, both.size)
        assertEquals(listOf("m1", "m3", "m4", "m5", "m6", "m7"), both.filterNot { it.second }.map { it.first })
        assertEquals(listOf("b1", "b2", "b3", "m2"), both.filter { it.second }.map { it.first })
        assertEquals(both.map { it.first }.distinct(), both.map { it.first })
    }

    @Test fun bothFillsWithBeatsWhenYouHaveFew() {
        assertEquals(listOf("m1" to false, "b1" to true, "b2" to true, "b3" to true, "m2" to true),
            pick(ArtistsFrom.BOTH, mine = listOf("m1"), count = 6))
    }

    @Test fun anUnknownChoiceIsBoth() {
        assertEquals(ArtistsFrom.BOTH, ArtistsFrom.of(null))
        assertEquals(ArtistsFrom.BOTH, ArtistsFrom.of("nope"))
        assertEquals(ArtistsFrom.BEATS, ArtistsFrom.of("beats"))
    }
}
