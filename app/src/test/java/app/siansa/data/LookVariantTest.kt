package app.siansa.data

import androidx.test.core.app.ApplicationProvider
import app.siansa.ui.shortcutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** The owner, 23 Nov: "shortcuts don't get their colour changes at all. All changes and settings must be possible for all cards." */
    @Test fun shortcutTilesTakeTheirRowsColours() {
        val look = { card: app.siansa.data.CardLook?, colours: PictureColours? -> app.siansa.ui.shortcutLookOf(card, colours) }
        // As they came: plain, with the covers' own colours and no colour of the row's.
        assertEquals(null, look(null, null))
        assertEquals(null, look(null, PictureColours.COVERS))
        // Colours chosen for the row show on its tiles, without a Card colour having to be picked first.
        for (chosen in listOf(PictureColours.MOOD, PictureColours.SIANSA, PictureColours.MONO)) assertEquals(app.siansa.data.CardLook.AS_HOME, look(null, chosen))
        // The row's own Card colour stays the look, whatever its Colours.
        assertEquals(app.siansa.data.CardLook.PLUM, look(app.siansa.data.CardLook.PLUM, PictureColours.MONO))
        assertEquals(app.siansa.data.CardLook.INK, look(app.siansa.data.CardLook.INK, PictureColours.SIANSA))
        // Words stay readable on the colours: dark on pale ones (frost, light grey), white on deep ones.
        val c = { v: Long -> androidx.compose.ui.graphics.Color(v) }
        assertTrue(app.siansa.ui.wordsDarkOn(c(0xFFD8E3EC) to c(0xFF6F86A0)))
        assertTrue(app.siansa.ui.wordsDarkOn(c(0xFFF2F2F3) to c(0xFF8A8A90)))
        assertTrue(app.siansa.ui.wordsDarkOn(c(0xFFFFB547) to c(0xFFD8E3EC)))
        assertFalse(app.siansa.ui.wordsDarkOn(c(0xFF4B2BD6) to c(0xFF2A1470)))
        assertFalse(app.siansa.ui.wordsDarkOn(c(0xFF7A2E3A) to c(0xFF14181C)))
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

    @Test fun aRowsSettingsSayWhatItIsAndGoBackToHowItCame() {
        // The owner, 22 Nov: a tidier cog, each choice a line with what's chosen, and "Reset this row".
        assertEquals("Hero and 3 cards · Stickers", app.siansa.ui.rowSummary(HomeLook.BANNER, LookVariant.HERO_THREE, PictureStyle.PILE))
        assertEquals("Carousel", app.siansa.ui.rowSummary(HomeLook.CAROUSEL, null, PictureStyle.AUTO))
        assertEquals("Cover grid · Aura", app.siansa.ui.rowSummary(HomeLook.COVER_GRID, LookVariant.FEATURED, PictureStyle.AURA))
        assertEquals(listOf("Card", "Hero", "Hero + 3", "Hero + 4"), LookVariant.of(HomeLook.BANNER).map { app.siansa.ui.shortLabel(it) })
        fun settings() = SettingsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        for (section in listOf(HomeSection.MADE_FOR_YOU, HomeSection.QUICK)) {
            settings().setHomeLook(section, HomeLook.BANNER)
            settings().setHomeVariant(section, LookVariant.HERO_FOUR)
            settings().setHomePicture(section, PictureStyle.FROST)
            settings().setHomeHeroCardPicture(section, PictureStyle.AURA)
            settings().setHomePictureColours(section, PictureColours.MONO)
            settings().setHomeNames(section, CardNames.ON)
            settings().setHomeLinesShown(section, false)
            settings().setHomeTitleShown(section, false)
        }
        settings().resetHomeRow(HomeSection.MADE_FOR_YOU)
        val s = settings().state.value
        // That row is as it came; the other keeps everything.
        assertEquals(setOf(HomeSection.QUICK), s.homeLooks.keys)
        assertEquals(mapOf(HomeSection.QUICK to LookVariant.HERO_FOUR), s.homeVariants)
        assertEquals(mapOf(HomeSection.QUICK to PictureStyle.FROST), s.homePictures)
        assertEquals(mapOf(HomeSection.QUICK to PictureStyle.AURA), s.homeHeroCardPictures)
        assertEquals(mapOf(HomeSection.QUICK to PictureColours.MONO), s.homePictureColours)
        assertEquals(mapOf(HomeSection.QUICK to CardNames.ON), s.homeNames)
        assertEquals(setOf(HomeSection.QUICK), s.homeNoLines)
        assertEquals(setOf(HomeSection.QUICK), s.homeUntitled)
        settings().resetHomeLooks()
    }
}
