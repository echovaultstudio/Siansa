package app.siansa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Searching the library (issue #661): "Im" finds "I'm", "dont" finds "Don't". */
class SearchMatchTest {
    private fun matches(name: String, query: String) = searchMatches(normalizeKey(name), normalizeKey(query).split(' ').filter { it.isNotEmpty() })

    @Test fun anApostropheTypedOrNot() {
        assertTrue(matches("I'm Looking Through You The Beatles Rubber Soul", "Im looking"))
        assertTrue(matches("I'm Looking Through You The Beatles Rubber Soul", "I'm looking"))
        assertTrue(matches("Don't Stop Me Now Queen Jazz", "dont stop"))
        assertTrue(matches("Don't Stop Me Now Queen Jazz", "don't stop"))
    }

    @Test fun everyWordStillHasToBeThere() {
        assertFalse(matches("I'm Looking Through You The Beatles", "im looking at you now"))
        assertFalse(matches("Hey Jude The Beatles", "im"))
        assertTrue(matches("Déjà Vu Beyoncé", "deja vu"))
    }
}
