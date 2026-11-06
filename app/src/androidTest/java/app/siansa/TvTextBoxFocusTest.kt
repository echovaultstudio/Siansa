package app.siansa

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.ui.SiansaTheme
import app.siansa.ui.TvTextBox
import app.siansa.ui.TvTextKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The TV's login forms (issue #955): from the last text box, the remote's down reaches the Test button under it, as
 * under the AudioMuse login, where it used to jump past it.
 */
@RunWith(AndroidJUnit4::class)
class TvTextBoxFocusTest {
    @get:Rule val compose = createComposeRule()

    private fun press(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    @Test fun downFromThePasswordReachesTest() {
        compose.setContent {
            SiansaTheme {
                Column {
                    TvTextBox("", {}, "Username", opensOnFocus = true)
                    TvTextBox("", {}, "Password", kind = TvTextKind.SECRET)
                    OutlinedButton(onClick = {}, modifier = Modifier.testTag("test")) { Text("Test") }
                }
            }
        }
        compose.waitForIdle()
        // Username has the focus; down twice: Password, then Test.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("test").assertIsFocused()
    }
}
