package app.musix.situation

import app.musix.net.Steer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The moment a vibe's mix is made in (issue #247). */
class SituationTest {
    @Test fun weatherCodes() {
        assertEquals(Sky.CLEAR, Sky.ofWmo(0))
        assertEquals(Sky.CLOUDY, Sky.ofWmo(3))
        assertEquals(Sky.FOG, Sky.ofWmo(45))
        assertEquals(Sky.DRIZZLE, Sky.ofWmo(53))
        assertEquals(Sky.RAIN, Sky.ofWmo(63))
        assertEquals(Sky.RAIN, Sky.ofWmo(81))
        assertEquals(Sky.SNOW, Sky.ofWmo(75))
        assertEquals(Sky.STORM, Sky.ofWmo(95))
    }

    @Test fun lightLevels() {
        assertEquals(Light.DARK, Light.ofLux(2f))
        assertEquals(Light.DIM, Light.ofLux(150f))
        assertEquals(Light.BRIGHT, Light.ofLux(20_000f))
    }

    @Test fun movementFromTwoStepCounts() {
        val t = 1_000_000L
        assertEquals(Movement.WALKING, movementOf(1_000f, t, 1_500f, t + 5 * 60_000))   // 100 a minute
        assertEquals(Movement.RUNNING, movementOf(1_000f, t, 1_800f, t + 5 * 60_000))   // 160 a minute
        assertEquals(Movement.STILL, movementOf(1_000f, t, 1_020f, t + 5 * 60_000))
        assertNull(movementOf(1_000f, t, 1_500f, t + 30_000))           // too close together
        assertNull(movementOf(1_000f, t, 1_500f, t + 2 * 60 * 60_000))  // too long ago
        assertNull(movementOf(5_000f, t, 10f, t + 5 * 60_000))          // counter restarted (phone rebooted)
    }

    private val rainyWalk = Situation(
        weather = Weather(Sky.RAIN, 8.6, isDay = false, minutesToSunset = 40),
        movement = Movement.WALKING,
        light = Light.DARK,
        charging = false,
        batteryPercent = 15,
    )

    @Test fun oneLineForTheAiWithoutAPlace() {
        assertEquals("rain, 9 °C, after dark, sunset in 40 min; walking; in a dark room; battery low (15%)", rainyWalk.describe())
        assertEquals("phone charging", Situation(charging = true, batteryPercent = 80).describe())
        assertEquals("", Situation(charging = false, batteryPercent = 80).describe())
        assertTrue(Situation().isEmpty)
        assertFalse(rainyWalk.isEmpty)
    }

    @Test fun labelForTheDescription() {
        assertEquals("Rain · walking", rainyWalk.label())
        assertEquals("Clear sky", Situation(weather = Weather(Sky.CLEAR, 25.0, true, null), movement = Movement.STILL).label())
    }

    @Test fun nudgesOnlyUseConceptsAudioMuseOffers() {
        val concepts = listOf("Acoustic", "calm", "upbeat", "energetic", "happy")
        assertEquals(listOf(Steer("upbeat", true), Steer("Acoustic", true), Steer("calm", true)), rainyWalk.nudges(concepts))
        val sunnyRun = Situation(weather = Weather(Sky.CLEAR, 24.0, true, null), movement = Movement.RUNNING)
        assertEquals(listOf(Steer("energetic", true), Steer("happy", true)), sunnyRun.nudges(concepts))
        assertTrue(rainyWalk.nudges(emptyList()).isEmpty())
    }

    @Test fun openMeteoGetsARoundedPlaceOnly() {
        val url = OpenMeteo.url(52.520008, 13.404954)
        assertTrue(url, url.contains("latitude=52.5&longitude=13.4"))
        assertFalse(url.contains("52.52"))
        assertEquals(-33.9, OpenMeteo.round(-33.8688), 0.0)
    }

    @Test fun readsOpenMeteosAnswer() {
        val answer = """{"latitude":52.5,"longitude":13.4,"timezone":"Europe/Berlin",
            "current":{"time":"2026-10-20T17:30","interval":900,"temperature_2m":9.4,"weather_code":61,"is_day":1},
            "daily":{"time":["2026-10-20"],"sunset":["2026-10-20T18:10"]}}"""
        val w = OpenMeteo.parse(answer, LocalDateTime.of(2026, 10, 20, 12, 0))!!
        assertEquals(Weather(Sky.RAIN, 9.4, isDay = true, minutesToSunset = 40), w)
        assertNull(OpenMeteo.parse("""{"error":true,"reason":"Latitude must be in range"}""", LocalDateTime.now()))
        assertNull(OpenMeteo.parse("not json", LocalDateTime.now()))
    }
}
