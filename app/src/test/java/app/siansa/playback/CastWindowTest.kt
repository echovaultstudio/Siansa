package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window of a long queue the TV holds (issue #114; 0.52.0-dev: a 497-song queue never reached the TV, Cast's
 * messages being limited to 64 KB).
 */
class CastWindowTest {
    private fun queue(n: Int) = (0 until n).map { "s$it" }

    @Test fun aLongQueueGoesAsTheCurrentSongAFewBeforeAndTheNextOnes() {
        val w = CastWindow<String>()
        assertEquals(117 until 146, w.set(queue(497), 120))
        assertEquals(29, w.size)
        assertEquals(3, w.toLocal(120))
        assertNull(w.toLocal(116))
        assertNull(w.toLocal(146))
        assertEquals(120, w.toFull(3))
        // At the start and the end of the queue, and a short queue whole.
        assertEquals(0 until 26, w.reset(0))
        assertEquals(493 until 497, w.reset(496))
        assertEquals(0 until 11, w.set(queue(11), 5))
    }

    @Test fun songsAddedInsideTheWindowGoToTheTvOthersOnlyToThePhonesQueue() {
        val w = CastWindow<String>()
        w.set(queue(100), 50) // 47..75
        // Play next: right after the current song.
        assertEquals(4, w.insert(51, listOf("n")))
        assertEquals(30, w.size)
        // Far after: the phone's queue only.
        assertNull(w.insert(90, listOf("x")))
        assertEquals(47, w.start)
        // Before the window: it moves along.
        assertNull(w.insert(10, listOf("a", "b")))
        assertEquals(49, w.start)
        assertEquals("s50", w.full[w.toFull(3)])
        // At its very end: the TV gets it too.
        assertEquals(30, w.insert(w.end, listOf("e")))
    }

    @Test fun removalsFollowWhereTheyAre() {
        val w = CastWindow<String>()
        w.set(queue(100), 50) // 47..75
        assertNull(w.remove(0, 5))
        assertEquals(42, w.start)
        assertEquals(0 until 2, w.remove(40, 44)) // two before it, two in it
        assertEquals(40, w.start)
        assertEquals(27, w.size)
        assertEquals(1 until 3, w.remove(41, 43))
        assertEquals(25, w.size)
        assertNull(w.remove(90, 92))
        assertEquals(w.full.subList(w.start, w.end), w.items())
    }

    @Test fun movesInsideTheWindowAreTheTvsToo() {
        val w = CastWindow<String>()
        w.set(queue(100), 50)
        assertEquals(10 to 5, w.move(57, 58, 52))
        assertEquals("s57", w.full[52])
        assertNull(w.move(90, 91, 52))
    }

    @Test fun asTheTvPlaysOnItGetsMoreAndDropsTheOldest() {
        val w = CastWindow<String>()
        w.set(queue(200), 0) // 0..25
        assertTrue(w.topUp(2).nothing)
        val step = w.topUp(10)
        assertEquals(26 until 36, step.add)
        assertEquals(7, step.drop)
        w.grown(step)
        assertEquals(7, w.start)
        assertEquals(36, w.end)
        // Near the end of the queue, the last few at once.
        w.reset(190)
        val last = w.topUp(w.toLocal(195)!!)
        assertEquals(w.end until 200, last.add)
    }

    @Test fun theTvsSongIsFoundInThePhonesQueue() {
        val full = queue(100)
        assertEquals(53, CastWindowView.fullIndex(full, 50, listOf("s50", "s51", "s52", "s53"), 3))
        // A blank song from the TV at the right place agrees.
        assertEquals(52, CastWindowView.fullIndex(full, 50, listOf("s50", "", "", "s53"), 2))
        // The TV moved its queue by itself: the same song nearest the window.
        assertEquals(60, CastWindowView.fullIndex(full, 50, listOf("s59", "s60"), 1))
        assertNull(CastWindowView.fullIndex(full, 50, listOf("other"), 0))
    }
}
