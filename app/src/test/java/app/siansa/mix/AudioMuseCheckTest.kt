package app.siansa.mix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AudioMuse test says whether its songs are this server's (beta testers #32). */
class AudioMuseCheckTest {
    @Test fun songsOfThisServer() {
        val (ok, words) = audioMuseCheck(LibraryMatch(total = 10, byId = 10, byName = 0, librarySongs = 5000), otherServer = false)
        assertTrue(ok)
        assertTrue(words, words.contains("in your library"))
    }

    @Test fun songsOfAnotherServerMatchedByName() {
        val (ok, words) = audioMuseCheck(LibraryMatch(10, 0, 7, 5000), otherServer = false)
        assertTrue(ok)
        assertTrue(words, words.contains("7 of 10"))
    }

    @Test fun noneOfTheSongsAreHere() {
        val (ok, words) = audioMuseCheck(LibraryMatch(10, 0, 0, 5000), otherServer = false)
        assertFalse(ok)
        assertTrue(words, words.startsWith("Connected, but none"))
    }

    @Test fun nothingAnalysed() {
        assertFalse(audioMuseCheck(LibraryMatch(0, 0, 0, 5000), otherServer = false).first)
    }

    @Test fun beforeTheLibraryIsSyncedItCantTell() {
        assertTrue(audioMuseCheck(LibraryMatch(10, 0, 0, 0), otherServer = false).first)
        assertTrue(audioMuseCheck(LibraryMatch(10, 0, 0, 5000), otherServer = true).second.contains("Save and sync"))
    }
}
