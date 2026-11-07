package app.siansa

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.ui.SiansaTheme
import app.siansa.ui.TvTextBox
import app.siansa.ui.TvTextKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The TV's login forms (issue #955): from the last text box, the remote's down reaches the Test button under it, as
 * under the AudioMuse login, where it used to jump past it.
 */
@RunWith(AndroidJUnit4::class)
class TvTextBoxFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun editTexts(view: View): List<EditText> = when (view) {
        is EditText -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { editTexts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun press(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    /** Where the focus is: which text box has it (by label), and whether the Test button does. */
    private fun where(boxes: List<EditText>): String {
        val view = compose.activity.currentFocus
        val box = boxes.indexOf(view).takeIf { it >= 0 }?.let { listOf("Username", "Password")[it] }
        val button = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true) and androidx.compose.ui.test.hasTestTag("test"))
            .fetchSemanticsNodes().isNotEmpty()
        return "view=${box ?: view?.javaClass?.simpleName}, test button focused=$button"
    }

    @Test fun downFromThePasswordReachesTest() {
        compose.setContent {
            SiansaTheme {
                Column {
                    TvTextBox("", {}, "Username")
                    TvTextBox("", {}, "Password", kind = TvTextKind.SECRET)
                    OutlinedButton(onClick = {}, modifier = Modifier.testTag("test")) { Text("Test") }
                }
            }
        }
        compose.waitForIdle()
        // Keys, as the remote sends them: out of touch mode first.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val boxes = editTexts(compose.activity.window.decorView)
        compose.runOnUiThread { boxes[0].requestFocus() }
        compose.waitForIdle()
        val start = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val afterOne = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val afterTwo = where(boxes)
        // It stays there (on the TV it was pulled back into Password), and up goes back once the box takes focus again.
        Thread.sleep(app.siansa.ui.HOLD_OUT_MS * 2)
        compose.waitForIdle()
        val later = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_UP)
        val backUp = where(boxes)
        val trail = "start: $start · after one down: $afterOne · after two: $afterTwo · later: $later · up: $backUp"
        println("TV text box focus: $trail")
        assertTrue("Down from Password reaches Test ($trail)", afterTwo.endsWith("test button focused=true"))
        assertTrue("Test keeps the focus ($trail)", later.endsWith("test button focused=true"))
        assertTrue("Up from Test reaches Password ($trail)", backUp.startsWith("view=Password"))
    }
}
