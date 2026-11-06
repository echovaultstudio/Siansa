package app.siansa

import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SiansaTheme
import app.siansa.ui.TvTextBox
import app.siansa.ui.TvTextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The TV's text boxes for keys, tokens and passwords show dots, not what's typed (issue #957). */
@RunWith(AndroidJUnit4::class)
class TvTextBoxSecretTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun editTexts(view: View): List<EditText> = when (view) {
        is EditText -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { editTexts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun secretsAreHidden() {
        compose.setContent {
            SiansaTheme {
                Column {
                    TvTextBox("abc-key", {}, "API key", kind = TvTextKind.SECRET)
                    TvTextBox("navidrome.home", {}, "Server address", kind = TvTextKind.ADDRESS)
                }
            }
        }
        compose.waitForIdle()
        val boxes = editTexts(compose.activity.window.decorView)
        assertEquals(2, boxes.size)
        assertTrue("the key shows as dots", boxes[0].transformationMethod is PasswordTransformationMethod)
        assertTrue("an address shows as typed", boxes[1].transformationMethod !is PasswordTransformationMethod)
    }
}
