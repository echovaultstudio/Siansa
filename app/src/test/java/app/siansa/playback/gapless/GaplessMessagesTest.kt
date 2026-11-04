package app.siansa.playback.gapless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the phone and the TV screen say to each other while casting gaplessly (issue #114; cast-receiver/index.html). */
class GaplessMessagesTest {
    @Test fun nowPlayingForTheTv() {
        val text = GaplessMessages.now(GaplessMessages.Now("Teardrop", "Massive Attack", "Mezzanine", "http://nd/cover?id=1", 61_000, 330_000, true))
        val o = Json.parseToJsonElement(text).jsonObject
        assertEquals("now", o["type"]!!.jsonPrimitive.content)
        assertEquals("Teardrop", o["title"]!!.jsonPrimitive.content)
        assertEquals("http://nd/cover?id=1", o["cover"]!!.jsonPrimitive.content)
        assertEquals(61_000L, o["positionMs"]!!.jsonPrimitive.long)
        assertEquals(330_000L, o["durationMs"]!!.jsonPrimitive.long)
        assertEquals(true, o["playing"]!!.jsonPrimitive.boolean)
        val none = Json.parseToJsonElement(GaplessMessages.now(GaplessMessages.Now("", "", "", null, -5, -1, false))).jsonObject
        assertTrue("cover" !in none)
        assertEquals(0L, none["positionMs"]!!.jsonPrimitive.long)
    }

    @Test fun theTvScreenIsToldToStartAndStopThePhonesStream() {
        val o = Json.parseToJsonElement(GaplessMessages.start(4, "http://192.168.1.56:4000/live/k/4.wav?from=9", StreamWay(StreamFormat.Wav, throughCast = false))).jsonObject
        assertEquals("start", o["type"]!!.jsonPrimitive.content)
        assertEquals(4L, o["stream"]!!.jsonPrimitive.long)
        assertEquals("http://192.168.1.56:4000/live/k/4.wav?from=9", o["url"]!!.jsonPrimitive.content)
        assertEquals("wav", o["format"]!!.jsonPrimitive.content)
        assertEquals("audio/wav", o["mime"]!!.jsonPrimitive.content)
        assertEquals("element", o["player"]!!.jsonPrimitive.content)
        val cast = Json.parseToJsonElement(GaplessMessages.start(5, "http://x/5.flac", StreamWay(StreamFormat.Flac, throughCast = true))).jsonObject
        assertEquals("cast", cast["player"]!!.jsonPrimitive.content)
        assertEquals("audio/flac", cast["mime"]!!.jsonPrimitive.content)
        assertEquals("stop", Json.parseToJsonElement(GaplessMessages.stop()).jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun whatTheTvSays() {
        assertEquals(GaplessMessages.FromTv.Clock(3, 12.5, true), GaplessMessages.parse("""{"type":"clock","stream":3,"time":12.5,"moving":true}"""))
        assertEquals(GaplessMessages.FromTv.Clock(3, 0.0, false), GaplessMessages.parse("""{"type":"clock","stream":3,"time":0}"""))
        assertEquals(GaplessMessages.FromTv.Command("next", null), GaplessMessages.parse("""{"type":"command","action":"next"}"""))
        assertEquals(GaplessMessages.FromTv.Command("seek", 71_000), GaplessMessages.parse("""{"type":"command","action":"seek","toMs":71000}"""))
        assertEquals(GaplessMessages.FromTv.Error(3, "905", "net"), GaplessMessages.parse("""{"type":"error","stream":3,"code":"905","message":"net"}"""))
    }

    @Test fun anythingElseIsLeftAlone() {
        listOf(
            "not json",
            """{"type":"command","action":"format the disk"}""",
            """{"type":"clock","stream":3,"time":-1}""",
            """{"type":"clock","time":2}""",
            """{"type":"hello"}""",
            "[1,2]",
        ).forEach { assertTrue(it, GaplessMessages.parse(it) is GaplessMessages.FromTv.Other) }
    }
}
