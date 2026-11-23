package app.siansa.playback.engine

import androidx.media3.exoplayer.audio.AudioSink
import app.siansa.data.AppSettings
import app.siansa.playback.CrossfadeMode
import app.siansa.ui.crossfadeFoldSummary
import app.siansa.ui.equaliserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

/**
 * Untouched playback (the owner, 22 Nov: "Normal playback of any song gets played in its original quality, frequency
 * and bit depth… no quiet, stuttering or stops" in a DJ mix): a player's sink with two ways out, changed only when the
 * player lets go of it.
 */
class SwitchSinkTest {
    /** A sink that only writes down what it was told. */
    private fun sink(calls: MutableList<String>): AudioSink = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AudioSink::class.java)) { _, method, _ ->
        calls += method.name
        when (method.returnType) {
            java.lang.Boolean.TYPE -> true
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    } as AudioSink

    @Test fun whatPlaysGoesOneWayAndWhatIsSetUpGoesToBoth() {
        val mixed = mutableListOf<String>()
        val direct = mutableListOf<String>()
        var untouched = false
        val sink = SwitchSink(sink(mixed), sink(direct)) { untouched }
        assertFalse(sink.isDirect)
        sink.setVolume(0.5f)
        sink.setAudioSessionId(7)
        sink.play()
        sink.handleBuffer(ByteBuffer.allocate(8), 0, 1)
        sink.pause()
        sink.flush()
        assertEquals(listOf("setVolume", "setAudioSessionId", "play", "handleBuffer", "pause", "flush"), mixed)
        assertEquals(listOf("setVolume", "setAudioSessionId"), direct)  // ready for its turn, and silent
    }

    @Test fun theWayOutChangesOnlyWhenThePlayerLetsGoOfItsSink() {
        val mixed = mutableListOf<String>()
        val direct = mutableListOf<String>()
        var untouched = false
        val sink = SwitchSink(sink(mixed), sink(direct)) { untouched }
        untouched = true  // switched in Settings while a song plays
        sink.handleBuffer(ByteBuffer.allocate(8), 0, 1)
        sink.flush()  // a seek: still the same way out, so nothing changes mid-song
        assertFalse(sink.isDirect)
        assertEquals(listOf("handleBuffer", "flush"), mixed)
        assertTrue(direct.isEmpty())
        sink.reset()  // the player stopped
        assertTrue(sink.isDirect)
        assertEquals("reset", mixed.last())
        assertEquals(listOf("reset"), direct)
        sink.play()
        sink.handleBuffer(ByteBuffer.allocate(8), 0, 1)
        assertEquals(listOf("reset", "play", "handleBuffer"), direct)
        assertEquals("reset", mixed.last())  // nothing more through the mixer
        untouched = false
        sink.reset()
        assertFalse(sink.isDirect)
        // A sink made while it's on starts on the phone's own output.
        assertTrue(SwitchSink(sink(mutableListOf()), sink(mutableListOf())) { true }.isDirect)
    }

    /** The owner, 23 Nov: Untouched playback on "still allows starting DJ mix from now playing screen". */
    @Test fun aDjMixAsksFirstWhileUntouchedPlaybackIsOn() {
        var started = 0
        app.siansa.ui.DjNeedsBlends.then(untouched = false) { started++ }
        assertEquals(1, started)  // off: straight away, nothing asked
        assertEquals(null, app.siansa.ui.DjNeedsBlends.waiting.value)
        app.siansa.ui.DjNeedsBlends.then(untouched = true) { started++ }
        assertEquals(1, started)  // on: it waits on the answer
        val waiting = app.siansa.ui.DjNeedsBlends.waiting.value
        assertTrue(waiting != null)
        waiting!!()  // "Turn off and start"
        assertEquals(2, started)
        app.siansa.ui.DjNeedsBlends.waiting.value = null
    }

    @Test fun thePlaybackPageSaysWhatItSwitchesOff() {
        assertFalse(AppSettings().untouchedPlayback)  // off as it comes: crossfades and DJ blends work as before
        val on = AppSettings(untouchedPlayback = true, crossfade = CrossfadeMode.SMART)
        assertEquals("Off while Untouched playback is on", crossfadeFoldSummary(on))
        assertEquals("Smart", crossfadeFoldSummary(on.copy(untouchedPlayback = false)))
        assertEquals("Off while Untouched playback is on", equaliserSummary(on, on = true, preset = "Rock"))
        assertEquals("On · Rock", equaliserSummary(AppSettings(), on = true, preset = "Rock"))
        assertEquals("Off", equaliserSummary(AppSettings(), on = false, preset = "Rock"))
    }
}
