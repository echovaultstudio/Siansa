package app.siansa.ui

import app.siansa.data.HomeSection
import app.siansa.mix.TimeCard
import app.siansa.mix.TimeCardList
import app.siansa.mix.TimeListKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** On this day (the owner, 27 Nov: "similar to how photo apps show photos from the year before"). */
class OnThisDayTest {
    private fun list(kind: TimeListKind, title: String, line: String, songs: Int, month: Int? = null) =
        TimeCardList(kind.id, title, "TAG", line, (1..songs).map { "s$it" }, year = 2025, month = month)

    private val day = list(TimeListKind.DAY, "This day last year", "27 November 2025", 12, 11)
    private val month = list(TimeListKind.YEAR_AGO, "This time last year", "November 2025", 50, 11)
    private val lastMonth = list(TimeListKind.LAST_MONTH, "Last month", "October 2026", 50, 10)
    private val year = list(TimeListKind.LAST_YEAR, "Your 2025", "2025", 50)

    /** The day, this month last year and last year, in that order, whatever order the card keeps them in. */
    @Test fun itShowsTheThreeInItsOrder() {
        val card = TimeCard("2026-11-27", listOf(month, day, lastMonth, year))
        assertEquals(listOf(day, month, year), OnThisDay.lists(card))
        // One missing (a day with nothing played): the others.
        assertEquals(listOf(month, year), OnThisDay.lists(card.copy(lists = listOf(month, lastMonth, year))))
        assertEquals(emptyList<TimeCardList>(), OnThisDay.lists(null))
    }

    @Test fun eachCardSaysWhatItIs() {
        assertEquals(OnThisDay.Words("A year ago today", "27 Nov 2025", "1 YEAR AGO TODAY"), OnThisDay.words(day, Locale.UK))
        assertEquals(OnThisDay.Words("November, last year", "50 songs", "1 YEAR AGO"), OnThisDay.words(month, Locale.UK))
        assertEquals(OnThisDay.Words("Your 2025", "50 songs", "LAST YEAR"), OnThisDay.words(year, Locale.UK))
        assertEquals("1 song", OnThisDay.words(list(TimeListKind.LAST_YEAR, "Your 2025", "2025", 1)).line)
    }

    /** The day's card is the one new each morning. */
    @Test fun onlyTheDayIsNew() {
        assertTrue(OnThisDay.isNew(day))
        assertFalse(OnThisDay.isNew(month))
        assertFalse(OnThisDay.isNew(year))
    }

    /** Its own row on Home, after Your listening; its look is its own, and it needs the day's lists made. */
    @Test fun itIsARowOfItsOwn() {
        val order = HomeSection.default
        assertEquals(order.indexOf(HomeSection.TIME_MACHINE) + 1, order.indexOf(HomeSection.ON_THIS_DAY))
        assertFalse(HomeSection.ON_THIS_DAY.lookable)
        assertTrue(HomeSection.ON_THIS_DAY.usesTimeCard)
        assertTrue(HomeSection.TIME_MACHINE.usesTimeCard)
        assertFalse(HomeSection.RADIOS.usesTimeCard)
        // A saved order from before gets it after Your listening.
        val saved = HomeSection.parse("quick,time_machine,radios")
        assertEquals(saved.indexOf(HomeSection.TIME_MACHINE) + 1, saved.indexOf(HomeSection.ON_THIS_DAY))
    }

    /** Its three styles from the mockup (the owner, 27 Nov), Stories as it comes; one unknown is Stories. */
    /** Artists' pictures with the switch on and artists in the library (the owner, 27 Nov), else the covers. */
    @Test fun itPicturesArtistsOrAlbums() {
        val faces = listOf(app.siansa.mix.CardArtist("One", 9, "ar1"), app.siansa.mix.CardArtist("Not in the library", 5), app.siansa.mix.CardArtist("Two", 3, "ar2", coverArt = "ar-2_x"))
        val withBoth = month.copy(covers = listOf("al-1", "al-2"), artists = faces)
        assertEquals(OnThisDay.Pictures(listOf("al-1", "al-2"), round = false), OnThisDay.pictures(withBoth, artists = false))
        assertEquals(OnThisDay.Pictures(listOf("ar-ar1", "ar-2_x"), round = true), OnThisDay.pictures(withBoth, artists = true))
        // A card made before it kept artists shows its covers either way.
        assertEquals(OnThisDay.Pictures(listOf("al-1"), round = false), OnThisDay.pictures(month.copy(covers = listOf("al-1")), artists = true))
    }

    /** Its pictures' layouts (the owner, 27 Nov: "some options"): Collage as it comes. */
    @Test fun itsPicturesHaveLayouts() {
        assertEquals(listOf("Collage", "Grid", "Top one", "Fan"), MemoryPicture.entries.map { it.label })
        assertEquals(MemoryPicture.COLLAGE, MemoryPicture.of(null))
        assertEquals(MemoryPicture.COLLAGE, MemoryPicture.of("nope"))
        assertEquals(MemoryPicture.FAN, MemoryPicture.of("fan"))
    }

    /** NEW goes once today's card was opened or played (the owner, 27 Nov), and comes back with the next day's card. */
    @Test fun newGoesOnceSeen() {
        assertTrue(OnThisDay.isNew(day))
        assertFalse(OnThisDay.isNew(day, seen = true))
        assertFalse(OnThisDay.isNew(month))
        assertTrue(OnThisDay.seen("2026-11-27", "2026-11-27"))
        assertFalse(OnThisDay.seen("2026-11-26", "2026-11-27"))
        assertFalse(OnThisDay.seen("", "2026-11-27"))
        assertFalse(OnThisDay.seen("", null))
    }

    @Test fun itHasThreeStyles() {
        assertEquals(listOf("Stories", "Spotlight", "Wide"), MemoryStyle.entries.map { it.label })
        assertEquals(MemoryStyle.STORIES, MemoryStyle.of(null))
        assertEquals(MemoryStyle.STORIES, MemoryStyle.of("nope"))
        assertEquals(MemoryStyle.SPOTLIGHT, MemoryStyle.of("spotlight"))
        // Prints, which Spotlight took the place of, is Spotlight.
        assertEquals(MemoryStyle.SPOTLIGHT, MemoryStyle.of("prints"))
        assertEquals(MemoryStyle.WIDE, MemoryStyle.of("wide"))
    }

    /** Spotlight (the owner, 27 Nov): the date huge behind, NEW while new, and who's on it under the title. */
    @Test fun spotlightSaysWhatAndWho() {
        assertEquals("27", OnThisDay.backdrop(day.copy(tag = "27 NOV")))
        assertEquals("NOV", OnThisDay.backdrop(month.copy(tag = "NOV 2025")))
        assertEquals("2025", OnThisDay.backdrop(year.copy(tag = "2025")))
        assertEquals("NEW · 27 NOV 2025", OnThisDay.spotlightKicker(day, locale = Locale.ENGLISH))
        assertEquals("1 YEAR AGO TODAY", OnThisDay.spotlightKicker(day, seen = true, locale = Locale.ENGLISH))
        assertEquals("1 YEAR AGO", OnThisDay.spotlightKicker(month, locale = Locale.ENGLISH))
        fun with(vararg names: String) = month.copy(artists = names.map { app.siansa.mix.CardArtist(it, 1) })
        assertEquals("50 songs", OnThisDay.spotlightLine(month, Locale.ENGLISH))
        assertEquals("12 songs", OnThisDay.spotlightLine(day, Locale.ENGLISH))
        assertEquals("Deftones", OnThisDay.spotlightLine(with("Deftones"), Locale.ENGLISH))
        assertEquals("Deftones and Moderat", OnThisDay.spotlightLine(with("Deftones", "Moderat"), Locale.ENGLISH))
        assertEquals("Deftones, Moderat and 2 more", OnThisDay.spotlightLine(with("Deftones", "Moderat", "Bicep", "Burial"), Locale.ENGLISH))
    }
}
