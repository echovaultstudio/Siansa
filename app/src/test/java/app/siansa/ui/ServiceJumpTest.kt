package app.siansa.ui

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A service's tile on Settings opens Connections at that service (the owner, 15 Nov: its card was opened, but the
 * page stayed at its top and "I still need to scroll to each field").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ServiceJumpTest {
    @get:Rule val compose = createComposeRule()

    /** A page as Connections is: things above, the cards, more below. */
    private fun page(open: String?): () -> ScrollState {
        lateinit var scroll: ScrollState
        compose.setContent {
            scroll = rememberScrollState()
            Column(Modifier.size(360.dp, 600.dp).verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(900.dp))
                for (key in listOf("beats", "ai")) {
                    FoldCard(Icons.Rounded.Speed, key, "set up", expanded = key == open, onToggle = {}, tag = "fold-$key", jumpTo = scroll.takeIf { key == open }) {
                        Box(Modifier.fillMaxWidth().height(200.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(900.dp))
            }
        }
        compose.waitForIdle()
        return { scroll }
    }

    @Test fun thePageOpensAtTheServiceThatWasTapped() {
        val scroll = page("ai")
        assertTrue("the page moved", scroll().value > 0)
        compose.onNodeWithTag("fold-ai-header").assertIsDisplayed()
        // Right at the top of the page, with its fields below it.
        assertEquals(0f, compose.onNodeWithTag("fold-ai").getBoundsInRoot().top.value, 1f)
        compose.onNodeWithTag("fold-ai-body").assertIsDisplayed()
    }

    @Test fun itJumpsOnceAndThenStaysWhereItsPut() {
        val scroll = page("beats")
        val there = scroll().value
        assertTrue(there > 0)
        compose.runOnIdle { runBlocking { scroll().scrollTo(0) } }
        compose.waitForIdle()
        assertEquals("scrolled back up, it stays up", 0, scroll().value)
    }

    @Test fun openedFromItsOwnRowThePageStaysAtItsTop() {
        assertEquals(0, page(null)().value)
    }
}
