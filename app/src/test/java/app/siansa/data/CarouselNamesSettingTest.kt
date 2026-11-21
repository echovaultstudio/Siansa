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
        settings().setHomeCarouselSize(HomeSection.CAROUSEL, CrateSize.COMPACT)
        settings().setHomeCarouselSize(HomeSection.MADE_FOR_YOU, CrateSize.SMALL)
        assertEquals(CrateSize.COMPACT, settings().state.value.carouselSize(HomeSection.CAROUSEL))
        assertEquals(CrateSize.SMALL, settings().state.value.carouselSize(HomeSection.MADE_FOR_YOU))
        assertEquals(CrateSize.BIG, settings().state.value.carouselSize(HomeSection.MORE_OF_YOU))
        // One size at a time: small after compact is small only.
        settings().setHomeCarouselSize(HomeSection.CAROUSEL, CrateSize.SMALL)
        assertEquals(CrateSize.SMALL, settings().state.value.carouselSize(HomeSection.CAROUSEL))
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselCompact)
        settings().resetHomeLooks()
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselOnCover)
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselCompact)
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeCarouselSmall)
    }

    @Test fun namesAreOneChoiceForEveryLook() {
        // The owner, 21 Nov: Names for every look with pictures; a carousel's older "on the cover" reads as On.
        fun settings() = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings().setHomeCarouselOnCover(HomeSection.CAROUSEL, true)
        assertEquals(CardNames.ON, settings().state.value.cardNames(HomeSection.CAROUSEL))
        settings().setHomeNames(HomeSection.CAROUSEL, CardNames.UNDER)
        settings().setHomeNames(HomeSection.MADE_FOR_YOU, CardNames.ON)
        assertEquals(CardNames.UNDER, settings().state.value.cardNames(HomeSection.CAROUSEL))
        assertEquals(CardNames.ON, settings().state.value.cardNames(HomeSection.MADE_FOR_YOU))
        assertEquals(CardNames.AUTO, settings().state.value.cardNames(HomeSection.QUICK))
        assertEquals(mapOf(HomeSection.QUICK to CardNames.ON), CardNames.parse("quick:on,nowhere:under,made_for_you:sideways"))
        settings().resetHomeLooks()
        assertEquals(CardNames.AUTO, settings().state.value.cardNames(HomeSection.MADE_FOR_YOU))
    }
}
