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
    @Test fun itHasThreeStyles() {
        assertEquals(listOf("Stories", "Prints", "Wide"), MemoryStyle.entries.map { it.label })
        assertEquals(MemoryStyle.STORIES, MemoryStyle.of(null))
        assertEquals(MemoryStyle.STORIES, MemoryStyle.of("nope"))
        assertEquals(MemoryStyle.PRINTS, MemoryStyle.of("prints"))
        assertEquals(MemoryStyle.WIDE, MemoryStyle.of("wide"))
        // Prints lean one way, the other, a little the first: never all alike.
        assertEquals(listOf(-2f, 1.5f, -1f, -2f), (0..3).map { printLean(it) })
    }
}
