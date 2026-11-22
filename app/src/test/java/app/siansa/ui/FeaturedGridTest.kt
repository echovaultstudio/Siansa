package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A cover grid with one featured: every gap the same (the owner, 22 Nov). */
class FeaturedGridTest {
    @Test fun theFeaturedSquareIsAsTallAsTheTwoBesideItAndTheirGap() {
        for ((width, gap) in listOf(1096 to 12, 1080 to 11, 700 to 8, 1001 to 13)) {
            val (cell, big) = featuredSquares(width, gap)
            assertEquals("two beside it and their gap", 2 * cell + gap, big)
            assertEquals("beside it, the row is as wide as three cells and two gaps", 3 * cell + 2 * gap, big + gap + cell)
            assert(3 * cell + 2 * gap <= width)
        }
    }
}
