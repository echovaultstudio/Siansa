package app.siansa.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Holding left or right on the TV's now playing winds through the song; a press skips (issue #984). */
class RemoteHoldTest {
    private val right = KeyEvent.KEYCODE_DPAD_RIGHT
    private val left = KeyEvent.KEYCODE_DPAD_LEFT

    @Test
    fun aPressSkipsOnLettingGo() {
        val hold = RemoteHold()
        assertEquals(RemoteHold.Step.Wait, hold.down(right, 0, 1_000))
        assertEquals(RemoteHold.Step.Act(RemoteAction.NEXT), hold.up(right))
        assertEquals(RemoteHold.Step.Wait, hold.down(left, 0, 2_000))
        assertEquals(RemoteHold.Step.Act(RemoteAction.PREVIOUS), hold.up(left))
    }

    @Test
    fun heldItWindsFasterTheLongerItsHeld() {
        val hold = RemoteHold()
        hold.down(right, 0, 0)
        // Android repeats a held key; too soon after pressing, it's still a press.
        assertEquals(RemoteHold.Step.Wait, hold.down(right, 1, 300))
        assertEquals(RemoteHold.Step.Wind(500L * RemoteHold.SPEED), hold.down(right, 2, 500))
        assertEquals(RemoteHold.Step.Wind(100L * RemoteHold.SPEED), hold.down(right, 3, 600))
        // After a few seconds, faster.
        hold.down(right, 4, 3_000)
        assertEquals(RemoteHold.Step.Wind(100L * RemoteHold.FAST_SPEED), hold.down(right, 5, 3_100))
        assertEquals(RemoteHold.Step.WindDone, hold.up(right))
    }

    @Test
    fun leftWindsBack() {
        val hold = RemoteHold()
        hold.down(left, 0, 0)
        assertEquals(RemoteHold.Step.Wind(-500L * RemoteHold.SPEED), hold.down(left, 1, 500))
        assertEquals(RemoteHold.Step.WindDone, hold.up(left))
    }

    @Test
    fun okHeldPlaysAndPausesOnce() {
        val hold = RemoteHold()
        assertEquals(RemoteHold.Step.Act(RemoteAction.PLAY_PAUSE), hold.down(KeyEvent.KEYCODE_DPAD_CENTER, 0, 0))
        assertEquals(RemoteHold.Step.Wait, hold.down(KeyEvent.KEYCODE_DPAD_CENTER, 1, 500))
        assertEquals(RemoteHold.Step.Wait, hold.up(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(RemoteHold.Step.Pass, hold.down(KeyEvent.KEYCODE_BACK, 0, 0))
    }
}
