package app.siansa.playback.gapless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the TV's stream gets nowhere (issue #114; 0.52.0-dev: asked for again every 6 s, for ever, in silence): the
 * other format once, then the queue.
 */
class StreamTriesTest {
    @Test fun flacThenWavThenGiveUp() {
        val t = StreamTries(StreamFormat.Flac)
        assertEquals(StreamTries.Next.Retry(StreamFormat.Wav), t.failed())
        assertEquals(StreamFormat.Wav, t.format)
        assertEquals(StreamTries.Next.GiveUp, t.failed())
        assertEquals("FLAC and WAV", t.triedLabels())
    }

    @Test fun aFormatThatPlayedStartsTheCountAgain() {
        val t = StreamTries(StreamFormat.Flac)
        t.failed()
        assertTrue(t.worked())
        assertFalse(t.worked())
        // WAV played; later its stream went nowhere: FLAC once more, then give up.
        assertEquals(StreamTries.Next.Retry(StreamFormat.Flac), t.failed())
        assertEquals(StreamTries.Next.GiveUp, t.failed())
    }

    @Test fun startsWithTheFormatThatPlayedBefore() {
        val t = StreamTries(StreamFormat.Wav)
        assertEquals(StreamFormat.Wav, t.format)
        assertEquals(StreamTries.Next.Retry(StreamFormat.Flac), t.failed())
    }
}
