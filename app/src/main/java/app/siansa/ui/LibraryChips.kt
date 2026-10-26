package app.siansa.ui

/**
 * Your Library's chips (issue #497): in the order they were last opened, with a sort kept for each. Names are the
 * chips' own ([Kind] names in YourLibrary.kt), so this is tested without the screen.
 */
internal object LibraryChips {
    /** The order before any chip has been opened. */
    val DEFAULT = listOf("PLAYLIST", "MUSIC", "MIX", "FAVOURITES", "LISTENING")

    /** The sort key for the page shown: a chip's name, or "ALL" with no chip chosen. */
    fun sortKey(chip: String?): String = chip ?: "ALL"

    /** [all] chips in the order last opened ([lastUsed], most recent first), the rest in [DEFAULT] order after them. */
    fun order(lastUsed: List<String>, all: List<String>): List<String> {
        val used = lastUsed.filter { it in all }.distinct()
        val rest = all.filter { it !in used }.sortedBy { DEFAULT.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        return used + rest
    }

    /** [lastUsed] after opening [chip]: it moves to the front. */
    fun used(lastUsed: List<String>, chip: String): List<String> = listOf(chip) + lastUsed.filter { it != chip }

    /** Stored as "A,B,C". */
    fun parse(stored: String?): List<String> = stored.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun format(list: List<String>): String = list.joinToString(",")

    /** The sorts kept per page, stored as "KEY=SORT;KEY=SORT". */
    fun parseSorts(stored: String?): Map<String, String> = stored.orEmpty().split(';').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0 || eq == part.lastIndex) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
    }.toMap()

    fun formatSorts(sorts: Map<String, String>): String = sorts.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
}
