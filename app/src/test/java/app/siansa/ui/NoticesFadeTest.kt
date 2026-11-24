package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticesFadeTest {
    @Test
    fun `a chip the fade has hidden is not under the finger`() {
        // The chips' room is 300 wide and its last 48 fade out: the first quarter of the fade still shows the chip.
        assertEquals(false, underFade(x = 100f, room = 300f, fade = 48f, more = true))
        assertEquals(false, underFade(x = 260f, room = 300f, fade = 48f, more = true)) // where the fade begins
        assertEquals(true, underFade(x = 270f, room = 300f, fade = 48f, more = true))
        assertEquals(true, underFade(x = 299f, room = 300f, fade = 48f, more = true)) // the very edge of the menu button
    }

    @Test
    fun `with no more chips to scroll to nothing is hidden`() {
        assertEquals(false, underFade(x = 299f, room = 300f, fade = 48f, more = false))
    }
}
