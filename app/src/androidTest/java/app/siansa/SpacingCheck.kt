package app.siansa

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlin.math.abs

/**
 * What the spacing tests share (issue #714): a screen to draw on and the check that two gaps match. Each area of
 * the app has its own file of checks (SpacingHomeTest, SpacingPlayerTest, SpacingSettingsTest, SpacingLibraryTest,
 * SpacingListeningTest), so changes to different pages don't meet in one long file. Add a check to the file of the
 * page you change, for any visual change that places things next to each other.
 */
abstract class SpacingCheck {
    @get:Rule val compose = createComposeRule()

    protected fun assertEven(what: String, above: Dp, below: Dp, tolerance: Dp = 3.dp) {
        assertTrue("$what: ${above.value} dp above but ${below.value} dp below", abs(above.value - below.value) <= tolerance.value)
    }
}
