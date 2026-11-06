package app.siansa.ui

import android.view.KeyEvent
import androidx.compose.ui.focus.FocusDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The remote leaving a TV text box (issue #955). */
class TvTextBoxExitTest {
    @Test
    fun upAndDownLeaveAOneLineBox() {
        assertEquals(FocusDirection.Down, textBoxExit(KeyEvent.KEYCODE_DPAD_DOWN, multiLine = false))
        assertEquals(FocusDirection.Up, textBoxExit(KeyEvent.KEYCODE_DPAD_UP, multiLine = false))
        // Left, right and OK stay in it: the cursor and the keyboard.
        assertNull(textBoxExit(KeyEvent.KEYCODE_DPAD_LEFT, multiLine = false))
        assertNull(textBoxExit(KeyEvent.KEYCODE_DPAD_CENTER, multiLine = false))
        // A box of several lines keeps up and down for its lines.
        assertNull(textBoxExit(KeyEvent.KEYCODE_DPAD_DOWN, multiLine = true))
    }
}
