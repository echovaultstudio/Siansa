package app.siansa.mix

import app.siansa.data.Song
import app.siansa.net.ClapHit
import org.junit.Assert.assertEquals
import org.junit.Test

/** A vibe from another server's AudioMuse songs is picked only from those on this server (beta testers #41). */
class HereSearchTest {
    private fun hit(id: String) = ClapHit(id, "t$id", "a$id", null, 0.5)
    private val hits = (1..10).map { hit("n$it") }

    @Test fun anotherServersSongsArePickedOnlyFromThoseHere() {
        val here = mapOf("n3" to Song("e3", "t3", "a3"), "n7" to Song("e7", "t7", "a7"))
        val found = HereSearch(hits, here, foreign = true)
        assertEquals(listOf("n3", "n7"), hitsToPick(found).map { it.id })
    }

    @Test fun thisServersSongsAreAllPickedFrom() {
        // Some outside the synced libraries are fetched from the server later, as before.
        val found = HereSearch(hits, mapOf("n1" to Song("n1", "t1", "a1")), foreign = false)
        assertEquals(hits, hitsToPick(found))
    }

    @Test fun withNoneHereTheyAreLookedUpAsBefore() {
        // Not synced yet: the server is asked by name later (beta testers #40).
        assertEquals(hits, hitsToPick(HereSearch(hits, emptyMap(), foreign = true)))
    }
}
