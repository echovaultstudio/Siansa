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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
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

    /** Which of the page's controls has the focus (a box by its label, or "Test"), and whether a box's field types. */
    private fun where(boxes: List<EditText>): String {
        fun focused(tag: String) = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true) and androidx.compose.ui.test.hasTestTag(tag))
            .fetchSemanticsNodes().isNotEmpty()
        val control = listOf("Username", "Password").firstOrNull { focused("tvTextBox:$it") } ?: "Test".takeIf { focused("test") } ?: "none"
        val typing = boxes.indexOf(compose.activity.currentFocus).takeIf { it >= 0 }?.let { listOf("Username", "Password")[it] }
        return "on=$control" + (typing?.let { ", typing in $it" } ?: "")
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
        compose.onNodeWithTag("tvTextBox:Username").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.waitForIdle()
        val start = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val afterOne = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val afterTwo = where(boxes)
        // It stays there (on the TV it was pulled back into Password), and up goes back.
        Thread.sleep(800)
        compose.waitForIdle()
        val later = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_UP)
        val backUp = where(boxes)
        // OK types in Password; down while typing goes on to Test.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val typing = where(boxes)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val leftTyping = where(boxes)
        val trail = "start: $start · down: $afterOne · down: $afterTwo · later: $later · up: $backUp · OK: $typing · down: $leftTyping"
        println("TV text box focus: $trail")
        assertTrue("Starts on Username ($trail)", start == "on=Username")
        assertTrue("Down reaches Password ($trail)", afterOne == "on=Password")
        assertTrue("Down from Password reaches Test ($trail)", afterTwo == "on=Test")
        assertTrue("Test keeps the focus ($trail)", later == "on=Test")
        assertTrue("Up from Test reaches Password ($trail)", backUp == "on=Password")
        assertTrue("OK types in Password ($trail)", typing.endsWith("typing in Password"))
        assertTrue("Down while typing goes on to Test ($trail)", leftTyping == "on=Test")
    }
}
