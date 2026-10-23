package app.siansa

import app.siansa.data.AppJson
import app.siansa.net.structuredLyrics
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navidrome's word and syllable timings (OpenSubsonic songLyrics v2, issue #155). */
class StructuredLyricsTest {
    private fun set(json: String) = AppJson.parseToJsonElement(json).jsonObject

    // "Hello wonderful world" with the syllables of "wonderful" timed one by one; byteEnd is inclusive.
    private val enhanced = """
        {"kind":"main","lang":"eng","synced":true,"offset":0,
         "line":[{"start":1000,"value":"Hello wonderful world"},{"start":5000,"value":"Again"}],
         "agents":[{"id":"lead","role":"main","name":"A"},{"id":"bg","role":"bg","name":"B"}],
         "cueLine":[
           {"index":0,"agentId":"bg","start":1000,"end":4000,"value":"Hello wonderful world","cue":[{"start":1000,"end":4000,"value":"Hello","byteStart":0,"byteEnd":4}]},
           {"index":0,"agentId":"lead","start":1000,"end":4000,"value":"Hello wonderful world","cue":[
             {"start":1000,"end":1400,"value":"Hello","byteStart":0,"byteEnd":4},
             {"start":1500,"end":1700,"value":"won","byteStart":6,"byteEnd":8},
             {"start":1700,"end":1900,"value":"der","byteStart":9,"byteEnd":11},
             {"start":1900,"end":2200,"value":"ful","byteStart":12,"byteEnd":14},
             {"start":2300,"end":2800,"value":"world","byteStart":16,"byteEnd":20}]}]}
    """.trimIndent()

    @Test fun syllablesBecomeTheLinesWords() {
        val (lang, lyrics) = structuredLyrics(set(enhanced))!!
        assertEquals("eng", lang)
        assertTrue(lyrics.wordTimed)
        val first = lyrics.lines[0]
        assertEquals(listOf(1000L, 1500L, 1700L, 1900L, 2300L), first.words.map { it.startMs })
        // The spaces stay in, so the pieces together are the whole line.
        assertEquals(listOf("Hello", " won", "der", "ful", " world"), first.words.map { it.text })
        assertEquals(first.text, first.words.joinToString("") { it.text })
        // A line without cues stays timed by line.
        assertEquals(5000L, lyrics.lines[1].startMs)
        assertTrue(lyrics.lines[1].words.isEmpty())
    }

    @Test fun theLeadSingerWinsOverBackgroundVocals() {
        val (_, lyrics) = structuredLyrics(set(enhanced))!!
        assertEquals(5, lyrics.lines[0].words.size)
    }

    @Test fun aPositiveOffsetMakesLyricsComeSooner() {
        val json = enhanced.replace("\"offset\":0", "\"offset\":500")
        val (_, lyrics) = structuredLyrics(set(json))!!
        assertEquals(500L, lyrics.lines[0].startMs)
        assertEquals(1000L, lyrics.lines[0].words[1].startMs)
    }

    @Test fun withoutByteOffsetsTheCuesOwnTextIsUsed() {
        val json = """{"synced":true,"line":[{"start":0,"value":"La la"}],
            "cueLine":[{"index":0,"value":"La la","cue":[{"start":0,"value":"La "},{"start":400,"value":"la"}]}]}"""
        val (_, lyrics) = structuredLyrics(set(json))!!
        assertEquals(listOf("La ", "la"), lyrics.lines[0].words.map { it.text })
    }

    @Test fun accentedTextKeepsWholeCharacters() {
        // "Café olé": é is two bytes in UTF-8.
        val json = """{"synced":true,"line":[{"start":0,"value":"Café olé"}],
            "cueLine":[{"index":0,"value":"Café olé","cue":[{"start":0,"value":"Café","byteStart":0,"byteEnd":4},{"start":600,"value":"olé","byteStart":6,"byteEnd":9}]}]}"""
        val (_, lyrics) = structuredLyrics(set(json))!!
        assertEquals(listOf("Café", " olé"), lyrics.lines[0].words.map { it.text })
    }

    @Test fun translationsAreLeftOut() {
        assertNull(structuredLyrics(set("""{"kind":"translation","synced":true,"line":[{"start":0,"value":"Hola"}]}""")))
    }

    @Test fun oldServersGivePlainLines() {
        val (_, lyrics) = structuredLyrics(set("""{"lang":"eng","synced":true,"line":[{"start":1200,"value":"One"},{"start":3000,"value":"Two"}]}"""))!!
        assertFalse(lyrics.wordTimed)
        assertEquals(listOf(1200L, 3000L), lyrics.lines.map { it.startMs })
    }
}
