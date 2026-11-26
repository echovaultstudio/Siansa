package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.BeatsUse
import kotlinx.coroutines.launch
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
        "basis":"slot","based":"From what you play in the early morning on weekdays.","listens":7614,"songs":840,"keep_days":7,
        "daylight":{"light":"dark","elevation":-12.2,"sunrise":"08:22","sunset":"15:47","said":"Dark outside: the sun rises at 08:22"},
        "shelves":[
          {"key":"now","title":"Your early morning","note":"Weekdays, 05:00–09:00","mixes":[
            {"id":"now-1","key":"2026-11-17-early-weekdays-now-1","name":"Velvet Undertow","named":"ai","why":"Like A, B and C.","about":"Slow synths and hazy voices","tags":["Sung"],"genres":["Ambient"],"artists":["A"],"energy":0.12,"mood":0.3,"own":2,
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

    @Test fun underAMixIsWhatsInItNotItsArtists() {
        val got = BeatsMixes.parse(AppJson.parseToJsonElement(answer).jsonObject, 1L)
        assertEquals(7, got.keepDays)
        val first = got.shelves[0].mixes.single()
        assertEquals("Slow synths and hazy voices", first.caption)
        assertEquals("2026-11-17-early-weekdays-now-1", first.key)
        // Kept by Siansa Beats' key, so a morning mix isn't overwritten by the evening's; its page has the artists too.
        assertEquals("beats-2026-11-17-early-weekdays-now-1", app.siansa.ui.beatsMixId(first, "2026-11-17"))
        assertEquals("Slow synths and hazy voices. Like A, B and C.", app.siansa.ui.beatsMixDescription(first))
        // From an older Siansa Beats: no line, no key, no days.
        val old = got.shelves[1].mixes.single().copy(about = "", key = "", genres = listOf("Rock", "Indie", "Pop"))
        assertEquals("Rock, Indie", old.caption)
        assertEquals("beats-2026-11-17-repeat", app.siansa.ui.beatsMixId(old, "2026-11-17"))
        assertEquals("What you played most in the last 30 days.", old.copy(genres = emptyList()).caption)
        assertEquals(0, BeatsMixes.parse(AppJson.parseToJsonElement(answer.replace("\"keep_days\":7,", "")).jsonObject, 1L).keepDays)
    }

    @Test fun arrangeHomeSaysWhichRowsHoldThem() {
        assertEquals(emptyMap<app.siansa.data.HomeSection, String>(), app.siansa.ui.beatsRows(false))
        val rows = app.siansa.ui.beatsRows(true)
        assertEquals("From Siansa Beats", rows[app.siansa.data.HomeSection.CAROUSEL])
        assertEquals("From Siansa Beats", rows[app.siansa.data.HomeSection.MADE_FOR_YOU])
        // The rows have Siansa Beats' names (the owner, 20 Nov).
        assertEquals(listOf("For now", "Made for you", "More of you"),
            listOf(app.siansa.data.HomeSection.CAROUSEL, app.siansa.data.HomeSection.MADE_FOR_YOU, app.siansa.data.HomeSection.MORE_OF_YOU).map { it.label })
        assertEquals("From Siansa Beats: On repeat, Rediscover, New for you", rows[app.siansa.data.HomeSection.MORE_OF_YOU])
    }

    /** The owner, 26 Nov: "The mixes made by siansa could have a small tag or text in the arrange cards". */
    @Test fun arrangeHomeTagsTheRowsSiansaBeatsFills() {
        val rows = app.siansa.ui.beatsRows(true)
        assertEquals("Siansa Beats", app.siansa.ui.arrangeTag(app.siansa.data.HomeSection.CAROUSEL, rows))
        assertEquals("Siansa Beats", app.siansa.ui.arrangeTag(app.siansa.data.HomeSection.MORE_OF_YOU, rows))
        // Today's mix is the phone's own, made beside them (the owner, 26 Nov): no tag.
        assertEquals(null, app.siansa.ui.arrangeTag(app.siansa.data.HomeSection.TODAY, rows))
        assertEquals(null, app.siansa.ui.arrangeTag(app.siansa.data.HomeSection.BOOKS, rows))
        // Without Siansa Beats' mixes, no tags.
        assertEquals(null, app.siansa.ui.arrangeTag(app.siansa.data.HomeSection.TODAY, app.siansa.ui.beatsRows(false)))
        assertEquals(null, app.siansa.ui.arrangeTag(app.siansa.data.HomeSection.CAROUSEL, app.siansa.ui.beatsRows(false)))
    }

    @Test fun twoRowsAskingAtOnceAskOnce() = runBlocking {
        BeatsSound.restoreMixes(true)
        kotlinx.coroutines.coroutineScope {
            repeat(2) { launch { mixes.refresh() } }
        }
        assertEquals(1, asked.size)
    }

    @Test fun theLastMixesAreKeptOnThePhoneForTheNextStart() = runBlocking {
        // The owner, 17 Nov: not gone after a restart until they're loaded.
        var kept: String? = null
        var writes = 0
        val first = BeatsMixes({ path -> asked += path; AppJson.parseToJsonElement(answer) }, { settings },
            { ZoneId.of("Europe/Helsinki") }, { clock.toInstant().toEpochMilli() }, { kept }, { kept = it; writes++ })
        BeatsSound.restoreMixes(true)
        assertNull(first.state.value)
        first.refresh()
        assertEquals(1, writes)
        first.refresh(force = true)
        assertEquals("the same mixes: not written again", 1, writes)
        assertFalse(first.refreshing.value)
        // A new start: the kept ones show at once, and are asked for again (they're as old as they were).
        val next = BeatsMixes({ path -> asked += path; AppJson.parseToJsonElement(answer) }, { settings },
            { ZoneId.of("Europe/Helsinki") }, { clock.toInstant().toEpochMilli() + 20 * 60_000 }, { kept }, { kept = it })
        assertEquals(listOf("now", "more"), next.state.value!!.shelves.map { it.key })
        val before = asked.size
        next.refresh()
        assertEquals(before + 1, asked.size)
        // A broken file is no mixes, not a crash.
        assertNull(BeatsMixes({ AppJson.parseToJsonElement(answer) }, { settings }, loadKept = { "{broken" }).state.value)
    }

    @Test fun theWeatherGoesAlongForTheNames() = runBlocking {
        // Beats 0.61 (the owner, 21 Nov): names "taking weather, location, season etc into consideration".
        var weather: String? = "rain"
        val rainy = BeatsMixes({ path -> asked += path; AppJson.parseToJsonElement(answer) }, { settings },
            { ZoneId.of("Europe/Helsinki") }, { clock.toInstant().toEpochMilli() }, weather = { weather })
        BeatsSound.restoreMixes(true)
        rainy.refresh(force = true)
        assertTrue(asked.last(), asked.last().endsWith("&weather=rain"))
        weather = null
        rainy.refresh(force = true)
        assertFalse(asked.last(), "weather=" in asked.last())
        val w = app.siansa.situation.Weather(app.siansa.situation.Sky.DRIZZLE, 12.0, true, null)
        assertEquals("rain", w.forBeats)
        assertEquals("cold", w.copy(sky = app.siansa.situation.Sky.SNOW, celsius = -14.0).forBeats)
        assertEquals("hot", w.copy(sky = app.siansa.situation.Sky.CLEAR, celsius = 31.0).forBeats)
        assertEquals("snow", w.copy(sky = app.siansa.situation.Sky.SNOW, celsius = -2.0).forBeats)
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
