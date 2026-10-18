package app.musix.playback

import app.musix.data.AppSettings
import app.musix.data.ReplayGainMode
import app.musix.data.Song
import app.musix.net.ApiException
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

/** Builds a ramp blob as the Lumae plugin sends it: (int8 dB, uint16 little-endian chunk) per level. */
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

/** The Lumae plugin's ramps and answers (issue #60). */
class LumaeProfilesTest {
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
        val profiles = LumaeProfiles.parse(text)
        assertEquals(listOf("s1", "s2"), profiles.map { it.trackId })
        assertEquals(-8.5, profiles[0].refLufs!!, 0.0)
        assertEquals(300L, profiles[0].reachesAfterMs(-18))
        assertEquals(4_000L, profiles[0].quietForLastMs(-18))
        assertEquals(200_000L, profiles[0].durationMs)
        assertNull(profiles[1].refLufs)
    }

    @Test fun aNewerLayoutIsNotMisread() {
        assertTrue(LumaeProfiles.parse(answer(profileJson("s1", -8.0, ramp(), ramp()), schema = 2)).isEmpty())
        assertTrue(LumaeProfiles.parse("<html>login</html>").isEmpty())
    }

    @Test fun batchesKeepRequestsShortAndUnderTheIdLimit() {
        val base = "https://audiomuse.example.com" + LumaeProfiles.PATH
        val ids = (1..2_000).map { "song-id-%05d".format(it) }
        val batches = LumaeProfiles.batches(ids, base)
        assertEquals(ids, batches.flatten())
        batches.forEach { batch ->
            assertTrue((base + LumaeProfiles.query(batch)).length <= LumaeProfiles.URL_BUDGET_BYTES)
            assertTrue(batch.size <= LumaeProfiles.MAX_IDS)
        }
        // Full batches use the budget: no batch is followed by one that would have fitted into it.
        val first = base + LumaeProfiles.query(batches[0] + batches[1].first())
        assertTrue(first.length > LumaeProfiles.URL_BUDGET_BYTES)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), LumaeProfiles.batches(listOf("a", "b", "a", "c"), base, maxIds = 2))
    }

    @Test fun queryEncodesIds() {
        assertEquals("?ids=a%2Fb,c+d", LumaeProfiles.query(listOf("a/b", "c d")))
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
        val plan = Crossfade.plan(CrossfadeMode.FIXED, 6_000, a1, a2, minutes4, minutes4)!!
        assertEquals(6_000L, plan.overlapMs)
        assertEquals(6_000L, plan.fadeInMs)
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
        assertEquals(2_000L, plan.fadeInMs)
    }

    @Test fun smartFadeIsNeverLongerThanTheSetting() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 4_000, a1, b1, minutes4, minutes4, profile("a1", 0, 9_000), profile("b1", 3_000, 0))!!
        assertEquals(4_000L, plan.overlapMs)
        assertTrue(plan.fadeInMs <= plan.overlapMs)
    }

    @Test fun aHardEndingIntoAHardHitPlaysBackToBack() {
        assertNull(Crossfade.plan(CrossfadeMode.SMART, 8_000, a1, b1, minutes4, minutes4, profile("a1", 0, 100), profile("b1", 100, 0)))
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
}

/** Levelling from measured loudness, with ReplayGain tags as the fallback (issue #60). */
class LevellingTest {
    private val tagged = Song("s", "Song", trackGain = -6f, albumGain = -9f)
    private val lumae = AppSettings(replayGain = ReplayGainMode.TRACK, loudnessSource = LoudnessSource.LUMAE, loudnessTarget = -18)
    private fun measured(lufs: Double, id: String = "s") = TrackProfile(id, lufs, emptyList(), emptyList())

    @Test fun bringsLoudSongsDownToTheTarget() {
        assertEquals(10.0.pow(-8 / 20.0).toFloat(), Levelling.volume(measured(-10.0), -18)!!, 0.0001f)
        assertEquals(10.0.pow(-4 / 20.0).toFloat(), Levelling.volume(measured(-10.0), -14)!!, 0.0001f)
    }

    @Test fun quietSongsStayAtFullVolume() {
        assertEquals(1f, Levelling.volume(measured(-25.0), -18)!!, 0f)
        assertNull(Levelling.volume(TrackProfile("s", null, emptyList(), emptyList()), -18))
    }

    @Test fun measuredLoudnessWinsOverTagsWithTheLumaeSource() {
        assertEquals(10.0.pow(-8 / 20.0).toFloat(), Levelling.songVolume(tagged, lumae, measured(-10.0)), 0.0001f)
    }

    @Test fun tagsAreUsedOtherwise() {
        val tags = replayGainVolume(tagged, lumae)
        // Song tags chosen as the source, no measurement, or a measurement of another song.
        assertEquals(tags, Levelling.songVolume(tagged, lumae.copy(loudnessSource = LoudnessSource.TAGS), measured(-10.0)), 0f)
        assertEquals(tags, Levelling.songVolume(tagged, lumae, null), 0f)
        assertEquals(tags, Levelling.songVolume(tagged, lumae, measured(-10.0, id = "other")), 0f)
    }

    @Test fun albumModeKeepsAlbumTags() {
        val album = lumae.copy(replayGain = ReplayGainMode.ALBUM)
        assertEquals(replayGainVolume(tagged, album), Levelling.songVolume(tagged, album, measured(-10.0)), 0f)
        // Without album tags, the song's measurement.
        assertEquals(10.0.pow(-8 / 20.0).toFloat(), Levelling.songVolume(Song("s", "x"), album, measured(-10.0)), 0.0001f)
    }

    @Test fun replayGainOffMeansNoLevelling() {
        assertEquals(1f, Levelling.songVolume(tagged, lumae.copy(replayGain = ReplayGainMode.OFF), measured(-5.0)), 0f)
    }
}

/** Fetching and keeping profiles (issue #60). */
class ProfileStoreTest {
    private var settings = AppSettings(audioMuseUrl = "http://am:8000", loudnessSource = LoudnessSource.LUMAE)
    private var time = 1_000_000L
    private val asked = mutableListOf<String>()
    private var reply: (String) -> JsonElement = { path ->
        val ids = path.substringAfter("ids=").split(",")
        LumaeProfilesJson.parse(answer(*ids.filter { it != "unknown" }.map { profileJson(it, -9.0, ramp(-18 to 5), ramp(-18 to 20)) }.toTypedArray()))
    }
    private val store = ProfileStore({ path -> asked += path; reply(path) }, { settings }, { time })

    @Test fun fetchesOnceAndKeepsThem() = runBlocking {
        val found = store.fetch(listOf("a", "b"))
        assertEquals(setOf("a", "b"), found.keys)
        assertEquals(1, asked.size)
        assertTrue(asked[0].startsWith(LumaeProfiles.PATH + "?ids="))
        store.fetch(listOf("a", "b"))
        assertEquals(1, asked.size)
        assertNotNull(store.cached("a"))
        // Only the new one is asked for.
        store.fetch(listOf("a", "c"))
        assertEquals(LumaeProfiles.PATH + "?ids=c", asked.last())
    }

    @Test fun songsNotAnalysedYetAreAskedForAgainLater() = runBlocking {
        store.fetch(listOf("unknown"))
        store.fetch(listOf("unknown"))
        assertEquals(1, asked.size)
        time += ProfileStore.RETRY_MISSING_MS + 1
        store.fetch(listOf("unknown"))
        assertEquals(2, asked.size)
    }

    @Test fun aFailingPluginIsLeftAloneForAWhile() = runBlocking {
        reply = { throw ApiException("AudioMuse answered HTTP 404: not found") }
        assertTrue(store.fetch(listOf("a")).isEmpty())
        assertEquals("The Lumae plugin isn't installed on this AudioMuse server", store.problem)
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
        reply = { LumaeProfilesJson.parse(answer(profileJson("a", -9.0, ramp(), ramp()), schema = 2)) }
        assertTrue(store.fetch(listOf("a")).isEmpty())
        assertTrue(store.problem!!.contains("newer layout"))
    }

    @Test fun theTestSaysWhetherThePluginWorks() = runBlocking {
        reply = { JsonObject(mapOf("plugin" to JsonPrimitive("lumae_analysis"), "plugin_version" to JsonPrimitive("1.4.0"), "status" to JsonPrimitive("ok"))) }
        val ok = store.check()
        assertTrue(ok.ok)
        assertEquals("Lumae plugin 1.4.0 is working", ok.message)
        assertEquals(ProfileStore.HEALTH_PATH, asked.last())

        reply = { throw ApiException("AudioMuse sent something that isn't JSON. Is the address its API port?") }
        val login = store.check()
        assertFalse(login.ok)
        assertTrue(login.message.contains("login"))

        settings = settings.copy(audioMuseUrl = "")
        assertFalse(store.check().ok)
    }
}

private object LumaeProfilesJson {
    fun parse(text: String): JsonElement = app.musix.data.AppJson.parseToJsonElement(text)
}
