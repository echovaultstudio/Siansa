package app.siansa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SimilarAlbumsPainter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The "Find similar albums" icon: a waveform with an "A" cut out of it (issue #239). */
@RunWith(AndroidJUnit4::class)
class SimilarAlbumsIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theAIsCutOutOfTheWaveform() {
        compose.setContent {
            Box(Modifier.size(96.dp).background(Color.Black).testTag("icon")) {
                Icon(SimilarAlbumsPainter(), null, tint = Color.White, modifier = Modifier.size(96.dp))
            }
        }
        val pixels = compose.onNodeWithTag("icon").captureToImage().toPixelMap()
        val unit = pixels.width / 24f
        fun bright(x: Float, y: Float) = pixels[(x * unit).toInt(), (y * unit).toInt()].red > 0.5f
        // The middle bar, below the "A": drawn.
        assertTrue("the middle bar shows below the A", bright(12f, 20.5f))
        // The A's crossbar runs through the middle bar at y = 14: cut out, so the background shows.
        assertTrue("the A's crossbar is cut out of the middle bar", !bright(12f, 14f))
        // Between the bars there is nothing, A or not.
        assertTrue("nothing between the bars", !bright(4.9f, 12f))
    }
}
