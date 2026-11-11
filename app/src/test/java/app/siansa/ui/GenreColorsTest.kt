package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Genre colours by feel (the owner, 10 Nov): hard and fast red, calm blue or green, the rest warm. */
class GenreColorsTest {
    @Test fun hardAndFastIsRed() {
        listOf("Drum And Bass", "Halftime", "Dubstep", "Grime", "Hard Rock", "Metalcore", "Jungle", "Techno", "Psytrance", "Hip Hop / Trap")
            .forEach { assertEquals(it, GenreEnergy.HARD, genreEnergy(it)) }
    }

    @Test fun calmIsBlueOrGreen() {
        listOf("Ambient", "Liquid Funk", "Deep House", "Jazz", "Downtempo", "Lo-Fi", "Classical", "Folk", "Idm", "Trip Hop")
            .forEach { assertEquals(it, GenreEnergy.CALM, genreEnergy(it)) }
    }

    @Test fun theRestIsWarm() {
        listOf("Pop", "House", "Progressive House", "Classic Rock", "K-pop", "Disco", "Electronic", "Indie Rock")
            .forEach { assertEquals(it, GenreEnergy.WARM, genreEnergy(it)) }
    }

    @Test fun aGenreKeepsItsColours() {
        assertEquals(genreColors("Trance"), genreColors("trance"))
    }
}
