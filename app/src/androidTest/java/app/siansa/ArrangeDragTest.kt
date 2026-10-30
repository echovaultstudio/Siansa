package app.siansa

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.ArrangeList
import app.siansa.ui.SiansaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rearranging (issue #701): a row moves on every drag, not only the first. The list is hosted like the Nav Bar page,
 * whose order comes back from the settings after each drop.
 */
@RunWith(AndroidJUnit4::class)
class ArrangeDragTest {
    @get:Rule val compose = createComposeRule()

    private val pitch = 60.dp

    @Test fun rowsMoveDragAfterDrag() {
        var saved = listOf("A", "B", "C", "D", "E")
        compose.setContent {
            SiansaTheme {
                var order by remember { mutableStateOf(saved) }
                Column {
                    ArrangeList(
                        order = order,
                        shown = order.toSet(),
                        label = { it },
                        icon = { Icons.Rounded.Home },
                        key = { it },
                        canHide = { true },
                        canShowMore = true,
                        onChange = { o, _ -> order = o.toList(); saved = order },
                    )
                }
            }
        }
        val step = with(compose.density) { pitch.toPx() }
        fun drag(label: String, slots: Int) {
            compose.onNodeWithContentDescription("Move $label").performTouchInput {
                down(center)
                // Past the touch slop, then the slots, in small steps like a finger.
                repeat(10) { moveBy(Offset(0f, step * slots / 10f + if (it == 0) 12f * (if (slots < 0) -1 else 1) else 0f)) }
                up()
            }
            compose.waitForIdle()
        }
        drag("A", 2)
        assertEquals(listOf("B", "C", "A", "D", "E"), saved)
        // The second and third drags used to move a stale copy: the list didn't change.
        drag("E", -3)
        assertEquals(listOf("B", "E", "C", "A", "D"), saved)
        drag("B", 1)
        assertEquals(listOf("E", "B", "C", "A", "D"), saved)
    }
}
