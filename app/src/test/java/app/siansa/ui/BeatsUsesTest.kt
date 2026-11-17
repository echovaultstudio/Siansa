package app.siansa.ui

import android.app.Application
import org.robolectric.RuntimeEnvironment
import app.siansa.data.AppSettings
import app.siansa.data.BeatsUse
import app.siansa.data.SettingsRepository
import app.siansa.net.BeatsListens
import app.siansa.net.BeatsSound
import app.siansa.playback.LoudnessSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Siansa Beats' card lists what the app asks it for, a switch each (the owner, 17 Nov), and each switch is the
 * setting its own page shows: switched on the card, it's switched there, and the other way round.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BeatsUsesTest {
    @After fun forget() {
        BeatsSound.heard(emptySet())
        BeatsSound.restoreTraits(false)
        BeatsSound.restoreMixes(false)
    }

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication()).also {
        it.save(it.current.copy(beatsUrl = "http://beats.local:8090", navidromeUrl = "http://nd.local", navidromeUser = "max", navidromePassword = "p"))
    }

    @Test fun everyUseIsListedAndWhatsNewIsOn() {
        BeatsSound.heard(setOf(BeatsListens.PATH, "/api/similar_tracks"))
        BeatsSound.restoreTraits(true)
        BeatsSound.restoreMixes(true)
        val rows = beatsUses(repo().current)
        assertEquals(listOf("scrobble", "loudness", BeatsUse.DJ, BeatsUse.GENRES, BeatsUse.SOUND, BeatsUse.MOODS, BeatsUse.MIXES),
            rows.map { it.tag.removePrefix("beatsUse-") })
        // What the app did by itself before has its switch on: nothing changes until one's switched off.
        assertEquals(listOf(false, false, true, true, true, true, true), rows.map { it.on })
        assertTrue(rows.all { it.offered })
    }

    @Test fun eachSwitchIsTheSettingItsOwnPageShows() {
        BeatsSound.heard(setOf(BeatsListens.PATH, "/api/similar_tracks"))
        BeatsSound.restoreTraits(true)
        BeatsSound.restoreMixes(true)
        val repo = repo()
        for (row in beatsUses(repo.current)) {
            row.set(repo, !row.on)
            val now = beatsUses(repo.current).first { it.tag == row.tag }
            assertEquals(row.tag, !row.on, now.on)
        }
        val s = repo.current
        assertTrue("Scrobbling's own switch", s.scrobbleBeats)
        assertEquals("Crossfade's loudness source", LoudnessSource.PLUGIN, s.loudnessSource)
        assertEquals(setOf(BeatsUse.DJ, BeatsUse.GENRES, BeatsUse.SOUND, BeatsUse.MOODS, BeatsUse.MIXES), s.beatsOff)
        assertFalse(s.beatsSoundReady)
        assertFalse(s.beatsFor(BeatsUse.MIXES))
        // Changed on its own page (Scrobbling, Crossfade), the card shows it.
        repo.save(repo.current.copy(scrobbleBeats = false))
        repo.setLoudness(source = LoudnessSource.TAGS)
        val rows = beatsUses(repo.current).associateBy { it.tag }
        assertFalse(rows.getValue("beatsUse-scrobble").on)
        assertFalse(rows.getValue("beatsUse-loudness").on)
        // Kept after the app starts again.
        assertEquals(s.beatsOff, SettingsRepository(RuntimeEnvironment.getApplication()).current.beatsOff)
    }

    @Test fun whatThisSiansaBeatsCantDoIsSaid() {
        val rows = beatsUses(repo().current).associateBy { it.tag }
        assertFalse(rows.getValue("beatsUse-mixes").offered)
        assertEquals("Needs Siansa Beats 0.53", rows.getValue("beatsUse-mixes").needs)
        assertEquals("Needs Siansa Beats 0.51", rows.getValue("beatsUse-moods").needs)
        assertFalse(rows.getValue("beatsUse-scrobble").offered)
        assertTrue(rows.getValue("beatsUse-dj").offered)
        assertFalse(AppSettings().beatsFor(BeatsUse.DJ))  // no Siansa Beats at all
    }
}
