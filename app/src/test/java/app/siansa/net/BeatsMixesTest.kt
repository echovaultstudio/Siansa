package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.BeatsUse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Home's mixes from Siansa Beats (0.53, the owner, 17 Nov): its answer read, its shelves titled as on its page, asked
 * only while switched on and Siansa Beats makes them, and again when the part of the day changes.
 */
class BeatsMixesTest {
    private val answer = """{"schema_version":1,"user":"max","part":"early","days":"weekdays","said":"early morning","hours":"05:00–09:00",
        "basis":"slot","based":"From what you play in the early morning on weekdays.","listens":7614,"songs":840,
        "daylight":{"light":"dark","elevation":-12.2,"sunrise":"08:22","sunset":"15:47","said":"Dark outside: the sun rises at 08:22"},
        "shelves":[
          {"key":"now","title":"Your early morning","note":"Weekdays, 05:00–09:00","mixes":[
            {"id":"now-1","name":"Velvet Undertow","named":"ai","why":"Like A, B and C.","tags":["Sung"],"genres":["Ambient"],"artists":["A"],"energy":0.12,"mood":0.3,"own":2,
             "songs":[{"id":"s1","title":"One","artist":"A","album":"","own":true},{"id":"s2","title":"Two","artist":"B","album":"","own":false}]},
            {"id":"now-2","name":"Empty","named":"sound","why":"","tags":[],"genres":[],"artists":[],"energy":null,"mood":null,"own":0,"songs":[]}]},
          {"key":"daily","title":"Made for you","note":"","mixes":[]},
          {"key":"more","title":"More of you","note":"","mixes":[
            {"id":"repeat","name":"On repeat","named":"sound","why":"What you played most in the last 30 days.","tags":[],"genres":[],"artists":[],"energy":null,"mood":null,"own":1,
             "songs":[{"id":"s3","title":"Three","artist":"C","album":"","own":true}]}]}]}"""

    private val asked = mutableListOf<String>()
    private var clock = ZonedDateTime.of(2026, 11, 17, 6, 40, 0, 0, ZoneId.of("Europe/Helsinki"))
    private var settings = AppSettings(beatsUrl = "http://beats.local:8090", navidromeUser = "max")
    private val mixes = BeatsMixes(
        { path -> asked += path; AppJson.parseToJsonElement(answer) },
        { settings }, { ZoneId.of("Europe/Helsinki") }, { clock.toInstant().toEpochMilli() },
    )

    @After fun forget() = BeatsSound.restoreMixes(false)

    @Test fun theAnswerIsRead() {
        val got = BeatsMixes.parse(AppJson.parseToJsonElement(answer).jsonObject, 1L)
        assertEquals(listOf("now", "more"), got.shelves.map { it.key })  // an empty shelf, and a mix with no songs, aren't shown
        assertEquals(listOf("For now", "More of you"), got.shelves.map { BeatsMixes.title(it) })
        val first = got.shelves[0].mixes.single()
        assertEquals("Velvet Undertow", first.name)
        assertTrue(first.aiNamed)
        assertEquals(listOf("s1", "s2"), first.songIds)
        assertEquals(0.12, first.energy!!, 1e-9)
        assertEquals(2, first.own)
        assertFalse(got.shelves[1].mixes.single().aiNamed)
        assertNull(got.shelves[1].mixes.single().energy)
        assertEquals("Dark outside: the sun rises at 08:22", got.daylight?.said)
        assertEquals("early morning", got.said)
    }

    @Test fun thePartsOfTheDayAreSiansaBeats() {
        assertEquals(listOf("night", "early", "early", "morning", "afternoon", "evening", "evening", "night"),
            listOf(4, 5, 8, 9, 12, 17, 21, 22).map { BeatsMixes.partOf(it) })
    }

    @Test fun askedOnlyWhileOnAndAgainAtTheNextPartOfTheDay() = runBlocking {
        assertNull("Siansa Beats doesn't make them", mixes.refresh())
        BeatsSound.restoreMixes(true)
        settings = settings.copy(beatsOff = setOf(BeatsUse.MIXES))
        assertNull("switched off", mixes.refresh())
        assertTrue(asked.isEmpty())
        settings = settings.copy(beatsOff = emptySet())
        val first = mixes.refresh()!!
        assertEquals(listOf("/api/v1/mixes?user=max&tz=Europe%2FHelsinki&size=40"), asked)
        assertSame("the same part of the day: kept", first, mixes.refresh())
        clock = clock.withHour(9).withMinute(1)
        mixes.refresh()
        assertEquals("the morning: asked again", 2, asked.size)
        mixes.refresh(force = true)
        assertEquals(3, asked.size)
    }

    @Test fun whatWasShownStaysWhenSiansaBeatsIsAway() = runBlocking {
        BeatsSound.restoreMixes(true)
        val first = mixes.refresh()!!
        val failing = BeatsMixes({ throw ApiException("down") }, { settings }, { ZoneId.of("Europe/Helsinki") }, { clock.toInstant().toEpochMilli() })
        assertNull(failing.refresh())
        assertEquals(first.shelves.size, mixes.refresh(force = true)!!.shelves.size)
    }
}
