package app.siansa.mix

import app.siansa.data.SavedVibe
import app.siansa.data.pinnedVibes
import app.siansa.data.withVibe
import app.siansa.data.keptVibes
import app.siansa.net.ClapHit
import app.siansa.net.Steer
import app.siansa.net.TrackFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dscvr's sliders and saved vibes (issue #339). */
class VibeTuningTest {
    private fun hit(id: String, artist: String = "A$id") = ClapHit(id, "T$id", artist, null, 0.5)
    private fun feat(id: String, energy: Double? = null, tempo: Double? = null) =
        TrackFeatures(id, "", tempo, null, null, emptyMap(), emptyMap(), energy)

    @Test fun dialStepsAreAudioMusesWeights() {
        assertEquals(0, VibeTuning.steerWeight(0))
        assertEquals(listOf(1, 2, 3, 5, 10), (1..5).map { VibeTuning.steerWeight(it) })
        assertEquals(10, VibeTuning.steerWeight(-5))
        assertEquals(10, VibeTuning.steerWeight(9))
    }

    @Test fun dialsBecomeSteeringWithDirectionAndWeight() {
        val t = VibeTuning(sounds = listOf(SoundDial("piano", 4), SoundDial("vocals", -2), SoundDial("dark", 0)))
        assertEquals(listOf(Steer("piano", true, 5), Steer("vocals", false, 2)), t.steering())
    }

    @Test fun theAisConceptsStartAtTheDefaultWeightAndDefaultsFillUp() {
        val t = VibeTuning.start(listOf(Steer("Piano", true), Steer("vocals", false)), listOf("piano", "vocals", "acoustic", "dark"))
        assertEquals(listOf(SoundDial("Piano", 3), SoundDial("vocals", -3), SoundDial("acoustic", 0)), t.sounds)
        assertTrue(VibeTuning.start(emptyList(), emptyList()).sounds.isEmpty())
        assertEquals(5, VibeTuning.dialFor(Steer("x", true, 10)))
    }

    @Test fun slidersAloneMakeASearch() {
        assertNull(sliderQuery(VibeTuning()))
        assertEquals("calm gentle slow tempo piano music", sliderQuery(VibeTuning(energy = -2, tempo = -2, sounds = listOf(SoundDial("piano", 3)))))
        assertEquals("music", sliderQuery(VibeTuning(familiar = 2)))
        assertTrue(VibeTuning().isNeutral)
        assertFalse(VibeTuning(tempo = 1).isNeutral)
    }

    @Test fun neutralSlidersKeepAudioMusesOrder() {
        val hits = listOf(hit("1"), hit("2"), hit("3"))
        assertEquals(hits, rankVibe(hits, emptyMap(), emptyMap(), VibeTuning()))
    }

    @Test fun moreEnergyLiftsTheEnergeticSongs() {
        val hits = listOf(hit("calm"), hit("mid"), hit("loud"))
        val features = mapOf("calm" to feat("calm", energy = 0.1), "mid" to feat("mid", energy = 0.5), "loud" to feat("loud", energy = 0.9)).mapValues { it.value }
        assertEquals("loud", rankVibe(hits, features, emptyMap(), VibeTuning(energy = 2)).first().id)
        assertEquals("calm", rankVibe(hits, features, emptyMap(), VibeTuning(energy = -2)).first().id)
    }

    @Test fun slowerPutsTheSlowSongsFirst() {
        val hits = listOf(hit("fast"), hit("slow"))
        val features = mapOf("fast" to feat("fast", tempo = 160.0), "slow" to feat("slow", tempo = 70.0))
        assertEquals(listOf("slow", "fast"), rankVibe(hits, features, emptyMap(), VibeTuning(tempo = -2)).map { it.id })
    }

    @Test fun nicheAndFamiliarFollowYourPlays() {
        val hits = listOf(hit("1", "Often"), hit("2", "Never"))
        val plays = mapOf("often" to 40)
        assertEquals("2", rankVibe(hits, emptyMap(), plays, VibeTuning(familiar = -2)).first().id)
        assertEquals("1", rankVibe(hits, emptyMap(), plays, VibeTuning(familiar = 2)).first().id)
    }

    @Test fun summaryNamesWhatMoved() {
        assertEquals("more energy · slower · +piano ×5", VibeTuning(energy = 1, tempo = -1, sounds = listOf(SoundDial("piano", 4))).summary())
    }

    private fun vibe(id: String, text: String, pinned: Boolean = false) =
        SavedVibe(id, text, "T", "D", emptyList(), 0, pinned)

    @Test fun theSameWordsReplaceTheOlderVibeAndKeepItsPin() {
        val list = listOf(vibe("a", "Rainy  Sunday", pinned = true), vibe("b", "focus"))
        val out = withVibe(list, vibe("new", "rainy sunday"))
        assertEquals(listOf("a", "b"), out.map { it.id })
        assertTrue(out.first().pinned)
    }

    @Test fun slidersOnlyVibesNeverReplaceEachOther() {
        val out = withVibe(listOf(vibe("a", "")), vibe("b", ""))
        assertEquals(listOf("b", "a"), out.map { it.id })
    }

    @Test fun retuningKeepsTheVibesPlace() {
        val out = withVibe(listOf(vibe("a", "x"), vibe("b", "y")), vibe("b", "y").copy(title = "Tuned"))
        assertEquals(listOf("b", "a"), out.map { it.id })
        assertEquals("Tuned", out.first().title)
    }

    @Test fun onlyTheNewestUnpinnedAreKept() {
        val list = (1..5).map { vibe("v$it", "words $it") } + vibe("p", "pinned", pinned = true)
        val out = withVibe(list, vibe("n", "newest"), keep = 3)
        assertEquals(listOf("n", "v1", "v2", "p"), out.map { it.id })
    }

    @Test fun onlyPinnedVibesWithSongsGoHome() {
        val song = app.siansa.data.Song("s", "Song")
        val list = listOf(vibe("a", "x", pinned = true).copy(songs = listOf(song)), vibe("b", "y", pinned = true), vibe("c", "z").copy(songs = listOf(song)))
        assertEquals(listOf("a"), pinnedVibes(list).map { it.id })
    }

    @Test fun onlyTheLastFiveAreKeptPlusPinned() {
        assertEquals(5, app.siansa.data.SavedVibes.KEEP)
        val list = (1..7).map { vibe("v$it", "w$it") } + vibe("p", "pinned", pinned = true)
        assertEquals(listOf("v1", "v2", "v3", "v4", "v5", "p"), keptVibes(list).map { it.id })
    }

    @Test fun aNameOfYourOwnShowsAndSurvivesMakingItAgain() {
        val named = vibe("a", "rainy sunday").copy(name = "Sunday rain")
        assertEquals("Sunday rain", named.shownTitle)
        assertEquals("T", named.copy(name = null).shownTitle)
        val again = withVibe(listOf(named), vibe("new", "Rainy Sunday").copy(title = "New AI title"))
        assertEquals("Sunday rain", again.first().shownTitle)
    }

    @Test fun resetPutsEverySliderBackButKeepsTheSounds() {
        val t = VibeTuning(energy = 2, tempo = -1, familiar = 1, sounds = listOf(SoundDial("piano", 4)))
        assertEquals(VibeTuning(sounds = listOf(SoundDial("piano", 0))), t.cleared())
        assertTrue(t.cleared().isNeutral)
    }
}
