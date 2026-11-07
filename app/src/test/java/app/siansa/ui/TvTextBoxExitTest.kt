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
        assertEquals(FocusDirection.Next, textBoxExit(KeyEvent.KEYCODE_DPAD_DOWN, multiLine = false))
        assertEquals(FocusDirection.Previous, textBoxExit(KeyEvent.KEYCODE_DPAD_UP, multiLine = false))
        // Search, beside the rest of the page, goes by distance.
        assertEquals(FocusDirection.Down, textBoxExit(KeyEvent.KEYCODE_DPAD_DOWN, multiLine = false, inForm = false))
        // Left, right and OK stay in it: the cursor and the keyboard.
        assertNull(textBoxExit(KeyEvent.KEYCODE_DPAD_LEFT, multiLine = false))
        assertNull(textBoxExit(KeyEvent.KEYCODE_DPAD_CENTER, multiLine = false))
        // A box of several lines keeps up and down for its lines.
        assertNull(textBoxExit(KeyEvent.KEYCODE_DPAD_DOWN, multiLine = true))
    }

    /** Issue #978: in a form the remote goes in the form's order, so a narrow Test button under a login isn't skipped. */
    @Test
    fun aFormGoesInItsOrder() {
        assertEquals(FocusDirection.Next, textBoxMove(KeyEvent.KEYCODE_DPAD_DOWN, inForm = true))
        assertEquals(FocusDirection.Previous, textBoxMove(KeyEvent.KEYCODE_DPAD_UP, inForm = true))
        assertEquals(FocusDirection.Up, textBoxMove(KeyEvent.KEYCODE_DPAD_UP, inForm = false))
        // Left and right stay: the cursor.
        assertNull(textBoxMove(KeyEvent.KEYCODE_DPAD_LEFT, inForm = true))
    }

    @Test
    fun okTypes() {
        assertEquals(true, textBoxTypes(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(true, textBoxTypes(KeyEvent.KEYCODE_ENTER))
        assertEquals(false, textBoxTypes(KeyEvent.KEYCODE_DPAD_DOWN))
    }
}
