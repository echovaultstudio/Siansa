package app.siansa.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/** A new list starts with repeat off, so a repeat set on an earlier list isn't on "by default" (beta testers #42). */
class QueueRulesTest {
    @Test fun aNewListStartsWithRepeatOff() {
        assertEquals(Player.REPEAT_MODE_OFF, QueueRules.repeatForNewQueue(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, QueueRules.repeatForNewQueue(Player.REPEAT_MODE_ONE))
        assertEquals(Player.REPEAT_MODE_OFF, QueueRules.repeatForNewQueue(Player.REPEAT_MODE_OFF))
    }

    /** Tapping an album's eighth song plays from it: the seven before it aren't put in the queue (the owner, 12 Nov). */
    @Test fun aNewListStartsAtTheSongPicked() {
        val album = (1..12).map { "song $it" }
        assertEquals((8..12).map { "song $it" }, QueueRules.fromStart(album, 7, shuffle = false))
        assertEquals(album, QueueRules.fromStart(album, 0, shuffle = false))
        assertEquals(listOf("song 12"), QueueRules.fromStart(album, 11, shuffle = false))
        // Out of range: the nearest end, never an empty queue.
        assertEquals(listOf("song 12"), QueueRules.fromStart(album, 40, shuffle = false))
        assertEquals(album, QueueRules.fromStart(album, -1, shuffle = false))
        // Shuffled, the whole list plays.
        assertEquals(album, QueueRules.fromStart(album, 7, shuffle = true))
        assertEquals(emptyList<String>(), QueueRules.fromStart(emptyList<String>(), 3, shuffle = false))
    }
}
