package app.siansa.situation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import app.siansa.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import kotlin.coroutines.resume

/**
 * Reads the moment once, when a vibe's mix is made (issue #247). Nothing runs in between: no GPS, no sensor
 * left listening, no weather checks in the background, so the battery cost is one sensor reading and at most
 * one small request per mix.
 *
 * - Weather: the position Android already has (Wi-Fi or mobile network, not GPS), rounded to about 10 km and
 *   sent only to Open-Meteo. Kept for 30 minutes.
 * - Movement: the step counter (counted by a low-power chip anyway), compared with the reading taken when the
 *   app was last opened ([noteSteps]).
 * - Light: one reading of the light sensor. Charging: from the battery status Android keeps.
 */
class SituationReader(private val context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("situation", Context.MODE_PRIVATE)

    @Volatile private var weatherCache: Pair<Long, Weather>? = null

    suspend fun read(options: SituationOptions): Situation {
        if (!(options.weather || options.movement || options.light || options.charging)) return Situation()
        val situation = Situation(
            weather = if (options.weather) safely("weather") { weather() } else null,
            movement = if (options.movement) safely("movement") { movement() } else null,
            light = if (options.light) safely("light") { sensorValue(Sensor.TYPE_LIGHT)?.let { Light.ofLux(it) } } else null,
            charging = if (options.charging) battery().first else null,
            batteryPercent = if (options.charging) battery().second else null,
        )
        AppLog.i("Situation", "Mix made in: ${situation.describe().ifEmpty { "nothing could be read" }}", event = "mix.situation", fields = mapOf(
            "sky" to situation.weather?.sky, "celsius" to situation.weather?.celsius, "movement" to situation.movement,
            "light" to situation.light, "charging" to situation.charging,
        ))
        return situation
    }

    private suspend fun <T> safely(what: String, block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("Situation", "Couldn't read the $what: ${e.message}")
        null
    }

    // ---- Weather ----

    private suspend fun weather(): Weather? {
        weatherCache?.let { (at, w) -> if (System.currentTimeMillis() - at < WEATHER_FRESH_MS) return w }
        val place = location() ?: return null.also { AppLog.i("Situation", "No position known yet: no weather this time") }
        val answer = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(OpenMeteo.url(place.latitude, place.longitude)).build()).execute().use { r ->
                if (!r.isSuccessful) error("Open-Meteo answered HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }
        val weather = OpenMeteo.parse(answer, LocalDateTime.now()) ?: error("Open-Meteo sent an answer Siansa can't read")
        weatherCache = System.currentTimeMillis() to weather
        return weather
    }

    /** The newest position Android already knows (no GPS); or, on Android 11+, one quick network fix. */
    @SuppressLint("MissingPermission") // granted() checks it first
    private suspend fun location(): Location? {
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val known = providers.mapNotNull { p ->
            try {
                if (manager.isProviderEnabled(p) || p == LocationManager.PASSIVE_PROVIDER) manager.getLastKnownLocation(p) else null
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < LOCATION_FRESH_MS) return known
        if (Build.VERSION.SDK_INT < 30 || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return known
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                try {
                    manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, cancel, context.mainExecutor) { cont.resume(it) }
                } catch (e: SecurityException) {
                    cont.resume(null)
                }
            }
        } ?: known
    }

    // ---- Movement ----

    /** Takes a step count to compare with later (the app coming to the front). Cheap: one reading. */
    suspend fun noteSteps() {
        if (!stepsAllowed()) return
        val steps = sensorValue(Sensor.TYPE_STEP_COUNTER) ?: return
        prefs.edit().putFloat(KEY_STEPS, steps).putLong(KEY_STEPS_AT, System.currentTimeMillis()).apply()
    }

    private suspend fun movement(): Movement? {
        if (!stepsAllowed()) return null
        val now = sensorValue(Sensor.TYPE_STEP_COUNTER) ?: return null
        val before = prefs.getFloat(KEY_STEPS, -1f)
        val at = prefs.getLong(KEY_STEPS_AT, 0L)
        val time = System.currentTimeMillis()
        prefs.edit().putFloat(KEY_STEPS, now).putLong(KEY_STEPS_AT, time).apply()
        return if (before < 0) null else movementOf(before, at, now, time)
    }

    // ---- Sensors and battery ----

    /** One reading of a sensor, then it's let go at once; null when the phone has none or it's slow. */
    private suspend fun sensorValue(type: Int): Float? {
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = manager.getDefaultSensor(type) ?: return null
        return withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine<Float?> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values.firstOrNull())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun battery(): Pair<Boolean?, Int?> {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return null to null
        return manager.isCharging to manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }

    /** The step counter needs "physical activity" from Android 10 on; before that, nothing. */
    private fun stepsAllowed() = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val WEATHER_FRESH_MS = 30 * 60_000L
        const val LOCATION_FRESH_MS = 6 * 60 * 60_000L
        const val KEY_STEPS = "steps"
        const val KEY_STEPS_AT = "steps_at"
    }
}
