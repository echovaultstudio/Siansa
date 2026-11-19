package app.siansa.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A new list starts with repeat off, so a repeat set on an earlier list isn't on "by default" (beta testers #42). */
class QueueRulesTest {
    @Test fun aNewListStartsWithRepeatOff() {
        assertEquals(Player.REPEAT_MODE_OFF, QueueRules.repeatForNewQueue(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, QueueRules.repeatForNewQueue(Player.REPEAT_MODE_ONE))
        assertEquals(Player.REPEAT_MODE_OFF, QueueRules.repeatForNewQueue(Player.REPEAT_MODE_OFF))
    }

    /** Issue #1238: tapping an album's eighth song queues the whole album and starts at that song. */
    @Test fun aNewListStartsAtTheSongPickedWithTheWholeListQueued() {
        assertEquals(7, QueueRules.startOf(12, 7, shuffle = false))
        assertEquals(0, QueueRules.startOf(12, 0, shuffle = false))
        // Out of range: the nearest end.
        assertEquals(11, QueueRules.startOf(12, 40, shuffle = false))
        assertEquals(0, QueueRules.startOf(12, -1, shuffle = false))
        assertEquals(0, QueueRules.startOf(0, 3, shuffle = false))
        // Shuffled: any song.
        repeat(20) { assertTrue(QueueRules.startOf(12, 7, shuffle = true) in 0 until 12) }
    }

    /** Shuffle turned on: the song playing, the rest of the list shuffled, then Keep playing's songs in their order. */
    @Test fun shuffleKeepsTheListFirstAndTheAddedSongsAfter() {
        val auto = { i: Int -> i >= 10 }
        repeat(20) { seed ->
            val order = QueueRules.listFirstShuffle(13, 4, auto, kotlin.random.Random(seed)).toList()
            assertEquals(4, order.first())
            assertEquals((0 until 10).toSet() - 4, order.subList(1, 10).toSet())
            assertEquals(listOf(10, 11, 12), order.subList(10, 13))
        }
        // A song Keep playing added is playing: the list is over, only the added songs after it are shuffled.
        val late = QueueRules.listFirstShuffle(13, 11, auto, kotlin.random.Random(3)).toList()
        assertEquals((0..11).toList(), late.subList(0, 12))
        assertEquals(listOf(12), late.subList(12, 13))
        assertEquals(emptyList<Int>(), QueueRules.listFirstShuffle(0, 0, auto).toList())
    }

    /** The moves that reorder a queue, and the order that puts a shuffled one back (issue #1238). */
    @Test fun aQueueIsReorderedAndPutBack() {
        fun apply(ids: List<String>, moves: List<Pair<Int, Int>>) = ids.toMutableList().also { q -> moves.forEach { (f, t) -> q.add(t, q.removeAt(f)) } }
        val album = (0 until 10).map { "s$it" }
        repeat(20) { seed ->
            val order = QueueRules.listFirstShuffle(10, 4, { false }, kotlin.random.Random(seed))
            val shuffled = apply(album, QueueRules.moves(order))
            assertEquals(order.map { album[it] }, shuffled)
            assertEquals("s4", shuffled.first())
            assertEquals(album, apply(shuffled, QueueRules.moves(QueueRules.unshuffledOrder(shuffled, album))))
        }
        assertEquals(emptyList<Pair<Int, Int>>(), QueueRules.moves(intArrayOf(0, 1, 2)))
        // Songs added while shuffled go after the list; a song twice keeps both places.
        assertEquals(listOf(1, 0, 3, 2), QueueRules.unshuffledOrder(listOf("b", "a", "new", "a"), listOf("a", "b", "a")).toList())
    }

    @Test
    fun `repeat and shuffle are changed by Siansa only`() {
        assertTrue(QueueRules.takesModeChange("app.siansa", "app.siansa"))
        assertTrue(QueueRules.takesModeChange("app.siansa.dev", "app.siansa.dev"))
        // A car's or a headset's Bluetooth, the system, another build of the app.
        assertFalse(QueueRules.takesModeChange("com.android.bluetooth", "app.siansa"))
        assertFalse(QueueRules.takesModeChange("android.media.session.MediaController", "app.siansa"))
        assertFalse(QueueRules.takesModeChange("app.siansa", "app.siansa.dev"))
    }
}
