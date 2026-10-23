package app.siansa.mix

import app.siansa.data.Artist
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's artist radios: a set number, played lately mixed with artists like them (issue #377). */
class ArtistRadioPicksTest {
    private fun a(id: String) = Artist(id, id.uppercase())

    @Test fun oneArtistPlayedLatelyStillGivesAFullRow() {
        // A long mix by one artist: before, Home showed a single radio.
        val picked = mixArtistRadios(listOf(a("dj")), listOf(listOf(a("s1"), a("s2"), a("s3"))), (1..20).map { a("f$it") }, count = 10)
        assertEquals(10, picked.size)
        assertEquals(listOf("dj", "s1", "s2", "s3", "f1"), picked.take(5).map { it.id })
    }

    @Test fun recentAndSimilarTakeTurns() {
        val picked = mixArtistRadios(
            recent = listOf(a("r1"), a("r2")),
            similar = listOf(listOf(a("x1"), a("x2")), listOf(a("y1"))),
            fill = emptyList(),
            count = 10,
        )
        assertEquals(listOf("r1", "x1", "r2", "y1", "x2"), picked.map { it.id })
    }

    @Test fun eachArtistOnceAndNoMoreThanAsked() {
        val picked = mixArtistRadios(
            recent = listOf(a("r1"), a("r1"), a("r2")),
            similar = listOf(listOf(a("r2"), a("x1"))),
            fill = listOf(a("x1"), a("f1")),
            count = 4,
        )
        assertEquals(listOf("r1", "x1", "r2", "f1"), picked.map { it.id })
    }

    @Test fun recentArtistsBeyondTheirShareComeBeforeTheFill() {
        val recent = (1..8).map { a("r$it") }
        val picked = mixArtistRadios(recent, emptyList(), listOf(a("f1")), count = 10, recentShare = 6)
        assertEquals((1..8).map { "r$it" } + "f1", picked.map { it.id })
    }
}
