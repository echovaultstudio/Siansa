package app.siansa.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where a row's carousel puts each name (the owner, 19 Nov): kept per row, under the covers unless set, cleared by reset. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CarouselNamesSettingTest {
    private fun settings() = SettingsRepository(ApplicationProvider.getApplicationContext())

    @Test fun keptPerRowAndClearedByReset() {
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselOnCover)
        settings().setHomeCarouselOnCover(HomeSection.MADE_FOR_YOU, true)
        settings().setHomeCarouselOnCover(HomeSection.CAROUSEL, true)
        settings().setHomeCarouselOnCover(HomeSection.CAROUSEL, false)
        assertEquals(setOf(HomeSection.MADE_FOR_YOU), settings().state.value.homeCarouselOnCover)
        // Big or compact, kept per row the same way (the owner, 20 Nov).
        settings().setHomeCarouselCompact(HomeSection.CAROUSEL, true)
        assertEquals(setOf(HomeSection.CAROUSEL), settings().state.value.homeCarouselCompact)
        settings().resetHomeLooks()
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselOnCover)
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselCompact)
    }
}
