package app.siansa.playback.engine

import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

/**
 * Fades with Untouched playback (issue #1370): the player's own output, its volume moved for a moment when the listener
 * starts or stops something. Never between songs that follow on by themselves.
 */
class FadingSinkTest {
    private var clock = 0L
    private var waited = 0L
    private var pending = false
    private val volumes = mutableListOf<Float>()
    private val calls = mutableListOf<String>()

    /** The player's output: writes down what it's told, and has sound waiting when [pending] says so. */
    private val output: AudioSink = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
        calls += method.name
        when (method.name) {
            "setVolume" -> { volumes += args[0] as Float; null }
            "hasPendingData" -> pending
            "handleBuffer" -> { pending = true; true }
            "flush" -> { pending = false; null }
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> true
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
    } as AudioSink

    private fun sink(fadeIn: Int = 250, fadeOut: Int = 150) =
        FadingSink(output, { fadeIn }, { fadeOut }, now = { clock }, sleep = { waited += it; clock += it })

    private fun write(s: FadingSink) = s.handleBuffer(ByteBuffer.allocate(8), 0, 1)

    @Test fun theFirstSoundComesInFromSilenceOverTheFadeIn() {
        val s = sink()
        s.play()
        assertEquals("Silent until sound goes out", 0f, volumes.last())
        write(s)
        clock += 125; s.getCurrentPositionUs(false)
        assertEquals(0.5f, volumes.last(), 0.01f)
        clock += 125; s.getCurrentPositionUs(false)
        assertEquals(1f, volumes.last(), 0.001f)
        clock += 500; s.getCurrentPositionUs(false)
        assertEquals(1f, volumes.last(), 0.001f)
    }

    @Test fun aPauseFadesOutFirstAndPlayingAgainFadesIn() {
        val s = sink()
        s.play(); write(s); clock += 300; s.getCurrentPositionUs(false)
        volumes.clear(); calls.clear()
        s.pause()
        assertEquals("Waited for the fade out", 150L, waited)
        assertTrue("Down step by step", volumes.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(0f, volumes.last(), 0.001f)
        assertTrue("Paused once it was silent", calls.indexOf("pause") > calls.lastIndexOf("setVolume") - 1 && "pause" in calls)
        s.play()  // with sound still waiting: the fade in starts at once
        clock += 250; s.getCurrentPositionUs(false)
        assertEquals(1f, volumes.last(), 0.001f)
    }

    @Test fun aSkipFadesOutWhatWasPlayingAndTheNextFadesIn() {
        val s = sink()
        s.play(); write(s); clock += 300; s.getCurrentPositionUs(false)
        waited = 0
        s.flush()  // the listener skipped: sound was still to play
        assertEquals(150L, waited)
        assertEquals("Silent until the next song's sound", 0f, volumes.last())
        write(s)
        clock += 250; s.getCurrentPositionUs(false)
        assertEquals(1f, volumes.last(), 0.001f)
    }

    @Test fun afterASongThatPlayedOutTheNextStartsWhole() {
        val s = sink()
        s.play(); write(s); clock += 300; s.getCurrentPositionUs(false)
        pending = false  // played to its end
        waited = 0; volumes.clear()
        s.flush()
        assertEquals("Nothing to fade", 0L, waited)
        write(s)
        s.getCurrentPositionUs(false)
        assertTrue("Whole throughout", volumes.all { it == 1f })
    }

    @Test fun switchedOffNothingMoves() {
        val s = sink(fadeIn = 0, fadeOut = 0)
        s.play(); write(s); s.getCurrentPositionUs(false)
        s.pause(); s.play(); s.flush(); write(s)
        assertEquals(0L, waited)
        assertTrue(volumes.all { it == 1f })
    }

    @Test fun thePlayersOwnVolumeIsKeptUnderTheFade() {
        val s = sink()
        s.setVolume(0.5f)  // a sleep timer's wind down, say
        s.play(); write(s)
        clock += 125; s.getCurrentPositionUs(false)
        assertEquals(0.25f, volumes.last(), 0.01f)
        clock += 125; s.getCurrentPositionUs(false)
        assertEquals(0.5f, volumes.last(), 0.001f)
    }

    @Test fun theFadeInsShareByTime() {
        assertEquals(0f, FadingSink.levelAt(0, 250))
        assertEquals(0.4f, FadingSink.levelAt(100, 250), 0.001f)
        assertEquals(1f, FadingSink.levelAt(900, 250))
        assertEquals(1f, FadingSink.levelAt(0, 0))
    }
}
