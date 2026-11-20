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
        assertEquals(listOf(LookVariant.CARD, LookVariant.HERO), LookVariant.of(HomeLook.BANNER))
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
}
