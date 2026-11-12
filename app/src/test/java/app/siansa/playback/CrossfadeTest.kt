package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.data.ReplayGainMode
import app.siansa.data.Song
import app.siansa.net.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.math.pow

/** Builds a ramp blob as the Siansa plugin sends it: (int8 dB, uint16 little-endian chunk) per level. */
internal fun ramp(vararg points: Pair<Int, Int>): String {
    val bytes = ByteArray(points.size * 3)
    points.forEachIndexed { i, (db, chunk) ->
        bytes[i * 3] = db.toByte()
        bytes[i * 3 + 1] = (chunk and 0xFF).toByte()
        bytes[i * 3 + 2] = (chunk shr 8 and 0xFF).toByte()
    }
    return Base64.getEncoder().encodeToString(bytes)
}

private fun profileJson(id: String, lufs: Double?, start: String, end: String, duration: Int = 200_000) =
    """{"track_id":"$id","source":"waveform","sample_rate":44100,"duration_ms":$duration,""" +
        (if (lufs != null) """"ref_lufs":$lufs,""" else "") +
        """"start_ramp":"$start","end_ramp":"$end","analyzer_ver":1}"""

private fun answer(vararg profiles: String, missing: List<String> = emptyList(), schema: Int = 1) =
    """{"schema_version":$schema,"analyzer_version":1,"catalog_instance_id":"x","profiles":[${profiles.joinToString(",")}],""" +
        """"missing":[${missing.joinToString(",") { "\"$it\"" }}],"failed":[]}"""

/** The Siansa plugin's ramps and answers (issue #60). */
class PluginProfilesTest {
    @Test fun decodesRampsWithSignedLevelsAndLittleEndianChunks() {
        val points = RampCodec.decode(ramp(-90 to 0, -18 to 12, 3 to 300, 6 to 0x1234))
        assertEquals(listOf(RampPoint(-90, 0), RampPoint(-18, 1_200), RampPoint(3, 30_000), RampPoint(6, 0x1234 * 100L)), points)
    }

    @Test fun badRampsAreEmpty() {
        assertTrue(RampCodec.decode(null).isEmpty())
        assertTrue(RampCodec.decode("").isEmpty())
        assertTrue(RampCodec.decode("not base64 !!").isEmpty())
        // Not a whole number of entries.
        assertTrue(RampCodec.decode(Base64.getEncoder().encodeToString(byteArrayOf(1, 2))).isEmpty())
    }

    @Test fun rampsAnswerWhenASongGetsGoingAndWhenItGoesQuiet() {
        val p = TrackProfile("a", -9.0, RampCodec.decode(ramp(-60 to 1, -18 to 25, 0 to 40)), RampCodec.decode(ramp(-60 to 2, -18 to 60, 0 to 90)))
        assertEquals(2_500L, p.reachesAfterMs(-18))
        assertEquals(2_500L, p.reachesAfterMs(-24))
        assertEquals(6_000L, p.quietForLastMs(-18))
        assertNull(p.reachesAfterMs(3))
    }

    @Test fun readsProfilesAndLeavesOutTheRest() {
        val text = answer(
            profileJson("s1", -8.5, ramp(-18 to 3), ramp(-18 to 40)),
            profileJson("s2", null, ramp(-18 to 0), ramp(-18 to 0)),
            missing = listOf("s3"),
        )
        val profiles = PluginProfiles.parse(text)
        assertEquals(listOf("s1", "s2"), profiles.map { it.trackId })
        assertEquals(-8.5, profiles[0].refLufs!!, 0.0)
        assertEquals(300L, profiles[0].reachesAfterMs(-18))
        assertEquals(4_000L, profiles[0].quietForLastMs(-18))
        assertEquals(200_000L, profiles[0].durationMs)
        assertNull(profiles[1].refLufs)
    }

    /** Issue #188: an answer exactly as `audiomuse-plugin/` sends it (its test_loudness checks the same blob). */
    @Test fun readsOurPluginsAnswer() {
        val text = """{"schema_version":1,"profiles":[{"track_id":"nd-1","ref_lufs":-9.25,"start_ramp":"7iwBBgEA",""" +
            """"end_ramp":"","duration_ms":201000}],"pending":["nd-2"]}"""
        val p = PluginProfiles.parse(text).single()
        assertEquals("nd-1", p.trackId)
        assertEquals(listOf(RampPoint(-18, 30_000), RampPoint(6, 100)), p.startRamp)
        assertTrue(p.endRamp.isEmpty())
        assertEquals(201_000L, p.durationMs)
        // Siansa Dev's experiment: Siansa Beats (dev), next to the Siansa plugin.
        assertEquals("/plugins/siansa_beats/api/profiles", PluginProfiles.PATH)
        assertEquals("/plugins/siansa_beats/api/health", ProfileStore.HEALTH_PATH)
        assertEquals("siansa_beats", ProfileStore.PLUGIN_NAME)
    }

    @Test fun theStoredChoiceIsRead() {
        assertEquals(LoudnessSource.PLUGIN, LoudnessSource.parse("PLUGIN"))
        assertEquals(LoudnessSource.TAGS, LoudnessSource.parse("TAGS"))
        assertEquals(LoudnessSource.TAGS, LoudnessSource.parse(null))
    }

    @Test fun aNewerLayoutIsNotMisread() {
        assertTrue(PluginProfiles.parse(answer(profileJson("s1", -8.0, ramp(), ramp()), schema = 2)).isEmpty())
        assertTrue(PluginProfiles.parse("<html>login</html>").isEmpty())
    }

    @Test fun batchesKeepRequestsShortAndUnderTheIdLimit() {
        val base = "https://audiomuse.example.com" + PluginProfiles.PATH
        val ids = (1..2_000).map { "song-id-%05d".format(it) }
        val batches = PluginProfiles.batches(ids, base)
        assertEquals(ids, batches.flatten())
        batches.forEach { batch ->
            assertTrue((base + PluginProfiles.query(batch)).length <= PluginProfiles.URL_BUDGET_BYTES)
            assertTrue(batch.size <= PluginProfiles.MAX_IDS)
        }
        // Full batches use the budget: no batch is followed by one that would have fitted into it.
        val first = base + PluginProfiles.query(batches[0] + batches[1].first())
        assertTrue(first.length > PluginProfiles.URL_BUDGET_BYTES)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), PluginProfiles.batches(listOf("a", "b", "a", "c"), base, maxIds = 2))
    }

    @Test fun queryEncodesIds() {
        assertEquals("?ids=a%2Fb,c+d", PluginProfiles.query(listOf("a/b", "c d")))
    }
}

/** Where crossfades start and how long they are (issue #60). */
class CrossfadePlanTest {
    private val a1 = Song("a1", "One", albumId = "A", track = 1, disc = 1)
    private val a2 = Song("a2", "Two", albumId = "A", track = 2, disc = 1)
    private val a5 = Song("a5", "Five", albumId = "A", track = 5, disc = 1)
    private val b1 = Song("b1", "Other", albumId = "B", track = 1)
    private val minutes4 = 240_000L

    private fun profile(id: String, introMs: Int, tailMs: Int) =
        TrackProfile(id, -10.0, listOf(RampPoint(-18, introMs.toLong())), listOf(RampPoint(-18, tailMs.toLong())))

    @Test fun offMeansNoFade() {
        assertNull(Crossfade.plan(CrossfadeMode.OFF, 6_000, a1, b1, minutes4, minutes4))
    }

    @Test fun fixedFadesTheWholeLength() {
        val plan = Crossfade.plan(CrossfadeMode.FIXED, 6_000, a1, b1, minutes4, minutes4)!!
        assertEquals(6_000L, plan.overlapMs)
        assertEquals(6_000L, plan.fadeInMs)
        // An album playing in order stays gapless with Fixed too (the owner, 9 Nov).
        assertNull(Crossfade.plan(CrossfadeMode.FIXED, 6_000, a1, a2, minutes4, minutes4))
    }

    @Test fun smartKeepsAlbumsGaplessWhenTheyRunStraightOn() {
        assertNull(Crossfade.plan(CrossfadeMode.SMART, 6_000, a1, a2, minutes4, minutes4))
        assertTrue(Crossfade.runsStraightOn(Song("x", "", albumId = "A", track = 12, disc = 1), Song("y", "", albumId = "A", track = 1, disc = 2)))
        // Skipping ahead in the album, or another album, still fades.
        assertNotNull(Crossfade.plan(CrossfadeMode.SMART, 6_000, a1, a5, minutes4, minutes4))
        assertNotNull(Crossfade.plan(CrossfadeMode.SMART, 6_000, a1, b1, minutes4, minutes4))
        assertFalse(Crossfade.runsStraightOn(Song("x", ""), Song("y", "")))
    }

    @Test fun smartFollowsTheQuietTailAndTheIntro() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 12_000, a1, b1, minutes4, minutes4, profile("a1", 0, 5_000), profile("b1", 2_000, 0))!!
        assertEquals(7_000L, plan.overlapMs)
        // The old song ends quietly (5 s): the next one builds up over the whole of it (the owner, 9 Nov), no longer
        // coming in over 2 s and then playing at full level over the quiet end.
        assertEquals(7_000L, plan.fadeInMs)
        assertTrue(plan.buildUp)
    }

    @Test fun aHardEndingIntoAHardStartIsAColdEndWithAShortPause() {
        // 0.52.0-dev, 09:19: a 400 ms tail into a 0 ms intro overlapped for 400 ms, two loud edges colliding.
        val plan = Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, profile("a1", 0, 400), profile("b1", 0, 0))!!
        assertTrue(plan.why, plan.gapMs > 0)
        // No overlap: the old song's own last 400 ms (its tail) play out, the next song comes in after them and the pause.
        assertEquals(400L, plan.overlapMs)
        assertEquals(Crossfade.MIN_FADE_IN_MS, plan.fadeInMs)
        assertNull(plan.beat)
        assertTrue(plan.why, plan.why.contains("hard ending into a hard start"))
        // A song loud to its very last sound still fades over a moment, only so it can't click.
        assertEquals(Crossfade.COLD_OUT_MS, Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, profile("a1", 0, 0), profile("b1", 100, 0))!!.overlapMs)
    }

    @Test fun onlyBothEdgesHardMakeAColdEnd() {
        // A soft start under a hard ending, or a hard start after a soft ending, blends as before.
        val softStart = Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, profile("a1", 0, 400), profile("b1", 1_400, 0))!!
        assertEquals(0L, softStart.gapMs)
        assertEquals(1_800L, softStart.overlapMs)
        val softEnd = Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, profile("a1", 0, 3_000), profile("b1", 0, 0))!!
        assertEquals(0L, softEnd.gapMs)
        assertEquals(3_000L, softEnd.overlapMs)
        // Just past the edges: a 1 s tail, a 300 ms intro.
        assertNull(Crossfade.coldEnd(Crossfade.COLD_TAIL_MS, 0, 0, null, null))
        assertNull(Crossfade.coldEnd(0, Crossfade.COLD_HEAD_MS, 0, null, null))
        assertNotNull(Crossfade.coldEnd(Crossfade.COLD_TAIL_MS - 1, Crossfade.COLD_HEAD_MS - 1, 0, null, null))
        // Within one album running straight on it stays gapless, and fixed or off are untouched.
        assertNull(Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, a2, minutes4, minutes4, profile("a1", 0, 0), profile("a2", 0, 0)))
        assertEquals(0L, Crossfade.plan(CrossfadeMode.FIXED, 6_000, a1, b1, minutes4, minutes4, profile("a1", 0, 0), profile("b1", 0, 0))!!.gapMs)
    }

    @Test fun aColdEndBetweenTwoBeatsHasNoPause() {
        // 0.52.0-dev, 20:19, a DJ radio: a 132 BPM house track loud to its end into 176 BPM drum and bass loud from its
        // start (too far apart to match) got 0.6 s of silence between them. Two beats run straight on instead.
        val house = BeatEnd(bpm = 132.0, beatMs = 230_000.0, barMs = 230_000.0, rhythm = 0.88f, steady = 0.88f)
        val dnb = BeatEnd(bpm = 176.0, beatMs = 0.0, barMs = null, rhythm = 0.84f, steady = 0.86f)
        val out = TrackProfile("a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-18, 0)), outro = house)
        val inn = TrackProfile("b1", -10.0, listOf(RampPoint(-18, 0)), emptyList(), intro = dnb)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, out, inn, beatMatch = true)!!
        assertTrue(plan.why, plan.coldEnd)
        assertEquals(0L, plan.gapMs)
        assertEquals(Crossfade.COLD_OUT_MS, plan.overlapMs)
        assertTrue(plan.why, plan.why.contains("no pause"))
        // One side without a beat (Yor Zarad's 0.15 into a beat): outside DJ playback the pause, as before; in it, never
        // a pause (the owner's bad blends, 9 Nov).
        val beatless = out.copy(outro = house.copy(rhythm = 0.15f, steady = 0.0f))
        assertTrue(Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, beatless, inn, beatMatch = false)!!.gapMs > 0)
        assertEquals(0L, Crossfade.plan(CrossfadeMode.SMART, 24_000, a1, b1, minutes4, minutes4, beatless, inn, beatMatch = true)!!.gapMs)
    }

    @Test fun aSoftEndFadesIntoTheNextSong() {
        // The owner's DJ mix, 12 Nov: "Everything U" (132 BPM, its end not steady enough to lay on a beat) into a
        // 128 BPM song got 1.5 s, "beats would clash". He hears that end as quiet, and wants quiet ends to fade.
        val end = BeatEnd(bpm = 132.0, beatMs = 203_800.0, barMs = 203_800.0, rhythm = 1.0f, steady = 0.74f, lockMs = 203_800.0)
        val start = BeatEnd(bpm = 128.0, beatMs = 1_100.0, barMs = 1_100.0, rhythm = 1.0f, steady = 0.92f, lockMs = 1_100.0)
        fun out(softMs: Long, key: String? = "A", scale: String? = "minor") = TrackProfile(
            "a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-18, 2_200), RampPoint(-6, softMs)), outro = end, key = key, scale = scale)
        fun inn(key: String? = "A", scale: String? = "minor") =
            TrackProfile("b1", -10.0, listOf(RampPoint(-18, 100)), emptyList(), intro = start, key = key, scale = scale)
        fun plan(o: TrackProfile, i: TrackProfile, max: Long = 30_000) =
            Crossfade.plan(CrossfadeMode.SMART, max, a1, b1, minutes4, minutes4, o, i, beatMatch = true)!!

        // Its last 14 s are under -6 dB: faded over, as long as a soft blend goes.
        val soft = plan(out(14_000), inn())
        assertEquals(soft.why, Crossfade.SOFT_BLEND_MAX_MS, soft.overlapMs)
        assertTrue(soft.why, soft.why.contains("a soft end"))
        assertEquals(null, soft.beat)
        assertEquals(false, soft.coldEnd)
        // Eight soft seconds: over those; never longer than the crossfade length set.
        assertEquals(8_000L, plan(out(8_000), inn()).overlapMs)
        assertEquals(6_000L, plan(out(14_000), inn(), max = 6_000).overlapMs)
        // Keys that aren't known don't stop it.
        assertEquals(Crossfade.SOFT_BLEND_MAX_MS, plan(out(14_000, key = null, scale = null), inn()).overlapMs)
        // Keys that clash (A minor into D# minor), or an end soft for a moment only: the short blend, as before.
        val clash = plan(out(14_000), inn("D#", "minor"))
        assertTrue(clash.why, clash.overlapMs <= BeatMatch.CLASH_MS && clash.why.contains("the keys clash"))
        val loud = plan(out(3_000), inn())
        assertTrue(loud.why, loud.overlapMs <= BeatMatch.CLASH_MS && loud.why.contains("soft for the last 3.0 s"))
    }

    @Test fun aColdEndPausesLongerWhenTheTemposAreFarApart() {
        assertEquals(Crossfade.COLD_GAP_MS, Crossfade.coldGap(120.0, 126.0))
        assertEquals(Crossfade.COLD_GAP_FAR_MS, Crossfade.coldGap(145.0, 191.5))
        assertEquals(Crossfade.COLD_GAP_FAR_MS, Crossfade.coldGap(null, 120.0))
        assertTrue(Crossfade.COLD_GAP_MS < Crossfade.COLD_GAP_FAR_MS)
    }

    @Test fun smartFadeIsNeverLongerThanTheSetting() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 4_000, a1, b1, minutes4, minutes4, profile("a1", 0, 9_000), profile("b1", 3_000, 0))!!
        assertEquals(4_000L, plan.overlapMs)
        assertTrue(plan.fadeInMs <= plan.overlapMs)
    }

    @Test fun aBeatlessOutroBlendsAfterTheLastBeat() {
        // The beat stops at 3:40 of a 4:00 song at 120 BPM, then 18 s of outro and 2 s of silence: the next song
        // comes in a beat after the last (3:40.5), over the outro to the end of its sound, fade and all.
        // The outro winds down: quiet (under −18 dB) for its last 10 s before the silence.
        val outro = BeatEnd(bpm = 120.0, beatMs = 219_500.0, barMs = null, rhythm = 0.9f, steady = 0.9f)
        val current = TrackProfile("a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-48, 2_000), RampPoint(-18, 12_000)), outro = outro, beatsUntilMs = 220_000)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, minutes4, current, profile("b1", 0, 0), beatMatch = true)!!
        assertEquals(17_500L, plan.overlapMs)
        assertEquals(2_000L, plan.cutMs)
        assertNull(plan.beat)
        assertTrue(plan.why, plan.why.contains("after the last beat (3:40)"))
    }

    @Test fun aLoudBeatlessOutroGetsOnlyAShortBlendAtItsEnd() {
        // 0.52.0-dev, 20:06: a loud track's 33.6 s beatless outro played 30 s under the next song's start, both loud.
        // Loud to its last 0.1 s before the silence: only its last 6 s are blended.
        val outro = BeatEnd(bpm = 120.0, beatMs = 219_500.0, barMs = null, rhythm = 0.9f, steady = 0.9f)
        val loud = TrackProfile("a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-48, 2_000), RampPoint(-18, 2_100)), outro = outro, beatsUntilMs = 220_000)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, minutes4, loud, profile("b1", 0, 0), beatMatch = true)!!
        assertEquals(Crossfade.BEATLESS_LOUD_MS, plan.overlapMs)
        assertEquals(2_000L, plan.cutMs)
        assertTrue(plan.why, plan.why.contains("stays loud"))
        // A long loud outro the same: never the half minute.
        val long = TrackProfile("a1", -10.0, emptyList(), listOf(RampPoint(-18, 1_000)), beatsUntilMs = 150_000)
        assertEquals(Crossfade.BEATLESS_LOUD_MS, Crossfade.afterLastBeat(long, minutes4, minutes4)!!.overlapMs)
        // Not measured: counts as loud.
        assertEquals(Crossfade.BEATLESS_LOUD_MS, Crossfade.afterLastBeat(TrackProfile("a1", -10.0, emptyList(), emptyList(), beatsUntilMs = 150_000), minutes4, minutes4)!!.overlapMs)
    }

    @Test fun aLongBeatlessOutroIsJoinedForItsLastHalfMinute() {
        // An ambient outro that winds down: quiet for its last minute.
        val current = TrackProfile("a1", -10.0, emptyList(), listOf(RampPoint(-18, 60_000)), beatsUntilMs = 150_000)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, minutes4, current, profile("b1", 0, 0))!!
        assertEquals(Crossfade.AFTER_BEAT_MAX_MS, plan.overlapMs)
        // Never more than a third of a short next song.
        val short = Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, 60_000, current, profile("b1", 0, 0))!!
        assertEquals(20_000L, short.overlapMs)
    }

    @Test fun aShortBeatlessEndIsLeftToTheOtherBlends() {
        assertNull(Crossfade.afterLastBeat(TrackProfile("a1", -10.0, emptyList(), emptyList(), beatsUntilMs = 236_000), minutes4, minutes4))
        // Within one album running straight on, it stays gapless.
        assertNull(Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, a2, minutes4, minutes4, TrackProfile("a1", -10.0, emptyList(), emptyList(), beatsUntilMs = 150_000)))
    }

    @Test fun theLastBeatIsReadFromTheProfile() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"schema_version":1,"profiles":[{"track_id":"x","ref_lufs":-9.0,"start_ramp":"","end_ramp":"","beats_until_ms":201234.5}]}""",
        ) as JsonObject
        assertEquals(201_234L, PluginProfiles.parse(body).single().beatsUntilMs)
    }

    @Test fun aDjBlendWithABeatlessEndRunsTheWholeCrossfadeLength() {
        // 0.52.0-dev, 16:51: a beat-driven outro (not steady enough to match) into a beatless intro got 3.3 s with
        // 30 s set. In a DJ blend nothing can clash, so it runs the whole length; the next song fades in over half.
        val out = BeatEnd(bpm = 124.6, beatMs = 230_000.0, barMs = null, rhythm = 0.85f, steady = 0.2f)
        val inn = BeatEnd(bpm = 130.8, beatMs = 500.0, barMs = null, rhythm = 0.0f, steady = 0.0f)
        val current = TrackProfile("a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-18, 3_000)), outro = out)
        val next = TrackProfile("b1", -10.0, listOf(RampPoint(-18, 300)), emptyList(), intro = inn)
        val dj = Crossfade.plan(CrossfadeMode.SMART, 30_000, a1, b1, minutes4, minutes4, current, next, beatMatch = true)!!
        assertEquals(30_000L, dj.overlapMs)
        assertEquals(15_000L, dj.fadeInMs)
        assertTrue(dj.why, dj.why.contains("nothing to clash"))
        // Outside DJ blends it follows the songs' shapes as before.
        assertEquals(3_300L, Crossfade.plan(CrossfadeMode.SMART, 30_000, a1, b1, minutes4, minutes4, current, next)!!.overlapMs)
        // Two beats that can't be matched still get a short blend.
        val beaty = next.copy(intro = inn.copy(rhythm = 0.9f))
        assertEquals(BeatMatch.CLASH_MS, Crossfade.plan(CrossfadeMode.SMART, 30_000, a1, b1, minutes4, minutes4, current, beaty, beatMatch = true)!!.overlapMs)
    }

    @Test fun aDjBlendWithABeatJustShortOfBeatDrivenStaysShort() {
        // 0.52.0-dev: Jon Hopkins (George Fitzgerald remix), outro read beat 0.46 steady 0.16, into Cubicolor
        // (beat 0.87) got the whole 30 s, out of step. It has a beat, just not a steady one: a short blend.
        val out = BeatEnd(bpm = 123.2, beatMs = 300_000.0, barMs = null, rhythm = 0.46f, steady = 0.16f)
        val inn = BeatEnd(bpm = 121.0, beatMs = 500.0, barMs = 500.0, rhythm = 0.87f, steady = 0.88f)
        val current = TrackProfile("a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-18, 2_000)), outro = out)
        val next = TrackProfile("b1", -10.0, listOf(RampPoint(-18, 0)), emptyList(), intro = inn)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 30_000, a1, b1, minutes4, minutes4, current, next, beatMatch = true)!!
        assertEquals(BeatMatch.CLASH_MS, plan.overlapMs)
        assertTrue(plan.why, plan.why.contains("both ends have some beat"))
        // Truly beatless (0.10) and winding down: the whole length.
        val ambient = current.copy(outro = out.copy(rhythm = 0.10f), endRamp = listOf(RampPoint(-18, 8_000)))
        assertEquals(30_000L, Crossfade.plan(CrossfadeMode.SMART, 30_000, a1, b1, minutes4, minutes4, ambient, next, beatMatch = true)!!.overlapMs)
    }

    @Test fun aLoudBeatlessEndingGetsAShortBlendNotTheWholeLength() {
        // 0.52.0-dev, morning drive: a song with no steady beat (0.10) but loud to its end (1.9 s tail) into a
        // beat-driven intro got the whole 30 s, starting at 3:00 of 3:32, the next song's beat over the old song.
        val out = BeatEnd(bpm = 161.1, beatMs = 200_000.0, barMs = null, rhythm = 0.10f, steady = 0.0f)
        val inn = BeatEnd(bpm = 116.0, beatMs = 500.0, barMs = 500.0, rhythm = 0.65f, steady = 0.46f)
        val current = TrackProfile("a1", -10.0, listOf(RampPoint(-18, 0)), listOf(RampPoint(-18, 1_900)), outro = out)
        val next = TrackProfile("b1", -10.0, listOf(RampPoint(-18, 1_500)), emptyList(), intro = inn)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 30_000, a1, b1, minutes4, minutes4, current, next, beatMatch = true)!!
        assertEquals(Crossfade.BEATLESS_LOUD_MS, plan.overlapMs)
        assertTrue(plan.why, plan.why.contains("still going, so a short blend"))
        // Two beatless folk songs, loud at both ends (0.4 s each), got 15 s over each other: short too.
        val folkOut = current.copy(outro = out.copy(rhythm = 0.19f), endRamp = listOf(RampPoint(-18, 400)))
        val folkIn = next.copy(intro = inn.copy(rhythm = 0.23f, steady = 0.0f), startRamp = listOf(RampPoint(-18, 400)))
        assertEquals(Crossfade.BEATLESS_LOUD_MS, Crossfade.plan(CrossfadeMode.SMART, 15_000, a1, b1, minutes4, minutes4, folkOut, folkIn, beatMatch = true)!!.overlapMs)
    }

    @Test fun whichBeatlessBlendsRunTheWholeLength() {
        // A beatless intro under a song that has a beat: always.
        assertTrue(Crossfade.beatlessRunsLong(outBeatless = false, inBeatless = true, tailMs = 0, headMs = 0))
        // A beatless song going out: only when it winds down.
        assertFalse(Crossfade.beatlessRunsLong(outBeatless = true, inBeatless = false, tailMs = 1_900, headMs = 1_500))
        assertTrue(Crossfade.beatlessRunsLong(outBeatless = true, inBeatless = false, tailMs = 6_000, headMs = 0))
        // Both beatless: when one of them is quiet there.
        assertFalse(Crossfade.beatlessRunsLong(outBeatless = true, inBeatless = true, tailMs = 400, headMs = 400))
        assertTrue(Crossfade.beatlessRunsLong(outBeatless = true, inBeatless = true, tailMs = 400, headMs = 7_000))
    }

    @Test fun aHardEndingIntoAHardHitIsAColdEnd() {
        // Once played straight back to back; now the old song's last sound, a short pause, then the next song.
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, minutes4, profile("a1", 0, 100), profile("b1", 100, 0))!!
        assertEquals(Crossfade.COLD_GAP_FAR_MS, plan.gapMs)
        assertEquals(100L, plan.overlapMs)
    }

    @Test fun aHardHitStillFadesInBriefly() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, minutes4, profile("a1", 0, 3_000), profile("b1", 0, 0))!!
        assertEquals(3_000L, plan.overlapMs)
        assertEquals(Crossfade.MIN_FADE_IN_MS, plan.fadeInMs)
    }

    @Test fun smartWithoutProfilesUsesTheFixedLength() {
        assertEquals(6_000L, Crossfade.plan(CrossfadeMode.SMART, 6_000, a1, b1, minutes4, minutes4, null, profile("b1", 0, 0))!!.overlapMs)
    }

    @Test fun shortSongsFadeForAThirdAtMost() {
        val plan = Crossfade.plan(CrossfadeMode.FIXED, 12_000, a1, b1, 15_000, minutes4)!!
        assertEquals(5_000L, plan.overlapMs)
        assertEquals(5_000L, plan.fadeInMs)
        assertNull(Crossfade.plan(CrossfadeMode.FIXED, 12_000, a1, b1, 600, minutes4))
    }

    @Test fun fadesKeepTheSameLoudnessThroughout() {
        for (i in 0..20) {
            val t = i / 20f
            val power = Crossfade.fadeOut(t).pow(2) + Crossfade.fadeIn(t).pow(2)
            assertEquals("at $t", 1f, power, 0.001f)
        }
        assertEquals(1f, Crossfade.fadeOut(0f), 0f)
        assertEquals(0f, Crossfade.fadeIn(0f), 0f)
        assertEquals(1f, Crossfade.fadeIn(2f), 0.0001f)
    }

    @Test fun aFadeEndsWhenTheQueueIsClearedOrItsNextSongTakenOut() {
        // Issue #822: stopping the player mid-fade left the old song playing from its silent copy.
        assertNull(Crossfade.incomingAfterQueueChange(emptyList(), "b", 1))
        assertNull(Crossfade.incomingAfterQueueChange(listOf("x", "y"), "b", 1))
        assertNull(Crossfade.incomingAfterQueueChange(listOf("a", "b"), null, 1))
        // Unchanged, or songs added after it: it carries on where it is.
        assertEquals(1, Crossfade.incomingAfterQueueChange(listOf("a", "b", "c", "d"), "b", 1))
        // A song taken out before it (a radio trimming what was played): it follows.
        assertEquals(0, Crossfade.incomingAfterQueueChange(listOf("b", "c"), "b", 1))
        // In the queue twice: the place nearest where it was.
        assertEquals(4, Crossfade.incomingAfterQueueChange(listOf("b", "x", "y", "z", "b"), "b", 5))
    }
}

/** Levelling from measured loudness, with ReplayGain tags as the fallback (issue #60). */
class LevellingTest {
    private val tagged = Song("s", "Song", trackGain = -6f, albumGain = -9f)
    private val plugin = AppSettings(replayGain = ReplayGainMode.TRACK, loudnessSource = LoudnessSource.PLUGIN, loudnessTarget = -18)
    private fun measured(lufs: Double, id: String = "s") = TrackProfile(id, lufs, emptyList(), emptyList())

    @Test fun bringsLoudSongsDownToTheTarget() {
        assertEquals(10.0.pow(-8 / 20.0).toFloat(), Levelling.volume(measured(-10.0), -18)!!, 0.0001f)
        assertEquals(10.0.pow(-4 / 20.0).toFloat(), Levelling.volume(measured(-10.0), -14)!!, 0.0001f)
    }

    @Test fun quietSongsStayAtFullVolume() {
        assertEquals(1f, Levelling.volume(measured(-25.0), -18)!!, 0f)
        assertNull(Levelling.volume(TrackProfile("s", null, emptyList(), emptyList()), -18))
    }

    @Test fun measuredLoudnessWinsOverTagsWithThePluginSource() {
        assertEquals(10.0.pow(-8 / 20.0).toFloat(), Levelling.songVolume(tagged, plugin, measured(-10.0)), 0.0001f)
    }

    @Test fun tagsAreUsedOtherwise() {
        val tags = replayGainVolume(tagged, plugin)
        // Song tags chosen as the source, no measurement, or a measurement of another song.
        assertEquals(tags, Levelling.songVolume(tagged, plugin.copy(loudnessSource = LoudnessSource.TAGS), measured(-10.0)), 0f)
        assertEquals(tags, Levelling.songVolume(tagged, plugin, null), 0f)
        assertEquals(tags, Levelling.songVolume(tagged, plugin, measured(-10.0, id = "other")), 0f)
    }

    @Test fun albumModeKeepsAlbumTags() {
        val album = plugin.copy(replayGain = ReplayGainMode.ALBUM)
        assertEquals(replayGainVolume(tagged, album), Levelling.songVolume(tagged, album, measured(-10.0)), 0f)
        // Without album tags, the song's measurement.
        assertEquals(10.0.pow(-8 / 20.0).toFloat(), Levelling.songVolume(Song("s", "x"), album, measured(-10.0)), 0.0001f)
    }

    @Test fun replayGainOffMeansNoLevelling() {
        assertEquals(1f, Levelling.songVolume(tagged, plugin.copy(replayGain = ReplayGainMode.OFF), measured(-5.0)), 0f)
    }

    /**
     * Issue #214: a song is levelled by the Siansa plugin or by ReplayGain, never both. Across every setting and every
     * kind of song, the volume is exactly the plugin's alone, ReplayGain's alone, or untouched: never the two
     * multiplied together.
     */
    @Test fun neverPluginAndReplayGainTogether() {
        val songs = listOf(tagged, Song("s", "No tags"), Song("s", "Track only", trackGain = -3f), Song("s", "Album only", albumGain = -7f))
        val profiles = listOf(null, measured(-10.0), measured(-30.0), measured(-10.0, id = "other"), TrackProfile("s", null, emptyList(), emptyList()))
        for (mode in ReplayGainMode.entries) for (source in LoudnessSource.entries) for (target in Levelling.TARGETS) {
            val settings = AppSettings(replayGain = mode, loudnessSource = source, loudnessTarget = target)
            for (song in songs) for (profile in profiles) {
                val d = Levelling.decide(song, settings, profile)
                val pluginOnly = Levelling.volume(profile, target)
                val tagsOnly = replayGainVolume(song, settings)
                val case = "$mode $source $target ${song.title} ${profile?.trackId}/${profile?.refLufs} → $d"
                when (d.source) {
                    Levelling.Source.PLUGIN -> {
                        assertEquals(case, pluginOnly!!, d.volume, 0f)
                        assertEquals(case, LoudnessSource.PLUGIN, source)
                    }
                    Levelling.Source.REPLAYGAIN -> assertEquals(case, tagsOnly, d.volume, 0f)
                    Levelling.Source.NONE -> assertEquals(case, 1f, d.volume, 0f)
                }
                // Both applied would be their product: never that (unless one of them changes nothing).
                if (pluginOnly != null && pluginOnly < 1f && tagsOnly < 1f) {
                    assertTrue(case, kotlin.math.abs(d.volume - pluginOnly * tagsOnly) > 1e-6f)
                }
                assertEquals(case, d.volume, Levelling.songVolume(song, settings, profile), 0f)
            }
        }
    }

    @Test fun theDecisionSaysWhy() {
        assertEquals(Levelling.Source.PLUGIN, Levelling.decide(tagged, plugin, measured(-10.0)).source)
        assertEquals(-8.0, Levelling.decide(tagged, plugin, measured(-10.0)).gainDb, 0.01)
        val notYet = Levelling.decide(tagged, plugin, null)
        assertEquals(Levelling.Source.REPLAYGAIN, notYet.source)
        assertTrue(notYet.why, notYet.why.contains("hasn't measured"))
        assertEquals(Levelling.Source.NONE, Levelling.decide(Song("s", "x"), plugin.copy(loudnessSource = LoudnessSource.TAGS), null).source)
        assertEquals(Levelling.Source.NONE, Levelling.decide(null, plugin, null).source)
    }
}

/** Fetching and keeping profiles (issue #60). */
class ProfileStoreTest {
    private var settings = AppSettings(audioMuseUrl = "http://am:8000", loudnessSource = LoudnessSource.PLUGIN)
    private var time = 1_000_000L
    private val asked = mutableListOf<String>()
    private var reply: (String) -> JsonElement = { path ->
        val ids = path.substringAfter("ids=").split(",")
        PluginProfilesJson.parse(answer(*ids.filter { it != "unknown" }.map { profileJson(it, -9.0, ramp(-18 to 5), ramp(-18 to 20)) }.toTypedArray()))
    }
    private val store = ProfileStore({ path -> asked += path; reply(path) }, { settings }, { time })

    @Test fun fetchesOnceAndKeepsThem() = runBlocking {
        val found = store.fetch(listOf("a", "b"))
        assertEquals(setOf("a", "b"), found.keys)
        assertEquals(1, asked.size)
        assertTrue(asked[0].startsWith(PluginProfiles.PATH + "?ids="))
        store.fetch(listOf("a", "b"))
        assertEquals(1, asked.size)
        assertNotNull(store.cached("a"))
        // Only the new one is asked for.
        store.fetch(listOf("a", "c"))
        assertEquals(PluginProfiles.PATH + "?ids=c", asked.last())
    }

    @Test fun songsNotAnalysedYetAreAskedForAgainLater() = runBlocking {
        store.fetch(listOf("unknown"))
        store.fetch(listOf("unknown"))
        assertEquals(1, asked.size)
        time += ProfileStore.RETRY_MISSING_MS + 1
        store.fetch(listOf("unknown"))
        assertEquals(2, asked.size)
    }

    @Test fun aProfileWithoutSiansaBeatsIsFetchedAgainLater() = runBlocking {
        // The owner's bad blend #1: a profile fetched before Siansa Beats had the song was kept all morning.
        store.fetch(listOf("a"))
        store.fetch(listOf("a"))
        assertEquals(1, asked.size)
        time += ProfileStore.RETRY_MISSING_MS + 1
        store.fetch(listOf("a"))
        assertEquals(2, asked.size)
        assertNotNull(store.cached("a"))  // kept meanwhile, and after
    }

    @Test fun aFailingPluginIsLeftAloneForAWhile() = runBlocking {
        reply = { throw ApiException("AudioMuse answered HTTP 404: not found") }
        assertTrue(store.fetch(listOf("a")).isEmpty())
        assertEquals(app.siansa.net.AudioMuseErrors.PLUGIN_MISSING, store.problem)
        store.fetch(listOf("a"))
        assertEquals(1, asked.size)
        time += ProfileStore.BACK_OFF_MS + 1
        store.fetch(listOf("a"))
        assertEquals(2, asked.size)
    }

    @Test fun nothingIsFetchedWithSongTags() = runBlocking {
        settings = settings.copy(loudnessSource = LoudnessSource.TAGS)
        assertTrue(store.fetch(listOf("a")).isEmpty())
        assertTrue(asked.isEmpty())
        assertNull(store.cached("a"))
    }

    @Test fun aNewerLayoutFallsBackToTags() = runBlocking {
        reply = { PluginProfilesJson.parse(answer(profileJson("a", -9.0, ramp(), ramp()), schema = 2)) }
        assertTrue(store.fetch(listOf("a")).isEmpty())
        assertTrue(store.problem!!.contains("newer layout"))
    }

    @Test fun theTestSaysWhetherThePluginWorks() = runBlocking {
        reply = { JsonObject(mapOf("plugin" to JsonPrimitive("siansa_beats"), "plugin_version" to JsonPrimitive("0.1.0"), "status" to JsonPrimitive("ok"))) }
        val ok = store.check()
        assertTrue(ok.ok)
        assertEquals("Siansa Beats (dev) 0.1.0 is working", ok.message)
        assertEquals(ProfileStore.HEALTH_PATH, asked.last())

        // Siansa Beats (dev) says whether Siansa Beats answers it.
        val beats = { reachable: Boolean -> JsonObject(mapOf(
            "plugin" to JsonPrimitive("siansa_beats"), "plugin_version" to JsonPrimitive("0.1.0"), "status" to JsonPrimitive("ok"),
            "siansa_plugin" to JsonPrimitive(true),
            "beats" to JsonObject(if (reachable) mapOf("reachable" to JsonPrimitive(true), "version" to JsonPrimitive("0.8.1"))
                else mapOf("reachable" to JsonPrimitive(false), "problem" to JsonPrimitive("Nothing answers at beats:8090"))),
        )) }
        reply = { beats(true) }
        assertEquals("Siansa Beats (dev) 0.1.0 is working · Siansa Beats 0.8.1 answers", store.check().message)
        reply = { beats(false) }
        val down = store.check()
        assertFalse(down.ok)
        assertTrue(down.message, "Nothing answers at beats:8090" in down.message)

        // The Siansa plugin itself isn't what this build asks.
        reply = { JsonObject(mapOf("plugin" to JsonPrimitive("siansa_analysis"), "status" to JsonPrimitive("ok"))) }
        assertTrue(store.check().message.contains("not as Siansa Beats (dev)"))

        reply = { throw ApiException("AudioMuse sent something that isn't JSON. Is the address its API port?") }
        val login = store.check()
        assertFalse(login.ok)
        assertTrue(login.message.contains("login"))

        settings = settings.copy(audioMuseUrl = "")
        assertFalse(store.check().ok)
    }
}

private object PluginProfilesJson {
    fun parse(text: String): JsonElement = app.siansa.data.AppJson.parseToJsonElement(text)
}
