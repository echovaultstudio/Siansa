package app.musix

import app.musix.ui.AlphabetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The A to Z quick-scroll bar in Your Library. */
class AlphabetIndexTest {
    @Test fun filesNamesUnderTheirFirstLetter() {
        assertEquals('B', AlphabetIndex.letterOf("burial"))
        assertEquals('A', AlphabetIndex.letterOf("Ångström"))
        assertEquals('O', AlphabetIndex.letterOf("  Ödland"))
        assertEquals('#', AlphabetIndex.letterOf("00.Info"))
        assertEquals('#', AlphabetIndex.letterOf("[Unknown Album]"))
        assertEquals('#', AlphabetIndex.letterOf("() (20th Anniversary)"))
        assertEquals('#', AlphabetIndex.letterOf("Кино"))
        assertEquals('#', AlphabetIndex.letterOf(""))
    }

    @Test fun sortKeepsEachLetterTogetherWithHashFirst() {
        val names = listOf("björk", "Ödland", "[Unknown]", "Arvo Pärt", "Ångström", "Zola", "00.Info", "Oasis")
        assertEquals(
            listOf("00.Info", "[Unknown]", "Ångström", "Arvo Pärt", "björk", "Oasis", "Ödland", "Zola"),
            AlphabetIndex.sort(names, { it }),
        )
    }

    @Test fun findsWhereEachLetterStarts() {
        val starts = AlphabetIndex.starts(listOf("00.Info", "Abba", "Air", "Burial", "Zola"))
        assertEquals(mapOf('#' to 0, 'A' to 1, 'B' to 3, 'Z' to 4), starts)
    }

    @Test fun emptyLetterJumpsToTheNextOneWithSomething() {
        val starts = mapOf('#' to 0, 'A' to 1, 'B' to 3, 'M' to 10)
        assertEquals(3, AlphabetIndex.target('B', starts))
        assertEquals(10, AlphabetIndex.target('C', starts))
        assertEquals(10, AlphabetIndex.target('Z', starts))
        assertNull(AlphabetIndex.target('A', emptyMap()))
    }

    @Test fun onlyLongListsGetTheBar() {
        val many = mapOf('A' to 0, 'B' to 10, 'C' to 20)
        assertTrue(AlphabetIndex.shouldShow(30, many))
        assertFalse(AlphabetIndex.shouldShow(29, many))
        assertFalse(AlphabetIndex.shouldShow(100, mapOf('A' to 0, 'B' to 50)))
    }
}
