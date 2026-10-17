package app.musix.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The spectrogram's frequency split and how it judges where the sound stops. */
class SpectrogramTest {
    private val rate = 44_100

    /** About [seconds] of "music": many tones from 50 Hz up to [topHz], quieter towards the top. */
    private fun music(topHz: Double, seconds: Double): FloatArray {
        val random = Random(7)
        val tones = List(150) { 50.0 + random.nextDouble() * (topHz - 50) }
        val phases = List(tones.size) { random.nextDouble() * 2 * PI }
        val amps = tones.map { 1 / sqrt(it) }
        val norm = 0.5 / amps.sum()
        return FloatArray((rate * seconds).toInt()) { i ->
            var x = 0.0
            for (t in tones.indices) x += amps[t] * sin(2 * PI * tones[t] * i / rate + phases[t])
            (x * norm).toFloat()
        }
    }

    @Test fun aFullScaleToneReadsZeroDbAtItsFrequency() {
        val analyser = SpectrumAnalyser()
        val hz = 1_000.0
        val tone = FloatArray(analyser.size) { sin(2 * PI * hz * it / rate).toFloat() }
        val frame = analyser.frame(tone)
        val peak = frame.indices.maxBy { frame[it] }
        assertEquals(hz, peak * rate.toDouble() / analyser.size, rate.toDouble() / analyser.size)
        assertEquals(0.0, frame[peak].toDouble(), 1.5)
        assertTrue(frame[(10_000.0 * analyser.size / rate).toInt()] < -100)
    }

    @Test fun musicMadeFromA128kMp3StopsNear16kHz() {
        val spectrogram = Spectrogram(rate)
        spectrogram.add(music(16_000.0, 10.0))
        val cutoff = spectrogram.cutoff.cutoffHz()!!
        assertEquals(16_000.0, cutoff, 400.0)
        assertEquals(SourceVerdict.STOPS_LOW, spectrogram.cutoff.verdict())
    }

    @Test fun musicThatReachesTheTopIsFullRange() {
        val spectrogram = Spectrogram(rate)
        spectrogram.add(music(21_800.0, 10.0))
        assertEquals(SourceVerdict.FULL_RANGE, spectrogram.cutoff.verdict())
    }

    @Test fun aHighQualityLossyCeilingIsToldApart() {
        val spectrogram = Spectrogram(rate)
        spectrogram.add(music(19_800.0, 10.0))
        assertEquals(SourceVerdict.STOPS_NEAR_20K, spectrogram.cutoff.verdict())
    }

    @Test fun noVerdictBeforeEnoughMusicOrDuringSilence() {
        val spectrogram = Spectrogram(rate)
        spectrogram.add(music(16_000.0, 3.0))
        spectrogram.add(FloatArray(rate * 10))
        assertEquals(SourceVerdict.LISTENING, spectrogram.cutoff.verdict())
    }

    @Test fun samplesArrivingInSmallPiecesGiveTheSamePicture() {
        val audio = music(16_000.0, 2.0)
        val whole = Spectrogram(rate).apply { add(audio) }
        val pieces = Spectrogram(rate).apply { audio.toList().chunked(1_000).forEach { add(it.toFloatArray()) } }
        assertTrue(whole.pixels.contentEquals(pieces.pixels))
    }

    @Test fun coloursRunFromBlackToPaleYellow() {
        assertEquals(0xFF000000.toInt(), Spectrogram.colour(-200f))
        assertEquals(0xFFFFF6C8.toInt(), Spectrogram.colour(0f))
    }

    @Test fun verdictWordingDependsOnTheFormat() {
        val fake = Spectrogram.verdictText(SourceVerdict.STOPS_LOW, 16_000.0, "FLAC", 0)
        assertTrue(fake, "probably made from a lossy file" in fake && "16.0 kHz" in fake)
        val mp3 = Spectrogram.verdictText(SourceVerdict.STOPS_LOW, 16_000.0, "MP3", 0)
        assertTrue(mp3, "as expected" in mp3)
        val opus = Spectrogram.verdictText(SourceVerdict.FULL_RANGE, 20_000.0, "FLAC", 160)
        assertTrue(opus, "streaming as Opus 160" in opus)
    }
}
