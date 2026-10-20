package app.musix.situation

import app.musix.net.Steer
import kotlin.math.roundToInt

/** Which parts of the moment the vibes may use (Vibes panel, issue #247). Each one on its own. */
data class SituationOptions(
    val weather: Boolean = false,
    val movement: Boolean = false,
    val light: Boolean = false,
    val charging: Boolean = false,
    val device: Boolean = true,
)

enum class Sky(val words: String) {
    CLEAR("clear sky"), CLOUDY("cloudy"), FOG("fog"), DRIZZLE("drizzle"), RAIN("rain"), SNOW("snow"), STORM("thunderstorm");

    companion object {
        /** Open-Meteo's WMO weather codes. */
        fun ofWmo(code: Int): Sky = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

data class Weather(val sky: Sky, val celsius: Double, val isDay: Boolean, val minutesToSunset: Int?)

enum class Movement(val words: String) { STILL("sitting still"), WALKING("walking"), RUNNING("running") }

enum class Light(val words: String) {
    DARK("in a dark room"), DIM("in dim light"), BRIGHT("in bright light");

    companion object {
        fun ofLux(lux: Float): Light = when {
            lux < 10f -> DARK
            lux < 400f -> DIM
            else -> BRIGHT
        }
    }
}

/** How fast steps came between two readings of the step counter, or null when the readings don't tell. */
fun movementOf(stepsBefore: Float, atBeforeMs: Long, stepsNow: Float, atNowMs: Long): Movement? {
    val minutes = (atNowMs - atBeforeMs) / 60_000.0
    // Too close together to mean anything, or too long ago to describe now.
    if (minutes < 1.0 || minutes > 30.0 || stepsNow < stepsBefore) return null
    val perMinute = (stepsNow - stepsBefore) / minutes
    return when {
        perMinute >= 130 -> Movement.RUNNING
        perMinute >= 40 -> Movement.WALKING
        else -> Movement.STILL
    }
}

/**
 * The moment a vibe's mix is made in (issue #247): only what's switched on and could be read. Nothing here is a
 * location: the weather was asked for with a rounded position that isn't kept.
 */
data class Situation(
    val weather: Weather? = null,
    val movement: Movement? = null,
    val light: Light? = null,
    val charging: Boolean? = null,
    val batteryPercent: Int? = null,
) {
    val isEmpty: Boolean get() = weather == null && movement == null && light == null && charging == null

    /** One line for the AI: "light rain, 9 °C, evening, sunset in 40 min; walking; in a dark room; charging". */
    fun describe(): String = listOfNotNull(
        weather?.let { w ->
            listOfNotNull(
                w.sky.words,
                "${w.celsius.roundToInt()} °C",
                if (w.isDay) "daylight" else "after dark",
                w.minutesToSunset?.takeIf { it in 0..120 }?.let { "sunset in $it min" },
            ).joinToString(", ")
        },
        movement?.words,
        light?.words,
        charging?.let { if (it) "phone charging" else batteryPercent?.takeIf { b -> b <= 20 }?.let { b -> "battery low ($b%)" } },
    ).joinToString("; ")

    /** A few words for the mix's description when there's no AI: "Rain · walking". */
    fun label(): String = listOfNotNull(
        weather?.sky?.words?.replaceFirstChar { it.uppercase() },
        movement?.takeIf { it != Movement.STILL }?.words,
    ).joinToString(" · ")

    /**
     * Concepts to nudge AudioMuse's search with when there's no AI (only ones AudioMuse offers, at most 3):
     * rain and dark rooms lean calm and acoustic, sun and heat upbeat, running energetic.
     */
    fun nudges(concepts: List<String>): List<Steer> {
        val known = concepts.associateBy { it.lowercase() }
        val wanted = buildList {
            when (movement) {
                Movement.RUNNING -> { add("energetic" to true); add("fast" to true) }
                Movement.WALKING -> add("upbeat" to true)
                else -> Unit
            }
            when (weather?.sky) {
                Sky.RAIN, Sky.DRIZZLE, Sky.FOG -> { add("acoustic" to true); add("calm" to true) }
                Sky.STORM -> add("dark" to true)
                Sky.SNOW -> add("calm" to true)
                Sky.CLEAR -> if (weather?.isDay == true && weather.celsius >= 18) add("happy" to true)
                else -> Unit
            }
            if (light == Light.DARK) add("calm" to true)
        }
        return wanted.mapNotNull { (term, more) -> known[term]?.let { Steer(it, more) } }.distinctBy { it.term }.take(3)
    }
}
