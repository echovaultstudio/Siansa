package app.siansa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's book shelves and progress are read again at most once a minute (#569). */
class BookFreshTest {
    @Test fun withinAMinuteIsFresh() {
        assertTrue(BookRepository.isFresh(at = 1_000, now = 1_000))
        assertTrue(BookRepository.isFresh(at = 1_000, now = 1_000 + BookRepository.FRESH_MS - 1))
    }

    @Test fun olderOrNeverIsNot() {
        assertFalse(BookRepository.isFresh(at = 1_000, now = 1_000 + BookRepository.FRESH_MS))
        assertFalse(BookRepository.isFresh(at = null, now = 5_000))
        assertFalse(BookRepository.isFresh(at = 0, now = 5_000))
    }

    @Test fun aClockGoingBackIsNotFresh() {
        assertFalse(BookRepository.isFresh(at = 10_000, now = 5_000))
    }
}
