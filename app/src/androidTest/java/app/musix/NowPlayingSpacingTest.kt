package app.musix

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.musix.ui.MusixTheme
import app.musix.ui.StreamBadge
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Even spacing under the seek bar in now playing, measured on a real screen. */
@RunWith(AndroidJUnit4::class)
class NowPlayingSpacingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formatBadgeIsCentredAndNoTallerThanTheTimes() {
        compose.setContent {
            MusixTheme {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("1:23", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    StreamBadge("FLAC", converted = false)
                    Text("-2:34", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
            }
        }
        val time = compose.onNodeWithText("1:23").getBoundsInRoot()
        val badge = compose.onNodeWithText("FLAC").getBoundsInRoot()
        val timeHeight = time.bottom - time.top
        val badgeHeight = badge.bottom - badge.top
        assertTrue("Badge ${badgeHeight.value} dp tall, times ${timeHeight.value} dp", badgeHeight <= timeHeight + 1.dp)
        val centreGap = ((badge.top + badge.bottom) - (time.top + time.bottom)) / 2
        assertTrue("Badge sits ${centreGap.value} dp off the times' centre", abs(centreGap.value) <= 1f)
        val left = badge.left - time.right
        val right = compose.onNodeWithText("-2:34").getBoundsInRoot().left - badge.right
        assertTrue("Badge is ${left.value} dp from the left time but ${right.value} dp from the right", abs(left.value - right.value) <= 1f)
    }
}
