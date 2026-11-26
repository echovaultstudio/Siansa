package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.data.ReplayGainMode
import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smart levelling (the owner, 26 Nov: a DJ mix album's tracks, each levelled on its own, dipped and rose at every
 * change; "If same cd back to back one level. If shuffle album, album level. If different album tracks, tracks").
 */
class SmartLevellingTest {
    private val smart = AppSettings(replayGain = ReplayGainMode.SMART, loudnessSource = LoudnessSource.PLUGIN, loudnessTarget = -14)

    private fun song(id: String, album: String?, track: Int?, trackGain: Float? = null, albumGain: Float? = null) =
        Song(id = id, title = id, artist = "A", album = album ?: "", albumId = album, track = track, disc = 1, durationSec = 200, trackGain = trackGain, albumGain = albumGain)

    private fun measured(id: String, lufs: Double) = TrackProfile(trackId = id, refLufs = lufs, startRamp = emptyList(), endRamp = emptyList())

    @Test fun smartIsTheDefault() = assertEquals(ReplayGainMode.SMART, AppSettings().replayGain)

    @Test fun anAlbumInOrderKeepsTheLevelItsRunStartedAt() {
        val t1 = song("t1", "mix", 1)
        val t2 = song("t2", "mix", 2)
        // The first of the run: its own measured level (−12 LUFS to −14: −2 dB).
        val first = Levelling.decide(t1, smart, measured("t1", -12.0), Levelling.Around(next = t2))
        assertEquals(-2.0, first.gainDb, 0.01)
        // The next track measured louder (−9.6 LUFS, −4.4 dB on its own), but it carries on at the run's level.
        val second = Levelling.decide(t2, smart, measured("t2", -9.6), Levelling.Around(previous = t1, previousVolume = first.volume, previousSource = first.source))
        assertEquals(first.volume, second.volume, 0f)
        assertTrue(second.why, second.why.contains("kept"))
    }

    @Test fun anAlbumInOrderWithAlbumTagsTakesThem() {
        val t1 = song("t1", "live", 4, trackGain = -6f, albumGain = -3f)
        val t2 = song("t2", "live", 5, trackGain = -1f, albumGain = -3f)
        val a = Levelling.decide(t1, smart, null, Levelling.Around(next = t2))
        val b = Levelling.decide(t2, smart, null, Levelling.Around(previous = t1, previousVolume = 0.2f))
        assertEquals(a.volume, b.volume, 0f)
        assertEquals(replayGainVolume(t2, smart.copy(replayGain = ReplayGainMode.ALBUM)), b.volume, 0f)
    }

    @Test fun oneAlbumShuffledGetsTheAlbumsLevel() {
        val t7 = song("t7", "lp", 7)
        val t2 = song("t2", "lp", 2)
        val album = Levelling.togetherLufs(listOf(-12.0, -8.0))!!
        val d = Levelling.decide(t7, smart, measured("t7", -12.0), Levelling.Around(previous = t2, previousVolume = 0.5f, next = song("t3", "lp", 3), oneAlbum = true, albumLufs = album))
        assertEquals(-14 - album, d.gainDb, 0.01)
        // The power of the two together, nearer the louder: not their plain mean.
        assertTrue(album > -10.0)
    }

    @Test fun differentAlbumsEachGetTheirOwn() {
        val a = song("a", "one", 3)
        val b = song("b", "two", 4)
        val d = Levelling.decide(b, smart, measured("b", -9.0), Levelling.Around(previous = a, previousVolume = 0.9f, next = song("c", "three", 1)))
        assertEquals(-5.0, d.gainDb, 0.01)
        // A later track of the same album, not the next: its own level too.
        val skip = Levelling.decide(song("x", "one", 9), smart, measured("x", -11.0), Levelling.Around(previous = a, previousVolume = 0.9f))
        assertEquals(-3.0, skip.gainDb, 0.01)
    }
}
