package app.siansa

import app.siansa.data.audioMuseUserFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** A shown user's songs go to AudioMuse under that user's server name (issue #708). */
class AudioMuseServerForTest {
    private val me = 0
    private val kids = 3
    private val owners = mapOf("k1" to kids, "k2" to kids)
    private fun owner(id: String) = owners[id]

    @Test fun ownSongsUseTheUserInUse() {
        assertEquals(me, audioMuseUserFor(listOf("m1", "m2"), ::owner, me, setOf(me, kids), ownHidden = false))
    }

    @Test fun aShownUsersSongsUseTheirs() {
        assertEquals(kids, audioMuseUserFor(listOf("k1"), ::owner, me, setOf(me, kids), ownHidden = false))
        // Mostly theirs.
        assertEquals(kids, audioMuseUserFor(listOf("k1", "k2", "m1"), ::owner, me, setOf(me, kids), ownHidden = false))
        // Half and half: the user in use.
        assertEquals(me, audioMuseUserFor(listOf("k1", "m1"), ::owner, me, setOf(me, kids), ownHidden = false))
    }

    @Test fun withoutSongsItsWhoseMusicShows() {
        assertEquals(me, audioMuseUserFor(emptyList(), ::owner, me, setOf(me, kids), ownHidden = false))
        // Own music hidden, only the kids' shown: theirs.
        assertEquals(kids, audioMuseUserFor(emptyList(), ::owner, me, setOf(me, kids), ownHidden = true))
        // Own hidden but two others shown: can't tell, the user in use.
        assertEquals(me, audioMuseUserFor(emptyList(), ::owner, me, setOf(me, kids, 5), ownHidden = true))
    }
}
