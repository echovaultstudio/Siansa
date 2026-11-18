package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.data.BeatsUse
import app.siansa.net.BeatsSound
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who gives Flow's moods (the owner, 18 Nov: "from either beats or audiomuse from what's available but not mixed"):
 * Siansa Beats when it answers about songs' sound, else AudioMuse-AI.
 */
class FlowMoodsFromTest {
    @After fun forget() = BeatsSound.restore(emptySet())

    private val audioMuse = AppSettings(audioMuseUrl = "http://am.local:8000", audioMuseToken = "t")
    private val both = audioMuse.copy(beatsUrl = "http://beats.local:8090")

    @Test fun beatsWhenItAnswersElseAudioMuse() {
        // AudioMuse-AI alone: its tags, no voice lines.
        val (fromAudioMuse, who) = moodsFrom(audioMuse, trained = false)
        assertEquals("AudioMuse-AI", who)
        assertEquals(listOf("Happy", "Sad", "Party", "Beautiful", "Chill", "Mellow", "Easy listening", "Catchy", "Sexy"), fromAudioMuse.map { it.label })
        // Siansa Beats with the songs' sound: all from it, AudioMuse-AI set up or not.
        BeatsSound.restore(setOf("/api/similar_tracks"))
        val (fromBeats, by) = moodsFrom(both, trained = true)
        assertEquals("Siansa Beats", by)
        assertTrue("Vocals" in fromBeats.map { it.label } && "Chill" in fromBeats.map { it.label })
        // Moods in Flow switched off for Siansa Beats: none, not AudioMuse-AI's instead.
        assertEquals(emptyList<String>(), moodsFrom(both.copy(beatsOff = setOf(BeatsUse.MOODS)), trained = true).first.map { it.label })
        // Neither: no Moods tab.
        BeatsSound.restore(emptySet())
        assertEquals(emptyList<String>(), moodsFrom(AppSettings(), trained = false).first.map { it.label })
    }
}
