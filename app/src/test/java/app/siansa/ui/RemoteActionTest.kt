package app.siansa.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The TV's now playing has no buttons: the remote's keys act straight away (issue #974). */
class RemoteActionTest {
    @Test
    fun theRemotesKeys() {
        assertEquals(RemoteAction.PLAY_PAUSE, remoteAction(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(RemoteAction.PLAY_PAUSE, remoteAction(KeyEvent.KEYCODE_ENTER))
        assertEquals(RemoteAction.PREVIOUS, remoteAction(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(RemoteAction.NEXT, remoteAction(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(RemoteAction.MENU, remoteAction(KeyEvent.KEYCODE_DPAD_DOWN))
        // Up does nothing here; Back leaves as anywhere else.
        assertNull(remoteAction(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(remoteAction(KeyEvent.KEYCODE_BACK))
    }
}
