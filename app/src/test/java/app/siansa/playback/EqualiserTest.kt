package app.siansa.playback

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

    /** The loudness change across 100 Hz – 6 kHz after the turn-down, in dB (issue #159). */
    private fun heardDb(design: EqDesign): Double {
        var sum = 0.0
        var n = 0
        var hz = EqDesign.LOUDNESS_LOW
        while (hz <= EqDesign.LOUDNESS_HIGH) {
            sum += Math.pow(10.0, design.responseDb(hz) / 10)
            n++
            hz *= Math.pow(2.0, 1.0 / 12)
        }
        return 10 * kotlin.math.log10(sum / n)
    }

    /** Switching on keeps the same loudness for every preset that makes music louder (issue #159). */
    @Test fun switchingOnKeepsTheLoudness() {
        EqPreset.entries.filter { it.gains != null }.forEach { preset ->
            val design = EqDesign.of(EqSettings(enabled = true, preset = preset), 44_100)
            val heard = heardDb(design)
            if (design.loudnessDb > 0) assertEquals(preset.label, 0.0, heard, 0.05)
            else assertEquals(preset.label, design.loudnessDb, heard, 0.05)
            assertTrue(preset.label, abs(heard) <= 1.0)
        }
    }

    /** The curve from #159: one +8 dB band no longer turns the middle frequencies down by 8 dB. */
    @Test fun oneBigBoostDoesntDuckTheRest() {
        val eq = EqSettings(enabled = true, preset = EqPreset.CUSTOM, custom = EqSettings.parseCustom("6,5,4,2,0,0,0,0,8,0"))
        val design = EqDesign.of(eq, 44_100)
        assertEquals(8.0, design.peakDb, 0.2)
        assertTrue("turned down by ${design.turnDownDb}", design.turnDownDb in 1.5..2.5)
        listOf(500.0, 1_000.0, 2_000.0).forEach { hz ->
            assertTrue("$hz Hz at ${design.responseDb(hz)} dB", design.responseDb(hz) > -3)
        }
        // ...and bass and 8 kHz still stand out against the middle.
        assertTrue(design.responseDb(60.0) - design.responseDb(1_000.0) > 4)
        assertTrue(design.responseDb(8_000.0) - design.responseDb(1_000.0) > 6)
    }

    @Test fun cutsAreNotMadeUpFor() {
        val eq = EqSettings(enabled = true, preset = EqPreset.CUSTOM, custom = List(10) { -4f })
        val design = EqDesign.of(eq, 44_100)
        assertEquals(0.0, design.turnDownDb, 1e-9)
        assertTrue(design.responseDb(1_000.0) < -3)
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
