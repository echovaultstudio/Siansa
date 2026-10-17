package app.musix.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** The equaliser's filters, presets and saved Custom bands. */
class EqualiserTest {
    @Test fun everyPresetHasOneValuePerBand() {
        EqPreset.entries.forEach { preset ->
            preset.gains?.let { assertEquals(preset.label, EqSettings.BANDS.size, it.size) }
        }
    }

    @Test fun aBandBoostsItsOwnFrequencyAndLeavesFarOnesAlone() {
        val filter = Biquad.peaking(1_000.0, 6.0, EqSettings.Q, 44_100)
        assertEquals(6.0, filter.responseDb(1_000.0, 44_100), 0.05)
        assertEquals(0.0, filter.responseDb(50.0, 44_100), 0.2)
        assertEquals(0.0, filter.responseDb(15_000.0, 44_100), 0.3)
    }

    @Test fun flatChangesNothing() {
        val design = EqDesign.of(EqSettings(enabled = true), 48_000)
        listOf(30.0, 1_000.0, 12_000.0).forEach { assertEquals(0.0, design.responseDb(it), 1e-6) }
        assertFalse(EqSettings(enabled = true).active)
    }

    @Test fun boostsAreTurnedDownOverallSoTheLoudestPointStaysAtZero() {
        val design = EqDesign.of(EqSettings(enabled = true, preset = EqPreset.BASS), 44_100)
        val peak = generateSequence(20.0) { it * 1.01 }.takeWhile { it < 20_000 }.maxOf { design.responseDb(it) }
        assertEquals(0.0, peak, 0.1)
        // ...and bass still stands out against the rest.
        assertTrue(design.responseDb(60.0) - design.responseDb(5_000.0) > 6)
    }

    @Test fun preampRaisesTheWholeSound() {
        val design = EqDesign.of(EqSettings(enabled = true, preampDb = 3f), 44_100)
        assertEquals(3.0, design.responseDb(1_000.0), 1e-6)
    }

    @Test fun bandsAboveWhatTheSampleRateCarriesAreLeftOut() {
        assertEquals(9, EqDesign.of(EqSettings(enabled = true), 32_000).filters.size)
        assertEquals(10, EqDesign.of(EqSettings(enabled = true), 44_100).filters.size)
    }

    @Test fun movingABandMakesItCustomFromThePresetInUse() {
        val eq = EqSettings(enabled = true, preset = EqPreset.ROCK).withBand(0, 2f)
        assertEquals(EqPreset.CUSTOM, eq.preset)
        assertEquals(2f, eq.gains[0])
        assertEquals(EqPreset.ROCK.gains!!.drop(1), eq.gains.drop(1))
        assertEquals(EqSettings.MAX_DB, eq.withBand(3, 40f).gains[3])
    }

    @Test fun customBandsAreSavedAndReadBack() {
        val values = listOf(1f, -2f, 3f, 0f, 0f, 0f, 4f, 0f, -12f, 12f)
        assertEquals(values, EqSettings.parseCustom(EqSettings.formatCustom(values)))
        assertEquals(EqSettings.FLAT, EqSettings.parseCustom(null))
        assertEquals(EqSettings.FLAT, EqSettings.parseCustom("1,2,3"))
    }

    @Test fun filteringASineChangesItsLevelByTheBandGain() {
        val rate = 48_000
        val eq = EqSettings(enabled = true, preset = EqPreset.CUSTOM, custom = EqSettings.FLAT.toMutableList().also { it[5] = -6f })
        val bank = EqFilterBank(EqDesign.of(eq, rate), channels = 2)
        var inRms = 0.0
        var outRms = 0.0
        for (i in 0 until rate) {
            val x = 0.5 * sin(2 * PI * 1_000 * i / rate)
            val left = bank.process(x, 0)
            bank.process(0.0, 1)
            // Skip the first tenth of a second while the filters settle.
            if (i > rate / 10) { inRms += x * x; outRms += left * left }
        }
        val db = 20 * kotlin.math.log10(sqrt(outRms) / sqrt(inRms))
        assertEquals(-6.0, db, 0.2)
    }

    @Test fun outputNeverGoesPastFullScaleAfterTheLimiter() {
        val bank = EqFilterBank(EqDesign.of(EqSettings(enabled = true, preampDb = 12f), 44_100), channels = 1)
        val limiter = PeakLimiter(44_100, 1)
        repeat(1_000) {
            val frame = doubleArrayOf(bank.process(if (it % 2 == 0) 1.0 else -1.0, 0))
            limiter.process(frame)
            assertTrue(abs(frame[0]) <= 1.0)
        }
    }
}
