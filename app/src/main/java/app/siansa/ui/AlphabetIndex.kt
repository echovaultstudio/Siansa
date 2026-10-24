package app.siansa.ui

import java.text.Normalizer

/**
 * The A to Z quick-scroll index for lists sorted by name: which letter each item files under, the order
 * that keeps every letter together, and where each letter starts.
 */
object AlphabetIndex {
    /** Numbers, symbols and non-Latin names file under this. */
    const val OTHER = '#'
    val LETTERS: List<Char> = listOf(OTHER) + ('A'..'Z')

    /** Lists shorter than this scroll easily enough, so they don't get the bar. */
    const val MIN_ITEMS = 30

    /** The letter a name files under: its first letter without accents (Ångström → A), else #. */
    fun letterOf(name: String): Char {
        val first = name.trimStart().firstOrNull() ?: return OTHER
        val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
        return if (plain in 'A'..'Z') plain else OTHER
    }

    /**
     * Sorts # first, then A to Z, and by name within a letter (then by [then]), so the bar and the list
     * agree. Each key is worked out once, not on every comparison.
     */
    fun <T> sort(items: List<T>, name: (T) -> String, then: (T) -> String = { "" }): List<T> =
        items.map { Triple(LETTERS.indexOf(letterOf(name(it))), sortKey(name(it)), it) }
            .sortedWith(compareBy<Triple<Int, String, T>>({ it.first }, { it.second }, { then(it.third).lowercase() }))
            .map { it.third }

    /** Lower case without accents, so Ångström sorts with the other A's. */
    private fun sortKey(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    private val MARKS = Regex("\\p{Mn}+")

    /** The position where each letter first appears, for names already in [sort] order. */
    fun starts(names: List<String>): Map<Char, Int> {
        val starts = LinkedHashMap<Char, Int>()
        names.forEachIndexed { i, n -> starts.putIfAbsent(letterOf(n), i) }
        return starts
    }

    /**
     * Where picking [letter] should scroll to: where it starts, or for a letter with nothing in it the
     * next letter that has something (or the last one, past the end).
     */
    fun target(letter: Char, starts: Map<Char, Int>): Int? {
        if (starts.isEmpty()) return null
        starts[letter]?.let { return it }
        val from = LETTERS.indexOf(letter)
        return LETTERS.drop(from + 1).firstNotNullOfOrNull { starts[it] } ?: starts.values.max()
    }

    fun shouldShow(count: Int, starts: Map<Char, Int>): Boolean = count >= MIN_ITEMS && starts.size >= 3
}
