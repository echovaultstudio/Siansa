package app.siansa.mix

import app.siansa.data.HomeSection
import app.siansa.data.Song
import app.siansa.ui.timeCardUnder
import app.siansa.ui.timeMachineStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Home's Time Machine card (issue #1341): its lists, what it keeps, and when it's made again. */
class TimeCardTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val plans = timeCardPlans(today, Locale.ENGLISH)
    private fun songs(n: Int, tag: String = "s") = (1..n).map { Song(id = "$tag$it", title = "Song $it", coverArt = "c${it % 3}") }

    @Test fun theListsInTheOrderTheyAreTried() {
        assertEquals(listOf("This time last year", "This day last year", "Last month", "Your 2025", "Two years ago", "Five years ago"), plans.map { it.title })
        assertEquals(listOf("NOV 2025", "23 NOV", "OCT", "2025", "NOV 2024", "NOV 2021"), plans.map { it.tag })
        assertEquals(listOf("November 2025", "23 November 2025", "October 2026", "2025", "November 2024", "November 2021"), plans.map { it.line })
    }

    @Test fun eachListCoversItsDays() {
        val (yearAgo, day, lastMonth, lastYear) = plans
        assertEquals(LocalDate.of(2025, 11, 1) to LocalDate.of(2025, 12, 1), yearAgo.window.from to yearAgo.window.to)
        assertEquals(LocalDate.of(2025, 11, 23) to LocalDate.of(2025, 11, 24), day.window.from to day.window.to)
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 11, 1), lastMonth.window.from to lastMonth.window.to)
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 1, 1), lastYear.window.from to lastYear.window.to)
    }

    /** A month's and a year's plays are kept under the Time Machine's own keys, so the page and the card share them. */
    @Test fun monthsAndYearsShareTheTimeMachinesKeys() {
        assertEquals(listOf("2025-11", "2025-11-23", "2026-10", "2025-all", "2024-11", "2021-11"), plans.map { it.key })
        assertEquals(listOf(11, 11, 10, null, 11, 11), plans.map { it.month })
        assertEquals("timemachine?year=2025&month=11", timeMachineRoute(2025, 11))
        assertEquals("timemachine?year=2025&month=0", timeMachineRoute(2025, null))
    }

    @Test fun januaryLooksBackOverTheYearsEnd() {
        val january = timeCardPlans(LocalDate.of(2027, 1, 5), Locale.ENGLISH)
        assertEquals(listOf("JAN 2026", "5 JAN", "DEC", "2026"), january.take(4).map { it.tag })
        assertEquals("2026-12", january[2].key)
        // The 29th of February a year on is the 28th.
        assertEquals("2027-02-28", timeCardPlans(LocalDate.of(2028, 2, 29), Locale.ENGLISH)[1].key)
    }

    @Test fun theFirstFourWithEnoughSongsMakeTheCard() = runBlocking {
        val asked = ArrayList<TimeListKind>()
        val card = buildTimeCard(today, plans) { plan ->
            asked += plan.kind
            when (plan.kind) {
                TimeListKind.YEAR_AGO -> songs(60)
                TimeListKind.DAY -> songs(2)   // too few for a day: left out
                TimeListKind.LAST_MONTH -> songs(4)  // too few for a month
                else -> songs(8, plan.kind.id)
            }
        }!!
        assertEquals("2026-11-23", card.day)
        assertEquals(listOf("year_ago", "last_year", "two_years", "five_years"), card.lists.map { it.kind })
        assertEquals(TIME_LIST_SONGS, card.lists.first().songIds.size)
        assertEquals(listOf("c1", "c2", "c0"), card.lists.first().covers)
        assertEquals(2025 to null, card.lists[1].year to card.lists[1].month)
        assertEquals(TimeListKind.entries.toList(), asked)
    }

    @Test fun itStopsAskingOnceItHasFour() = runBlocking {
        val asked = ArrayList<TimeListKind>()
        val card = buildTimeCard(today, plans) { asked += it.kind; songs(10) }!!
        assertEquals(4, card.lists.size)
        assertEquals(listOf(TimeListKind.YEAR_AGO, TimeListKind.DAY, TimeListKind.LAST_MONTH, TimeListKind.LAST_YEAR), asked)
        // A day with three songs is enough.
        assertEquals(3, buildTimeCard(today, plans) { if (it.kind == TimeListKind.DAY) songs(3) else emptyList() }!!.lists.single().songIds.size)
    }

    /** A list that couldn't be fetched isn't taken for an empty one: the card on the phone stays, and it's tried again. */
    @Test fun aListThatCouldNotBeFetchedKeepsTheOldCard() = runBlocking {
        assertNull(buildTimeCard(today, plans) { if (it.kind == TimeListKind.DAY) null else songs(10) }.takeIf { it?.lists?.size != 4 })
        assertNull(buildTimeCard(today, plans) { if (it.kind == TimeListKind.LAST_YEAR) null else if (it.kind == TimeListKind.DAY) songs(10) else emptyList() })
        // Four lists all the same: nothing is missing from the card, so it's kept.
        assertEquals(4, buildTimeCard(today, plans) { if (it.kind == TimeListKind.DAY) null else songs(10) }!!.lists.size)
        // No history at all is a card with no lists, not a failure: Home shows nothing for it.
        assertTrue(buildTimeCard(today, plans) { emptyList() }!!.lists.isEmpty())
    }

    @Test fun itIsMadeAgainOnAnotherDay() {
        val card = TimeCard("2026-11-23", emptyList())
        assertTrue(timeCardStale(null, today))
        assertFalse(timeCardStale(card, today))
        assertTrue(timeCardStale(card, today.plusDays(1)))
    }

    @Test fun theOthersAreAtItsFootInOrder() {
        fun list(kind: String) = TimeCardList(kind, kind, "", "", emptyList(), year = 2025)
        val card = TimeCard("2026-11-23", listOf(list("year_ago"), list("day"), list("last_month"), list("last_year")))
        assertEquals(listOf("day", "last_month", "last_year"), timeCardUnder(card, "year_ago").map { it.kind })
        assertEquals(listOf("year_ago", "day", "last_year"), timeCardUnder(card, "last_month").map { it.kind })
    }

    @Test fun theTimeMachineOpensWhereAsked() {
        assertEquals(2025 to 11, timeMachineStart(null, null, today))
        assertEquals(2026 to 10, timeMachineStart(2026, 10, today))
        assertEquals(2025 to null, timeMachineStart(2025, 0, today))
        // A month that hasn't come yet is the whole year; a year out of reach is the usual start.
        assertEquals(2026 to null, timeMachineStart(2026, 12, today))
        assertEquals(2025 to 11, timeMachineStart(2030, 3, today))
    }

    /** In Arrange Home it's a row like any other, after More of you; its look is fixed, so it has no cog. */
    @Test fun itIsARowOfHomeWithAFixedLook() {
        assertTrue(HomeSection.TIME_MACHINE.fixed)
        assertFalse(HomeSection.TIME_MACHINE.lookable)
        assertEquals(listOf(HomeSection.TIME_MACHINE), HomeSection.entries.filter { it.fixed })
        // A saved order from before it existed gets it after More of you.
        val order = HomeSection.parse("quick,more_of_you,recent")
        assertEquals(HomeSection.TIME_MACHINE, order[order.indexOf(HomeSection.MORE_OF_YOU) + 1])
    }
}
