package app.siansa.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** Behind Home's open menu (issue #959): a light dim with the blur, a darker one where there's no blur. */
class HomeMenuDimTest {
    @Test
    fun darkerWithoutTheBlur() {
        assertTrue(homeMenuDim(blurs = false) > homeMenuDim(blurs = true))
        assertTrue(homeMenuDim(blurs = true) in 0.2f..0.45f)
        // A little blur, not a smear.
        assertTrue(HOME_MENU_BLUR.value in 4f..10f)
    }
}
