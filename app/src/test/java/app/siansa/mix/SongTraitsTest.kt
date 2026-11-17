package app.siansa.mix

import app.siansa.net.AudioMuseApi
import app.siansa.net.BeatsApi
import app.siansa.net.ClapHit
import app.siansa.net.TrackFeatures
import app.siansa.data.AppJson
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Siansa Beats' traits (0.51) in the app: read, asked for by words, picked in a vibe, and what they do to its songs. */
class SongTraitsTest {
    private fun hit(id: String) = ClapHit(id, "T$id", "A$id", null, 0.5)
    private fun feat(id: String, traits: Map<String, Double>) = TrackFeatures(id, "", null, null, null, emptyMap(), emptyMap(), traits = traits)
    private fun json(text: String) = AppJson.parseToJsonElement(text) as JsonObject

    @Test fun theTraitsAreReadAndTheTrainedFeelingsTakeTheCLAPOnesPlace() {
        val f = AudioMuseApi.parseFeatures(json(
            """{"id":"s1","artist":"X","other_features":"danceable:0.60,happy:0.50,party:0.40","arousal":6.1,"valence":3.2,
               "traits":{"voice":0.93,"female":0.81,"happy":0.12,"sad":0.71,"mirex_bittersweet":0.6,"bad":"x"}}""",
        ))!!
        assertEquals(3.2, f.valence!!, 1e-9)
        assertEquals(0.93, f.traits.getValue("voice"), 1e-9)
        assertFalse("bad" in f.traits)
        // happy from the trained model, danceable and party as AudioMuse-AI made them (the model gave none here).
        assertEquals(0.12, f.moods.getValue("happy"), 1e-9)
        assertEquals(0.60, f.moods.getValue("danceable"), 1e-9)
        assertEquals(0.71, f.moods.getValue("sad"), 1e-9)
        assertFalse("mirex_bittersweet" in f.moods)
        // From AudioMuse-AI or an older Siansa Beats: none, as before.
        val old = AudioMuseApi.parseFeatures(json("""{"id":"s2","other_features":"happy:0.50"}"""))!!
        assertTrue(old.traits.isEmpty())
        assertNull(old.valence)
        assertEquals(0.5, old.moods.getValue("happy"), 1e-9)
    }

    @Test fun beatsSaysWhetherItHasThem() {
        assertTrue(BeatsApi.Capabilities.parse(json("""{"version":"0.51.0","api":1,"features":{"traits":{"available":true,"songs":10}}}""")).traits)
        assertFalse(BeatsApi.Capabilities.parse(json("""{"version":"0.50.2","api":1,"features":{}}""")).traits)
    }

    @Test fun whoseVoiceIsOfTheVoice() {
        val sungByAWoman = mapOf("voice" to 0.9, "female" to 0.8)
        assertEquals(0.72, SongTraits.named("Female vocals")!!.share(sungByAWoman)!!, 1e-9)
        assertEquals(0.18, SongTraits.named("Male vocals")!!.share(sungByAWoman)!!, 1e-9)
        assertEquals(0.1, SongTraits.named("Instrumental")!!.share(sungByAWoman)!!, 1e-9)
        // An instrumental song is neither a woman's nor a man's.
        val instrumental = mapOf("voice" to 0.05, "female" to 0.9)
        assertTrue(SongTraits.named("Female vocals")!!.share(instrumental)!! < SongTraits.HOLDS)
        assertNull(SongTraits.named("Sad")!!.share(emptyMap()))
        assertEquals(SongTraits.ALL.size, SongTraits.ALL.map { it.label.lowercase() }.toSet().size)
        // The picker's headings, each group's traits together: the voice, then moods, then the sound.
        assertEquals(listOf("Voice", "Mood", "Sound"), SongTraits.ALL.map { it.group }.distinct())
        assertEquals(2, SongTraits.ALL.map { it.group }.zipWithNext().count { (a, b) -> a != b })
        assertEquals(listOf("Vocals", "Instrumental", "Female vocals", "Male vocals"), SongTraits.ALL.filter { it.group == SongTraits.VOICE }.map { it.label })
    }

    @Test fun wordsAskForTraitsByName() {
        fun asked(text: String) = SongTraits.askedIn(text).map { it.label }
        assertEquals(listOf("Female vocals"), asked("chill female vocals for the evening"))
        assertEquals(listOf("Male vocals"), asked("Deep male voice"))
        assertEquals(listOf("Instrumental", "Sad"), asked("sad instrumental piano"))
        assertEquals(listOf("Vocals", "Happy"), asked("happy songs with singing"))
        assertEquals(emptyList<String>(), asked("rainy sunday"))
        // The tempo slider's words aren't the mood: nothing is picked for a vibe made from the sliders alone.
        assertEquals(emptyList<String>(), asked(sliderQuery(VibeTuning(tempo = -1))!!))
        assertEquals(listOf("Relaxed"), asked("relaxed evening"))
        // "female vocals" isn't read as two asks.
        assertEquals(listOf("Female vocals"), asked("female vocals"))
    }

    @Test fun aVibeTakesWhatItsWordsAskForUnlessSetAlready() {
        val none = VibeTuning()
        val asked = withAskedTraits(none, "sad female vocals")
        assertEquals(listOf(SoundDial("Female vocals", FlowMood.MORE, trait = true), SoundDial("Sad", FlowMood.MORE, trait = true)), asked.sounds)
        // Set aside by the listener ("less"): left as it is.
        val less = VibeTuning(sounds = listOf(SoundDial("Sad", FlowMood.LESS, trait = true)))
        assertEquals(less.sounds + SoundDial("Female vocals", FlowMood.MORE, trait = true), withAskedTraits(less, "sad female vocals").sounds)
        // Only while there's room.
        val full = VibeTuning(sounds = (1..FlowMood.MOST_PICKS).map { SoundDial("s$it", 3) })
        assertEquals(full, withAskedTraits(full, "female vocals"))
        // Traits don't steer the sound search: their words go into it.
        assertTrue(asked.steering().isEmpty())
        assertEquals("sad female vocals", withTraitWords("sad female vocals", asked))
        assertEquals("rainy day vocals singing", withTraitWords("rainy day", VibeTuning(sounds = listOf(SoundDial("Vocals", 3, trait = true)))))
        assertEquals("female vocals music", sliderQuery(VibeTuning(sounds = listOf(SoundDial("Female vocals", 3, trait = true)))))
    }

    @Test fun songsWithTheTraitComeFirstAndOnesWithATraitSetAsideLast() {
        val hits = listOf(hit("man"), hit("unknown"), hit("woman"), hit("instrumental"))
        val features = mapOf(
            "man" to feat("man", mapOf("voice" to 0.9, "female" to 0.1, "sad" to 0.8)),
            "woman" to feat("woman", mapOf("voice" to 0.95, "female" to 0.9, "sad" to 0.2)),
            "instrumental" to feat("instrumental", mapOf("voice" to 0.1, "female" to 0.5, "sad" to 0.6)),
        )
        val woman = VibeTuning(sounds = listOf(SoundDial("Female vocals", 3, trait = true)))
        assertEquals(listOf("woman", "man", "unknown", "instrumental"), rankVibe(hits, features, emptyMap(), woman).map { it.id })
        // Less sad: songs known not to be sad first, then those Beats has no traits for, the sad ones last.
        val notSad = VibeTuning(sounds = listOf(SoundDial("Sad", -3, trait = true)))
        assertEquals(listOf("woman", "unknown", "man", "instrumental"), rankVibe(hits, features, emptyMap(), notSad).map { it.id })
        // Nothing picked: the search's order.
        assertEquals(hits, rankVibe(hits, features, emptyMap(), VibeTuning()))
    }

    @Test fun aTraitIsPickedAsATrait() {
        val t = FlowMood.pick(VibeTuning(), "Female vocals", genre = false, trait = true)
        assertEquals(listOf(SoundDial("Female vocals", FlowMood.MORE, trait = true)), t.sounds)
        assertTrue(FlowMood.picked(t, "female vocals"))
    }
}
