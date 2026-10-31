package app.siansa.ui

import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Arrange Home's spacing slider (#554): five steps of the spacing scale, Normal by default. */
class HomeGapTest {
    @Test fun theStepsAreTheScale() {
        assertEquals(listOf(Space.Small, Space.Medium, Space.Page, Space.Section, Space.Large), (0..4).map { homeGap(it) })
        assertEquals(listOf("Tight", "Compact", "Normal", "Roomy", "Airy"), (0..4).map { homeGapLabel(it) })
    }

    @Test fun theDefaultIsNormalAndOutOfRangeStepsClamp() {
        assertEquals(Space.Page, homeGap(AppSettings.HOME_GAP_DEFAULT))
        assertEquals("Normal", homeGapLabel(AppSettings().homeGap))
        assertEquals(Space.Small, homeGap(-3))
        assertEquals(Space.Large, homeGap(9))
    }

    /** The label's width is measured from every step's name (issue #694): the list and the names agree. */
    @Test fun everyStepsNameIsInTheList() {
        assertEquals(HOME_GAP_LABELS, (0..AppSettings.HOME_GAP_MAX).map { homeGapLabel(it) })
        assertEquals("Airy", homeGapLabel(99))
    }
}
