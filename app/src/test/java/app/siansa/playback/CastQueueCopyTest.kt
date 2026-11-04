package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Naming the songs Cast leaves blank from the queue Siansa sent (issue #114). Songs are their ids here; "" is a blank
 * song as Media3 shows one the TV hasn't described.
 */
class CastQueueCopyTest {
    private fun copy() = CastQueueCopy<String> { it }
    private fun uids(n: Int, from: Int = 100): List<Any> = List(n) { from + it }

    @Test fun songsTheTvDoesntDescribeComeFromTheQueueSent() {
        // 0.52.0-dev: Google's TV screen described only songs 5 to 10 of 55; the rest showed blank in the queue.
        val sent = List(55) { "s$it" }
        val q = copy().apply { set(sent) }
        val tv = List(55) { if (it in 4..9) "s$it" else "" }
        assertEquals(sent, q.fill(uids(55), tv))
    }

    @Test fun whatTheTvSaysWins() {
        val q = copy().apply { set(listOf("a", "b", "c")) }
        assertEquals(listOf("a", "b", "c"), q.fill(uids(3), listOf("a", "b", "c")))
    }

    @Test fun aSongTheTvNamedOnceStaysNamedWhenMedia3ForgetsIt() {
        // 0.52.0-dev: after the same songs were sent again, Media3 lost even the playing song ("Now playing" nothing).
        val q = copy().apply { set(listOf("a", "b")) }
        q.fill(listOf(7, 8), listOf("a", "b"))
        q.add(2, listOf("c")) // a change the TV hasn't caught up with: no lining up by place
        assertEquals(listOf("a", "b"), q.fill(listOf(7, 8), listOf("", "")))
    }

    @Test fun aCopyThatDoesntLineUpIsntUsed() {
        val q = copy().apply { set(listOf("a", "b", "c")) }
        // A different length: the TV's queue isn't the one sent (yet).
        assertEquals(listOf("", "b"), q.fill(uids(2), listOf("", "b")))
        // The same length but a different song where the TV names one.
        assertEquals(listOf("", "x", ""), q.fill(uids(3, 200), listOf("", "x", "")))
    }

    @Test fun songsNobodyCanNameStayBlank() {
        assertEquals(listOf("", ""), copy().fill(uids(2), listOf("", "")))
    }

    @Test fun changesOnThePhoneKeepTheCopyInStep() {
        val q = copy().apply { set(listOf("a", "b", "c", "d")) }
        q.add(1, listOf("x"))
        assertEquals(listOf("a", "x", "b", "c", "d"), q.copy())
        q.remove(3, 4)
        assertEquals(listOf("a", "x", "b", "d"), q.copy())
        q.move(0, 2, 1) // as Player.moveMediaItems: a and x go after b
        assertEquals(listOf("b", "a", "x", "d"), q.copy())
        q.move(0, 1, 10) // past the end: to the end
        assertEquals(listOf("a", "x", "d", "b"), q.copy())
        q.replace(1, 3, listOf("y"))
        assertEquals(listOf("a", "y", "b"), q.copy())
        assertEquals(listOf("a", "y", "b"), q.fill(uids(3), listOf("a", "", "")))
    }

    @Test fun aChangeThatCantBeFollowedDropsTheCopy() {
        val q = copy().apply { set(listOf("a", "b")) }
        q.remove(1, 5)
        assertNull(q.copy())
        q.add(0, listOf("x"))
        assertNull(q.copy())
        assertEquals(listOf("", ""), q.fill(uids(2), listOf("", "")))
        // Sending a new queue starts afresh.
        q.set(listOf("c", "d"))
        assertEquals(listOf("c", "d"), q.fill(uids(2), listOf("", "")))
    }

    @Test fun anEmptyQueueFromTheTvForgetsNothing() {
        val q = copy().apply { set(listOf("a", "b")) }
        q.fill(listOf(1, 2), listOf("a", "b"))
        q.fill(emptyList(), emptyList()) // Cast empties the queue for a moment
        q.set(listOf("a", "b"))
        q.add(0, listOf("z")) // keep the copy from lining up, so only what the TV said can name them
        assertEquals(listOf("a", "b"), q.fill(listOf(1, 2), listOf("", "")))
    }

    @Test fun entriesTheTvNoLongerListsAreForgotten() {
        val q = copy()
        q.fill(listOf(1, 2), listOf("a", "b"))
        q.fill(listOf(3), listOf("c"))
        assertEquals(listOf("", ""), q.fill(listOf(1, 2), listOf("", "")))
    }

    @Test fun theLogCountsWhereTheSongsCameFrom() {
        val fill = CastQueueFill.of(listOf("a", "", "", ""), listOf("a", "b", "c", "")) { it }
        assertEquals(CastQueueFill(songs = 4, fromTv = 1, fromPhone = 2, unknown = 1), fill)
    }
}
