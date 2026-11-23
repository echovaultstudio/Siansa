package app.siansa.mix

import app.siansa.data.Song
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home's Time Machine card (issue #1341; the owner, 23 Nov: "a pinnable card from time machine, showing playlists like
 * this time last year, this day last year, your previous month"): which lists it can hold, in the order they're tried.
 */
enum class TimeListKind(val id: String) {
    YEAR_AGO("year_ago"),
    DAY("day"),
    LAST_MONTH("last_month"),
    LAST_YEAR("last_year"),
    TWO_YEARS("two_years"),
    FIVE_YEARS("five_years"),
}

/**
 * One list the card may show: its [title], the small date [tag] on its picture ("NOV 2025", "23 NOV"), the [line] that
 * says when it's from ("November 2025"), the days it covers and the [key] its plays are kept under on the phone.
 * [year] and [month] (null: the whole year) are where it opens in the Time Machine. [fewest]: with fewer songs in the
 * library than this it's left out.
 */
data class TimeListPlan(
    val kind: TimeListKind,
    val title: String,
    val tag: String,
    val line: String,
    val window: TimeWindow,
    val key: String,
    val year: Int,
    val month: Int?,
    val fewest: Int = TIME_LIST_FEWEST,
)

/** A list needs this many songs in the library to be on the card; a single day needs [TIME_DAY_FEWEST]. */
const val TIME_LIST_FEWEST = 5
const val TIME_DAY_FEWEST = 3

/** How many lists the card shows: one big, three at its foot. */
const val TIME_CARD_LISTS = 4

/** How many songs a list holds. */
const val TIME_LIST_SONGS = 50

/**
 * The lists to try on [today], in order: this month a year ago, this date a year ago, last month, last year, and two
 * to fall back on (this month two and five years ago). A month's and a year's [TimeListPlan.key] are the Time
 * Machine's own, so the card and the page share what's kept on the phone.
 */
fun timeCardPlans(today: LocalDate, locale: Locale = Locale.getDefault()): List<TimeListPlan> {
    fun short(date: LocalDate) = date.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').uppercase(locale)
    fun month(kind: TimeListKind, title: String, first: LocalDate, tag: String = "${short(first)} ${first.year}"): TimeListPlan {
        val window = timeWindow(first.year, first.monthValue, locale)
        return TimeListPlan(kind, title, tag, window.label, window, "${first.year}-${first.monthValue}", first.year, first.monthValue)
    }
    val thisMonth = today.withDayOfMonth(1)
    val day = today.minusYears(1)
    val lastYear = today.year - 1
    return listOf(
        month(TimeListKind.YEAR_AGO, "This time last year", thisMonth.minusYears(1)),
        TimeListPlan(
            TimeListKind.DAY, "This day last year", "${day.dayOfMonth} ${short(day)}",
            "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, locale)} ${day.year}",
            TimeWindow(day, day.plusDays(1), day.toString()), day.toString(), day.year, day.monthValue, TIME_DAY_FEWEST,
        ),
        month(TimeListKind.LAST_MONTH, "Last month", thisMonth.minusMonths(1), short(thisMonth.minusMonths(1))),
        TimeListPlan(TimeListKind.LAST_YEAR, "Your $lastYear", "$lastYear", "$lastYear", timeWindow(lastYear, null, locale), "$lastYear-all", lastYear, null),
        month(TimeListKind.TWO_YEARS, "Two years ago", thisMonth.minusYears(2)),
        month(TimeListKind.FIVE_YEARS, "Five years ago", thisMonth.minusYears(5)),
    )
}

/** One list as it's kept on the phone: what the card draws and plays, with no need to ask anyone again. */
@Serializable
data class TimeCardList(
    val kind: String,
    val title: String,
    val tag: String,
    val line: String,
    val songIds: List<String>,
    /** Its first few covers, for its picture. */
    val covers: List<String> = emptyList(),
    val year: Int,
    val month: Int? = null,
)

/** The card as kept on the phone: the [day] it was made for (ISO), and its lists, the big one first. */
@Serializable
data class TimeCard(val day: String, val lists: List<TimeCardList>)

/** What's in the card's file: nothing until it was first made. */
@Serializable
data class TimeCardKept(val card: TimeCard? = null)

/** Whether the card must be made again: there's none, or it was made for another day ("this day last year" moved on). */
fun timeCardStale(card: TimeCard?, today: LocalDate): Boolean = card?.day != today.toString()

/**
 * The card for [today] from [plans], asking [songsOf] for each list's songs in the library (null: its plays couldn't
 * be fetched) until [TIME_CARD_LISTS] have enough. Null when a list couldn't be fetched and the card would be short
 * of lists because of it: then the one already on the phone stays, and it's tried again later. A card with no lists
 * (no history yet) is a card: Home shows nothing for it.
 */
suspend fun buildTimeCard(today: LocalDate, plans: List<TimeListPlan>, songsOf: suspend (TimeListPlan) -> List<Song>?): TimeCard? {
    val lists = ArrayList<TimeCardList>()
    var failed = false
    for (plan in plans) {
        if (lists.size >= TIME_CARD_LISTS) break
        val songs = songsOf(plan)
        if (songs == null) { failed = true; continue }
        if (songs.size < plan.fewest) continue
        val kept = songs.take(TIME_LIST_SONGS)
        lists += TimeCardList(plan.kind.id, plan.title, plan.tag, plan.line, kept.map { it.id }, kept.mapNotNull { it.coverArt }.distinct().take(TIME_CARD_COVERS), plan.year, plan.month)
    }
    return if (failed && lists.size < TIME_CARD_LISTS) null else TimeCard(today.toString(), lists)
}

/** How many covers a list keeps for its picture. */
const val TIME_CARD_COVERS = 6

/** The Time Machine's page at a list's month or year: month 0 is the whole year. */
fun timeMachineRoute(year: Int, month: Int?): String = "timemachine?year=$year&month=${month ?: 0}"
