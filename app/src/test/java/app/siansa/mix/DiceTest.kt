package app.siansa.mix

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** Roll the dice: a song you've never played (issue #347). */
class DiceTest {
    private fun s(id: String) = Song(id, "T$id")

    @Test fun onlySongsNeverPlayedAnywhere() {
        val batch = listOf(s("played") to 3L, s("heard") to 0L, s("new") to 0L)
        assertEquals("new", pickNeverPlayed(batch, heard = setOf("heard"), random = Random(1))?.id)
    }

    @Test fun nothingWhenEverythingWasPlayed() {
        assertNull(pickNeverPlayed(listOf(s("a") to 1L, s("b") to 0L), heard = setOf("b")))
        assertNull(pickNeverPlayed(emptyList(), heard = emptySet()))
    }

    @Test fun onlyOnThePhoneWhenAsked() {
        val batch = listOf(s("far") to 0L, s("here") to 0L)
        assertEquals("here", pickNeverPlayed(batch, emptySet(), Random(2), allowed = { it.id == "here" })?.id)
    }

    @Test fun picksAtRandom() {
        val batch = (1..20).map { s("n$it") to 0L }
        val picks = (1..30).mapNotNull { pickNeverPlayed(batch, emptySet(), Random(it))?.id }.toSet()
        assertEquals(true, picks.size > 5)
    }

    @Test fun rollsFromWhatIsShown() {
        // Beta report 37b60d15: hidden libraries or the user in use's music hidden, or other users shown.
        assertEquals(true, diceFromLibrary(synced = true, hidesSome = true, shownUsers = 1))
        assertEquals(true, diceFromLibrary(synced = true, hidesSome = false, shownUsers = 2))
        assertEquals(false, diceFromLibrary(synced = true, hidesSome = false, shownUsers = 1))
        // Not synced yet: the server is all there is.
        assertEquals(false, diceFromLibrary(synced = false, hidesSome = true, shownUsers = 2))
    }
}
