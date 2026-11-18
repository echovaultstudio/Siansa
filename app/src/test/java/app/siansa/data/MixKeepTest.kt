package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Mixes are kept two weeks, and Siansa Beats' as many days as it keeps them (the owner, 17 Nov). */
class MixKeepTest {
    private fun mix(id: String, date: String, keepDays: Int = 0) =
        Mix(id, "s", date, id, "", "", emptyList(), 0L, aiNamed = false, keepDays = keepDays)

    @Test fun eachMixIsKeptItsOwnDays() {
        val today = LocalDate.parse("2026-11-17")
        val mixes = listOf(
            mix("own-13", "2026-11-04"), mix("own-15", "2026-11-02"),
            mix("beats-6", "2026-11-11", keepDays = 7), mix("beats-8", "2026-11-09", keepDays = 7),
            mix("beats-30", "2026-10-25", keepDays = 30),
        )
        assertEquals(listOf("own-13", "beats-6", "beats-30"), keptMixes(mixes, today).map { it.id })
    }
}
