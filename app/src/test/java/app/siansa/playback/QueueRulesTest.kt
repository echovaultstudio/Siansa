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
}
