package app.musix.situation

import app.musix.data.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Open-Meteo (open-meteo.com): free weather without an account or key (issue #247). */
object OpenMeteo {
    /** Rounds to 0.1° (about 10 km), so the weather service never gets the exact place. */
    fun round(degrees: Double): Double = (degrees * 10).roundToInt() / 10.0

    fun url(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f".format(Locale.ROOT, round(latitude), round(longitude)) +
            "&current=temperature_2m,weather_code,is_day&daily=sunset&timezone=auto&forecast_days=1"

    /** The weather from an answer; [now] (the local time there) is used when the answer doesn't say. */
    fun parse(text: String, now: LocalDateTime): Weather? = runCatching {
        val root = AppJson.parseToJsonElement(text).jsonObject
        val current = root["current"]?.jsonObject ?: return null
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
        val celsius = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val isDay = current["is_day"]?.jsonPrimitive?.intOrNull != 0
        val there = current["time"]?.jsonPrimitive?.contentOrNull?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: now
        val sunset = (root["daily"] as? JsonObject)?.get("sunset")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        Weather(Sky.ofWmo(code), celsius, isDay, sunset?.let { ChronoUnit.MINUTES.between(there, it).toInt() })
    }.getOrNull()
}
