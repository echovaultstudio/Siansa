package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page of all genres (the owner, 15 Nov): finding one by name, and A to Z down its side. */
class GenreFindTest {
    private val genres = listOf("Acid Jazz", "Ambient", "Bossa Nova", "Hip-Hop", "Jazz", "Néo-Soul", "R&B", "Rock", "UK Garage")
    private fun find(text: String) = foundByName(genres, text) { it }

    @Test fun nothingTypedIsEveryGenreInItsOrder() {
        assertEquals(genres, find(""))
        assertEquals(genres, find("  "))
    }

    @Test fun aGenreIsFoundByAnyPartOfItsNameWhateverTheCapitalsOrSigns() {
        assertEquals(listOf("Acid Jazz", "Jazz"), find("JAZZ"))
        assertEquals(listOf("Hip-Hop"), find("hip hop"))
        assertEquals(listOf("Hip-Hop"), find("hiphop"))
        assertEquals(listOf("Néo-Soul"), find("neo"))
        assertEquals(listOf("R&B"), find("r&b"))
        assertEquals(listOf("UK Garage"), find("garage uk"))
        assertTrue(find("polka").isEmpty())
    }

    @Test fun theListIsInTheLettersOrderSoEachLetterIsOneRun() {
        val sorted = AlphabetIndex.sort(listOf("rock", "2 Tone", "Ångström Pop", "Ambient", "Zydeco", "Blues"), { it })
        assertEquals(listOf("2 Tone", "Ambient", "Ångström Pop", "Blues", "rock", "Zydeco"), sorted)
        assertEquals(mapOf('#' to 0, 'A' to 1, 'B' to 3, 'R' to 4, 'Z' to 5), AlphabetIndex.starts(sorted))
    }
}
