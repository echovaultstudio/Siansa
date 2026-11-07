package app.siansa

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.ui.RemoteAction
import app.siansa.ui.SiansaTheme
import app.siansa.ui.TvRemoteStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The TV's now playing (issue #974): no buttons, the remote acts straight away; down opens the menu, OK plays and
 * pauses, left and right skip. Sent as the remote sends them, out of touch mode.
 */
@RunWith(AndroidJUnit4::class)
class TvRemoteStageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun press(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    @Test fun downOpensTheMenu() {
        var plays = 0
        var skips = 0
        compose.setContent {
            var menu by remember { mutableStateOf(false) }
            SiansaTheme {
                TvRemoteStage(onAction = { a ->
                    when (a) {
                        RemoteAction.MENU -> menu = true
                        RemoteAction.PLAY_PAUSE -> plays++
                        RemoteAction.PREVIOUS -> skips--
                        RemoteAction.NEXT -> skips++
                    }
                }) {
                    Column {
                        Text("Clubfood")
                        if (menu) Text("Like", Modifier.testTag("menu"))
                    }
                }
            }
        }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.waitForIdle()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("OK plays and pauses", 1, plays)
        assertEquals("right twice, left once", 1, skips)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("menu").assertIsDisplayed()
    }

    /** Issue #984: held, right winds forward through the song instead of skipping; let go, it plays from there. */
    @Test fun holdingRightWinds() {
        var skips = 0
        var wound = 0L
        var done = 0
        compose.setContent {
            SiansaTheme {
                TvRemoteStage(
                    onAction = { a -> if (a == RemoteAction.NEXT || a == RemoteAction.PREVIOUS) skips++ },
                    onWind = { wound += it },
                    onWindDone = { done++ },
                ) { Text("Clubfood") }
            }
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(false)
        compose.waitForIdle()
        // As the remote sends a held key: down, then repeats, then up a second and a half later.
        val start = android.os.SystemClock.uptimeMillis()
        val code = KeyEvent.KEYCODE_DPAD_RIGHT
        instrumentation.sendKeySync(KeyEvent(start, start, KeyEvent.ACTION_DOWN, code, 0))
        for (i in 1..10) {
            val at = start + 500 + i * 100L
            instrumentation.sendKeySync(KeyEvent(start, at, KeyEvent.ACTION_DOWN, code, i))
        }
        instrumentation.sendKeySync(KeyEvent(start, start + 1_600, KeyEvent.ACTION_UP, code, 0))
        compose.waitForIdle()
        assertEquals("Holding doesn't skip", 0, skips)
        assertEquals("Letting go plays from where it's wound to", 1, done)
        assertTrue("It winds forward, at ten times ($wound ms)", wound in 10_000L..20_000L)
        // A press still skips.
        press(code)
        assertEquals("A press skips", 1, skips)
    }
}
