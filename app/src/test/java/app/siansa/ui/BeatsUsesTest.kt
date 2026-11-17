package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.data.BeatsUse
import app.siansa.net.BeatsListens
import app.siansa.net.BeatsSound
import app.siansa.playback.LoudnessSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Siansa Beats' card lists what the app asks it for, a switch each (the owner, 17 Nov), and each switch is the
 * setting its own page shows (Scrobbling & history's switch, Crossfade's loudness source, the rest in beatsOff):
 * switched on the card, it's switched there, and the other way round.
 */
class BeatsUsesTest {
    /** The settings as the app keeps them, each switch writing the field its page reads. */
    private class Store(var s: AppSettings) : BeatsUseStore {
        override val current get() = s
        override fun scrobbleBeats(on: Boolean) { s = s.copy(scrobbleBeats = on) }
        override fun loudness(source: LoudnessSource) { s = s.copy(loudnessSource = source) }
        override fun use(id: String, on: Boolean) { s = s.copy(beatsOff = if (on) s.beatsOff - id else s.beatsOff + id) }
    }

    private val setUp = AppSettings(beatsUrl = "http://beats.local:8090", navidromeUser = "max")

    private fun everything() {
        BeatsSound.heard(setOf(BeatsListens.PATH, "/api/similar_tracks"))
        BeatsSound.restoreTraits(true)
        BeatsSound.restoreMixes(true)
    }

    @After fun forget() {
        BeatsSound.heard(emptySet())
        BeatsSound.restoreTraits(false)
        BeatsSound.restoreMixes(false)
    }

    @Test fun everyUseIsListedAndWhatsNewIsOn() {
        everything()
        val rows = beatsUses(setUp)
        assertEquals(listOf("scrobble", "loudness", BeatsUse.DJ, BeatsUse.GENRES, BeatsUse.SOUND, BeatsUse.MOODS, BeatsUse.MIXES),
            rows.map { it.tag.removePrefix("beatsUse-") })
        // What the app did by itself before has its switch on: nothing changes until one's switched off.
        assertEquals(listOf(false, false, true, true, true, true, true), rows.map { it.on })
        assertTrue(rows.all { it.offered })
    }

    @Test fun eachSwitchIsTheSettingItsOwnPageShows() {
        everything()
        val store = Store(setUp)
        for (row in beatsUses(store.current)) {
            row.set(store, !row.on)
            assertEquals(row.tag, !row.on, beatsUses(store.current).first { it.tag == row.tag }.on)
        }
        val s = store.current
        assertTrue("Scrobbling's own switch", s.scrobbleBeats)
        assertEquals("Crossfade's loudness source", LoudnessSource.PLUGIN, s.loudnessSource)
        assertEquals(setOf(BeatsUse.DJ, BeatsUse.GENRES, BeatsUse.SOUND, BeatsUse.MOODS, BeatsUse.MIXES), s.beatsOff)
        assertFalse(s.beatsSoundReady)
        assertFalse(s.beatsFor(BeatsUse.MIXES))
        // Changed on its own page (Scrobbling, Crossfade), the card shows it.
        val rows = beatsUses(s.copy(scrobbleBeats = false, loudnessSource = LoudnessSource.TAGS)).associateBy { it.tag }
        assertFalse(rows.getValue("beatsUse-scrobble").on)
        assertFalse(rows.getValue("beatsUse-loudness").on)
        // Switched on again, it's on again.
        beatsUses(store.current).filter { !it.on }.forEach { it.set(store, true) }
        assertTrue(store.current.beatsOff.isEmpty())
    }

    @Test fun whatThisSiansaBeatsCantDoIsSaid() {
        val rows = beatsUses(setUp).associateBy { it.tag }
        assertFalse(rows.getValue("beatsUse-mixes").offered)
        assertEquals("Needs Siansa Beats 0.53", rows.getValue("beatsUse-mixes").needs)
        assertEquals("Needs Siansa Beats 0.51", rows.getValue("beatsUse-moods").needs)
        assertFalse(rows.getValue("beatsUse-scrobble").offered)
        assertTrue(rows.getValue("beatsUse-dj").offered)
        assertFalse(AppSettings().beatsFor(BeatsUse.DJ))  // no Siansa Beats at all
    }
}
