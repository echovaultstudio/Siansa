package app.siansa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.playback.PlayerState
import app.siansa.ui.Controls
import app.siansa.ui.LocalGraph
import app.siansa.ui.SiansaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Now playing's repeat and add to playlist buttons give a short buzz when pressed (issue #771). */
@RunWith(AndroidJUnit4::class)
class PlayerHapticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatAndAddToPlaylistBuzz() {
        val graph = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val felt = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { felt += hapticFeedbackType }
        }
        compose.setContent {
            CompositionLocalProvider(LocalGraph provides graph, LocalHapticFeedback provides haptics) {
                SiansaTheme { Box(Modifier.width(360.dp)) { Controls(PlayerState()) } }
            }
        }
        compose.onNodeWithTag("repeatButton").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.LongPress), felt)
        compose.onNodeWithTag("addToPlaylistButton").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.LongPress, HapticFeedbackType.LongPress), felt)
    }
}
