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

    /** How the main chips are drawn to fit: their text's scale, and each chip's padding and the gap between, in px. */
    data class Fit(val text: Float, val padding: Float, val gap: Float)

    /**
     * How the main chips shrink to fit the row (issue #497, owner's comment): first the room around and between
     * them, down to [padMin] each side and [gapMin], then the text, never below [MIN_SCALE]. A row that's still
     * too wide then scrolls, with the › hint. Pixels: [texts] each chip's text at full size, [pad] the padding
     * on each side at full size, [gap] between chips, [available] the row without the page's edges.
     */
    fun fit(texts: List<Float>, pad: Float, padMin: Float, gap: Float, gapMin: Float, available: Float): Fit {
        val n = texts.size
        if (n == 0 || available <= 0f) return Fit(1f, pad, gap)
        val text = texts.sum()
        fun width(t: Float, p: Float, g: Float) = text * t + n * 2 * p + (n - 1) * g
        if (width(1f, pad, gap) <= available) return Fit(1f, pad, gap)
        // Room around and between, shrunk by the same share k (0 none, 1 down to the least).
        val room = width(1f, pad, gap) - width(1f, padMin, gapMin)
        if (width(1f, padMin, gapMin) <= available && room > 0f) {
            val k = (width(1f, pad, gap) - available) / room
            return Fit(1f, pad - k * (pad - padMin), gap - k * (gap - gapMin))
        }
        val t = ((available - n * 2 * padMin - (n - 1) * gapMin) / text).coerceIn(MIN_SCALE, 1f)
        return Fit(t, padMin, gapMin)
    }

    /** The smallest the main chips' text gets: 14 sp → about 10 sp, still readable. */
    const val MIN_SCALE = 0.7f

    fun formatSorts(sorts: Map<String, String>): String = sorts.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
}
