package app.siansa.mix

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The kinds of notice in the chips row at the top of Home (issue #784). [tier] orders them when they're automatic:
 * what needs you first, then what you're partway through, then what's new.
 */
enum class NoticeKind(val id: String, val label: String, val hint: String, val tier: Int) {
    NEEDS_YOU("needs", "Needs you", "When the library sync failed", 0),
    CARRY_ON("carry_on", "Carry on", "A book or episode you're partway through", 1),
    NEW_ALBUMS("new_albums", "New in your library", "Albums added in the last few days", 2),
    YOUR_MONTH("month", "Your month", "Last month's listening, in the first week", 2),
    NEW_ARTISTS("artists", "Artists for you", "Today's new artists to try", 2);

    companion object {
        fun byId(id: String): NoticeKind? = entries.firstOrNull { it.id == id.trim() }
    }
}

/** How a kind of notice shows (Settings › Home › Notices): always first, ordered for you, or never. */
enum class NoticeMode(val id: String, val label: String) {
    PINNED("pinned", "Pinned"),
    AUTO("auto", "Automatic"),
    OFF("off", "Off");

    companion object {
        fun byId(id: String): NoticeMode? = entries.firstOrNull { it.id == id.trim() }
    }
}

/**
 * The chosen modes. Kinds not in [modes] are automatic. [pinned] keeps the pinned kinds in the order they were
 * pinned, which is the order they show.
 */
data class NoticePrefs(val modes: Map<NoticeKind, NoticeMode> = emptyMap(), val pinned: List<NoticeKind> = emptyList()) {
    fun modeOf(kind: NoticeKind): NoticeMode = modes[kind] ?: NoticeMode.AUTO

    /** [kind] set to [mode]; a newly pinned kind goes after the ones already pinned. */
    fun with(kind: NoticeKind, mode: NoticeMode): NoticePrefs {
        val newModes = if (mode == NoticeMode.AUTO) modes - kind else modes + (kind to mode)
        val newPinned = when {
            mode != NoticeMode.PINNED -> pinned - kind
            kind in pinned -> pinned
            else -> pinned + kind
        }
        return NoticePrefs(newModes, newPinned)
    }

    /** "carry_on:pinned,month:off": pinned ones first in their order, then the rest. */
    fun format(): String = (pinned.map { it to NoticeMode.PINNED } + modes.filter { it.value != NoticeMode.PINNED }.toList().sortedBy { it.first.ordinal })
        .joinToString(",") { (kind, mode) -> "${kind.id}:${mode.id}" }

    companion object {
        fun parse(saved: String?): NoticePrefs {
            if (saved.isNullOrBlank()) return NoticePrefs()
            val modes = LinkedHashMap<NoticeKind, NoticeMode>()
            for (part in saved.split(',')) {
                val kind = NoticeKind.byId(part.substringBefore(':')) ?: continue
                val mode = NoticeMode.byId(part.substringAfter(':', "")) ?: continue
                if (kind !in modes && mode != NoticeMode.AUTO) modes[kind] = mode
            }
            return NoticePrefs(modes, modes.filterValues { it == NoticeMode.PINNED }.keys.toList())
        }
    }
}

/**
 * One chip. [key] names what it's about ("carry_on:book-12", "month:2026-10"), so hiding it hides only that one:
 * next month's report shows again.
 */
data class Notice(val kind: NoticeKind, val key: String, val text: String, val route: String)

object Notices {
    /** At most this many chips. */
    const val MAX = 5

    /** A book counts as "carry on" when it was played this recently. */
    const val CARRY_ON_DAYS = 7

    /** Albums this new count as new in your library. */
    const val NEW_ALBUM_DAYS = 3

    /** Your month shows in the first days of the next month. */
    const val MONTH_DAYS = 7

    /** Hidden notices remembered, the newest kept. */
    const val MAX_HIDDEN = 100

    /**
     * The chips to show, in order: pinned kinds first, in the order they were pinned; then the automatic ones by
     * tier, and within a tier the kinds you tap most first ([taps]). Off kinds and [hidden] notices are left out.
     */
    fun order(candidates: List<Notice>, prefs: NoticePrefs, taps: Map<NoticeKind, Int>, hidden: Set<String>, max: Int = MAX): List<Notice> {
        val shown = candidates.filter { prefs.modeOf(it.kind) != NoticeMode.OFF && it.key !in hidden }.distinctBy { it.key }
        val pinned = prefs.pinned.flatMap { kind -> shown.filter { it.kind == kind } }
        val auto = shown.filter { prefs.modeOf(it.kind) == NoticeMode.AUTO }
            .withIndex()
            .sortedWith(compareBy<IndexedValue<Notice>> { it.value.kind.tier }.thenByDescending { taps[it.value.kind] ?: 0 }.thenBy { it.index })
            .map { it.value }
        return (pinned + auto).take(max.coerceAtLeast(0))
    }

    fun needsYou(): Notice = Notice(NoticeKind.NEEDS_YOU, "needs:sync:${LocalDate.now()}", "Library sync failed", "settings/connections")

    fun carryOn(bookId: String, title: String): Notice =
        Notice(NoticeKind.CARRY_ON, "carry_on:$bookId", "Carry on: $title", "book/$bookId")

    /** Whether a book last played at [updatedAt] still counts as one you're in the middle of. */
    fun recentlyPlayed(updatedAt: Long, now: Long): Boolean = updatedAt > 0 && now - updatedAt <= CARRY_ON_DAYS * DAY_MS

    /** [count] albums added recently; [newestId] tells one batch from the next. Null when there are none. */
    fun newAlbums(count: Int, newestId: String): Notice? = if (count <= 0) null else
        Notice(NoticeKind.NEW_ALBUMS, "new_albums:$newestId", if (count == 1) "1 new album" else "$count new albums", "home/newest")

    /** How many of [created] (the albums' "created" dates, ISO) are from the last [NEW_ALBUM_DAYS] days. */
    fun newAlbumCount(created: List<String?>, today: LocalDate): Int {
        val from = today.minusDays(NEW_ALBUM_DAYS.toLong())
        return created.count { date ->
            val day = date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
            !day.isBefore(from) && !day.isAfter(today)
        }
    }

    /** "Your October", in the first [MONTH_DAYS] days of November; null the rest of the month. */
    fun yourMonth(today: LocalDate): Notice? {
        if (today.dayOfMonth > MONTH_DAYS) return null
        val last = today.minusMonths(1)
        val name = last.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return Notice(NoticeKind.YOUR_MONTH, "month:${last.year}-${last.monthValue}", "Your $name", "report/${ReportRange.LAST_MONTH.id}")
    }

    fun newArtists(count: Int, today: LocalDate): Notice? = if (count <= 0) null else
        Notice(NoticeKind.NEW_ARTISTS, "artists:$today", if (count == 1) "1 artist for you" else "$count artists for you", "discover/artists")

    /** [hidden] with [key] added, keeping the newest [MAX_HIDDEN]. */
    fun hide(hidden: List<String>, key: String): List<String> = (hidden - key + key).takeLast(MAX_HIDDEN)

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
