package app.siansa.playback.gapless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which way the TV plays the phone's stream, and when to give up (issue #114; 0.52.0-dev: asked for again every 6 s,
 * for ever, in silence): each way once, then the queue.
 */
class StreamTriesTest {
    private val castFlac = StreamWay(StreamFormat.Flac, throughCast = true)
    private val ownFlac = StreamWay(StreamFormat.Flac, throughCast = false)
    private val ownWav = StreamWay(StreamFormat.Wav, throughCast = false)

    @Test fun googlesPlayerFirstThenTheOwnPlayerThenWav() {
        assertEquals(listOf(castFlac, ownFlac, ownWav), StreamWay.ORDER)
        val t = StreamTries()
        assertEquals(castFlac, t.way)
        assertEquals(StreamTries.Next.Retry(ownFlac), t.failed())
        assertEquals(StreamTries.Next.Retry(ownWav), t.failed())
        assertEquals(StreamFormat.Wav, t.format)
        assertEquals(StreamTries.Next.GiveUp, t.failed())
        assertTrue(t.triedLabels().contains("through Google's player"))
    }

    @Test fun aWayThatPlayedStartsTheCountAgain() {
        val t = StreamTries()
        t.failed()
        assertTrue(t.worked())
        assertFalse(t.worked())
        // The own player played; later its stream went nowhere: the other two once more, then give up.
        assertEquals(StreamTries.Next.Retry(castFlac), t.failed())
        assertEquals(StreamTries.Next.Retry(ownWav), t.failed())
        assertEquals(StreamTries.Next.GiveUp, t.failed())
    }

    @Test fun startsWithTheWayThatPlayedBefore() {
        val t = StreamTries(ownWav)
        assertEquals(ownWav, t.way)
        assertEquals(StreamTries.Next.Retry(castFlac), t.failed())
    }

    @Test fun theTvPageIsToldWhichPlayer() {
        assertEquals("cast", castFlac.player)
        assertEquals("element", ownWav.player)
    }
}
