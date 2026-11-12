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
