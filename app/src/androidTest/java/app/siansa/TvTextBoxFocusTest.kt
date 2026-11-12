package app.siansa

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import app.siansa.ui.Space
import app.siansa.ui.TvActionRow
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

    /**
     * Issue #978: as on the TV's settings, a wide page, a hint between the login and Test, and a wide Save under it. Going
     * by distance, the narrow Test button lost to Save both ways, and the remote skipped it on the TV, while the phone's
     * narrow screen above passed; Test's row ([TvActionRow]) is now as wide as the page. Drawn at a density of 1 so the
     * phone's screen is as wide as a TV's page.
     */
    @Test fun downFromTheLastBoxOfAWidePageReachesTest() {
        var tested = 0
        compose.setContent {
            SiansaTheme {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f)) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.Page),
                        verticalArrangement = Arrangement.spacedBy(Space.Medium),
                    ) {
                        TvTextBox("", {}, "Username", Modifier.fillMaxWidth())
                        TvTextBox("", {}, "Password", Modifier.fillMaxWidth(), kind = TvTextKind.SECRET)
                        Text("With several music servers in AudioMuse, each server's own name there is under Servers.")
                        // As TestRow on the TV: the row is what the remote stops on.
                        TvActionRow(onClick = { tested++ }, modifier = Modifier.testTag("test"), tv = true) { button ->
                            OutlinedButton(onClick = {}, modifier = button) { Text("Test") }
                        }
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth().testTag("save")) { Text("Save") }
                    }
                }
            }
        }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.onNodeWithTag("tvTextBox:Password").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.waitForIdle()
        fun on(): String = listOf("tvTextBox:Username" to "Username", "tvTextBox:Password" to "Password", "test" to "Test", "save" to "Save")
            .firstOrNull { (tag, _) ->
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true) and androidx.compose.ui.test.hasTestTag(tag))
                    .fetchSemanticsNodes().isNotEmpty()
            }?.second ?: "none"
        val start = on()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val down = on()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val downAgain = on()
        press(KeyEvent.KEYCODE_DPAD_UP)
        val up = on()
        press(KeyEvent.KEYCODE_DPAD_UP)
        val upAgain = on()
        press(KeyEvent.KEYCODE_DPAD_UP)
        val top = on()
        val trail = "start: $start · down: $down · down: $downAgain · up: $up · up: $upAgain · up: $top"
        println("TV wide form focus: $trail")
        assertTrue("Starts on Password ($trail)", start == "Password")
        assertTrue("Down from Password reaches Test, not Save ($trail)", down == "Test")
        assertTrue("Down from Test reaches Save ($trail)", downAgain == "Save")
        assertTrue("Up from Save reaches Test ($trail)", up == "Test")
        assertTrue("Up from Test reaches Password ($trail)", upAgain == "Password")
        assertTrue("Up from Password reaches Username ($trail)", top == "Username")
        // OK on Test's row tests.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue("OK on Test tests (on ${on()})", tested == 1)
    }

    /**
     * ListenBrainz / Koito on the TV: the token, "Test and save", then "Remove", then the next section's wide header.
     * Remove as a narrow button on the left would be passed over for the wide header (issue #978's arithmetic); as a
     * row of its own the remote stops on it, and OK removes.
     */
    @Test fun removeUnderTestAndSaveIsReachedAndWorks() {
        var removed = 0
        compose.setContent {
            SiansaTheme {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f)) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.Page),
                        verticalArrangement = Arrangement.spacedBy(Space.Medium),
                    ) {
                        TvTextBox("", {}, "Token / API key", Modifier.fillMaxWidth(), kind = TvTextKind.SECRET)
                        TvActionRow(onClick = {}, modifier = Modifier.testTag("test"), tv = true) { button ->
                            Button(onClick = {}, modifier = button) { Text("Test and save") }
                        }
                        TvActionRow(onClick = { removed++ }, modifier = Modifier.testTag("remove"), tv = true) { button ->
                            OutlinedButton(onClick = {}, modifier = button) { Text("Remove") }
                        }
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth().testTag("next")) { Text("Your listening history") }
                    }
                }
            }
        }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.onNodeWithTag("tvTextBox:Token / API key").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.waitForIdle()
        fun on(): String = listOf("tvTextBox:Token / API key" to "Token", "test" to "Test", "remove" to "Remove", "next" to "Next")
            .firstOrNull { (tag, _) ->
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true) and androidx.compose.ui.test.hasTestTag(tag))
                    .fetchSemanticsNodes().isNotEmpty()
            }?.second ?: "none"
        val start = on()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val down = on()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val downAgain = on()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val pressed = removed
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val past = on()
        press(KeyEvent.KEYCODE_DPAD_UP)
        val back = on()
        val trail = "start: $start · down: $down · down: $downAgain · down: $past · up: $back"
        println("TV Koito remove focus: $trail")
        assertTrue("Starts on the token ($trail)", start == "Token")
        assertTrue("Down from the token reaches Test and save ($trail)", down == "Test")
        assertTrue("Down from Test and save reaches Remove, not the next section ($trail)", downAgain == "Remove")
        assertTrue("OK on Remove removes ($trail)", pressed == 1)
        assertTrue("Down from Remove goes on to the next section ($trail)", past == "Next")
        assertTrue("Up from the next section comes back to Remove ($trail)", back == "Remove")
    }
}
