package app.siansa

import app.siansa.data.Mix
import app.siansa.mix.MixGenerator
import app.siansa.mix.MixRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily mixes stay as they were made, and AI mixes need AudioMuse (issue #486). */
class MixRulesTest {
    private fun mix(id: String, selector: String, date: String, scope: String = "") =
        Mix(id, selector, date, "t", "d", "q", emptyList(), 0L, false, scope)

    @Test fun mixesMadeForOtherMusicStillCountAsToday() {
        val today = "2026-10-25"
        val mixes = listOf(
            mix("a", "evening", today, scope = ""),
            // Made while kids' music was shown too: still today's mix, not made again.
            mix("b", "you-sound", today, scope = "own=true;users=4;libs="),
            mix("c", "morning", today),
            mix("d", "evening", "2026-10-24"),
        )
        val shown = MixRules.todays(mixes, today, personalIds = setOf("you-sound"), applicableIds = setOf("evening"))
        assertEquals(listOf("a", "b"), shown.map { it.id })
    }

    @Test fun withoutAudioMuseOnlyMixesFromYourPlaysShow() {
        assertFalse(MixRules.showsAiMixes(audioMuseReady = false))
        assertTrue(MixRules.showsAiMixes(audioMuseReady = true))
        val withoutAudioMuse = MixGenerator.Personal.entries.filter { MixRules.showsPersonal(it, false) }
        assertTrue(withoutAudioMuse.none { it.needsAudioMuse })
        assertTrue(MixGenerator.Personal.REPEAT in withoutAudioMuse)
        assertEquals(MixGenerator.Personal.entries, MixGenerator.Personal.entries.filter { MixRules.showsPersonal(it, true) })
    }

    private fun made(id: String, selector: String, date: String, at: Long) = Mix(id, selector, date, "t", "d", "q", emptyList(), at, false)

    @Test fun todaysMixStaysWhenTheHoursVibeMovesOn() {
        val today = "2026-10-26"
        val vibes = setOf("morning", "daytime", "evening")
        val morning = made("m", "morning", today, at = 1)
        // 11:49: the vibe is now Daytime, but this morning's mix is the one you have (beta testers #37).
        assertEquals(morning, MixRules.todaysMix(listOf(morning), today, currentId = "daytime", vibeIds = vibes))
        // Once Daytime's is made (you tapped ↻), that's it.
        val daytime = made("d", "daytime", today, at = 2)
        assertEquals(daytime, MixRules.todaysMix(listOf(morning, daytime), today, "daytime", vibes))
        // The vibe for the hour first, even if another was made later (a vibe tile you tapped).
        assertEquals(daytime, MixRules.todaysMix(listOf(daytime, made("e", "evening", today, at = 3)), today, "daytime", vibes))
        // Yesterday's, carousel and personal mixes don't count: the day's first is made.
        val others = listOf(made("y", "morning", "2026-10-25", 1), made("c", "carousel-day-v0-0", today, 1), made("p", "you-sound", today, 1))
        assertEquals(null, MixRules.todaysMix(others, today, "morning", vibes))
    }
}
