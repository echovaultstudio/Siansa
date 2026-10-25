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
}
