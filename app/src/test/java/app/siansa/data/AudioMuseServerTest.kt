package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Each music server's songs go to AudioMuse with that server's own name, whichever user is in use. */
class AudioMuseServerTest {
    private val owners = mapOf("nav1" to 0) // user 0 is Navidrome, shown next to user 1 (Emby, in use)
    private val owner: (String) -> Int? = { owners[it] }

    @Test fun aSongUsesItsOwnersName() {
        assertEquals(0, audioMuseUserFor(listOf("nav1"), owner, active = 1, shown = setOf(0, 1)))
        assertEquals(1, audioMuseUserFor(listOf("emby1"), owner, active = 1, shown = setOf(0, 1)))
    }

    @Test fun noSongUsesTheMusicShown() {
        assertEquals(1, audioMuseUserFor(emptyList(), owner, active = 1, shown = setOf(0, 1)))
        // Only the Navidrome user's music shows (source filter): its name, not the Emby user's.
        assertEquals(0, audioMuseUserFor(emptyList(), owner, active = 1, shown = setOf(0)))
        assertEquals(1, audioMuseUserFor(emptyList(), owner, active = 1, shown = emptySet()))
    }
}
