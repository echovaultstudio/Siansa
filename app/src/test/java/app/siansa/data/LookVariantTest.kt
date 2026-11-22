package app.siansa.data

import androidx.test.core.app.ApplicationProvider
import app.siansa.ui.shortcutKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A look's own layout per row (the owner, 20 Nov): shortcut tiles, a cover grid with one featured, a hero banner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LookVariantTest {
    @Test fun eachLookHasItsOwnFirstOneAsBefore() {
        assertEquals(listOf("Two a row", "Three a row", "Big tiles", "Pill chips"), LookVariant.of(HomeLook.GRID).map { it.label })
        assertEquals(listOf(LookVariant.EVEN, LookVariant.FEATURED), LookVariant.of(HomeLook.COVER_GRID))
        assertEquals(listOf(LookVariant.CARD, LookVariant.HERO, LookVariant.HERO_THREE, LookVariant.HERO_FOUR), LookVariant.of(HomeLook.BANNER))
        assertEquals(emptyList<LookVariant>(), LookVariant.of(HomeLook.LIST))
    }

    @Test fun keptAndReadBack() {
        val saved = mapOf(HomeSection.QUICK to LookVariant.CHIPS, HomeSection.MADE_FOR_YOU to LookVariant.FEATURED)
        assertEquals(saved, LookVariant.parse(LookVariant.format(saved)))
        assertEquals(mapOf(HomeSection.QUICK to LookVariant.THREE), LookVariant.parse("quick:three,nothing:hero,made_for_you:sideways"))
        assertEquals(emptyMap<HomeSection, LookVariant>(), LookVariant.parse(null))
    }

    @Test fun keptPerRowAndClearedByReset() {
        fun settings() = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings().setHomeVariant(HomeSection.QUICK, LookVariant.BIG)
        settings().setHomeVariant(HomeSection.MADE_FOR_YOU, LookVariant.HERO)
        assertEquals(mapOf(HomeSection.QUICK to LookVariant.BIG, HomeSection.MADE_FOR_YOU to LookVariant.HERO), settings().state.value.homeVariants)
        settings().resetHomeLooks()
        assertEquals(emptyMap<HomeSection, LookVariant>(), settings().state.value.homeVariants)
    }

    @Test fun aBigShortcutSaysWhatItIs() {
        assertEquals("Your hearts", shortcutKind("liked"))
        assertEquals("Mix", shortcutKind("mix/abc"))
        assertEquals("Album", shortcutKind("album/1"))
        assertEquals("Radio", shortcutKind("radio/mood/chill"))
        assertEquals("", shortcutKind("somewhere"))
    }

    @Test fun aHeroCanHaveThreeOrFourCardsUnderItWithPicturesOfTheirOwn() {
        // The owner, 22 Nov: "Extended hero looks awesome. Let users choose different pictures for main top card and
        // the bottom ones also".
        assertEquals(listOf(0, 0, 3, 4), LookVariant.of(HomeLook.BANNER).map { it.cardsUnder })
        assertEquals(listOf(false, true, true, true), LookVariant.of(HomeLook.BANNER).map { it.hero })
        assertEquals(mapOf(HomeSection.MADE_FOR_YOU to LookVariant.HERO_FOUR), LookVariant.parse("made:hero4,quick:hero9"))
        // Cards share the row: 358 px wide with 8 between, as in the mockup.
        assertEquals(114, app.siansa.ui.heroCardSide(358, 8, 3))
        assertEquals(83, app.siansa.ui.heroCardSide(358, 8, 4))
        fun settings() = SettingsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        settings().setHomePicture(HomeSection.MADE_FOR_YOU, PictureStyle.PILE)
        settings().setHomeHeroCardPicture(HomeSection.MADE_FOR_YOU, PictureStyle.AURA)
        assertEquals(PictureStyle.PILE, settings().state.value.homePictures[HomeSection.MADE_FOR_YOU])
        assertEquals(PictureStyle.AURA, settings().state.value.homeHeroCardPictures[HomeSection.MADE_FOR_YOU])
        settings().resetHomeLooks()
        assertEquals(emptyMap<HomeSection, PictureStyle>(), settings().state.value.homeHeroCardPictures)
    }

    @Test fun aRowsCardsCanHideTheirLines() {
        // The owner, 22 Nov: "make it possible to hide the descriptions on any card if they are present".
        fun settings() = SettingsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeNoLines)
        settings().setHomeLinesShown(HomeSection.MADE_FOR_YOU, false)
        assertEquals(setOf(HomeSection.MADE_FOR_YOU), settings().state.value.homeNoLines)
        settings().setHomeLinesShown(HomeSection.MADE_FOR_YOU, true)
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeNoLines)
        settings().setHomeLinesShown(HomeSection.QUICK, false)
        settings().resetHomeLooks()
        assertEquals(emptySet<HomeSection>(), settings().state.value.homeNoLines)
    }
}
