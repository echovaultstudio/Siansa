package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tile titles keep whole words, shrinking a little when one doesn't fit (issue #697). */
class WholeWordsTest {
    /** A pretend font: each letter is 10 px wide at full size. */
    private fun widest(words: List<String>) = { scale: Float -> (words.maxOf { it.length } * 10 * scale).toInt() }
    private fun lines(text: String, width: Int) = { scale: Float ->
        var lines = 1
        var used = 0
        for (w in text.split(" ")) {
            val need = (w.length * 10 * scale).toInt()
            if (used > 0 && used + 5 + need > width) { lines++; used = need } else used += if (used == 0) need else 5 + need
        }
        lines
    }

    @Test fun aWordThatFitsKeepsTheFullSize() {
        assertEquals(1f, wholeWordsScale(widest(listOf("Workout")), lines("Workout", 120), width = 120, maxLines = 3), 0.001f)
    }

    @Test fun aWordWiderThanTheTileShrinksInsteadOfBreaking() {
        // "Melancholy" is 100 px at full size; the tile has 90.
        val scale = wholeWordsScale(widest(listOf("Melancholy")), lines("Melancholy", 90), width = 90, maxLines = 3)
        assertEquals(0.9f, scale, 0.001f)
    }

    @Test fun tooManyLinesShrinksToo() {
        val text = "Rainy Window Early Hours"
        val scale = wholeWordsScale(widest(text.split(" ")), lines(text, 110), width = 110, maxLines = 2)
        assertEquals(true, scale < 1f)
        assertEquals(true, lines(text, 110)(scale) <= 2)
    }

    @Test fun neverSmallerThanTheFloor() {
        assertEquals(MIN_TITLE_SCALE, wholeWordsScale({ 10_000 }, { 1 }, width = 50, maxLines = 1), 0.001f)
    }
}
