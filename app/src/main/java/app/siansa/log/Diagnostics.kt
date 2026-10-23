package app.siansa.log

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Stutter detectors for detailed logging. They cost next to nothing while detailed logging is off.
 * - Main thread watchdog: if the app's main thread can't respond for 400 ms (the screen freezes and
 *   taps don't register), it records what code was running at that moment.
 * - Frame monitor: counts frames that took too long to draw (visible as jerky scrolling), per screen.
 * - Phone state: memory, overheating and battery saver, every minute and on change.
 */
object Diagnostics {
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        startWatchdog()
        startFrameMonitor()
        startPhoneState(context.applicationContext)
    }

    private fun startWatchdog() = thread(name = "siansa-watchdog", isDaemon = true) {
        while (true) {
            if (!AppLog.detailOn) {
                Thread.sleep(2000)
                continue
            }
            val answered = AtomicBoolean(false)
            val start = SystemClock.uptimeMillis()
            main.post { answered.set(true) }
            Thread.sleep(400)
            if (!answered.get()) {
                val stack = Looper.getMainLooper().thread.stackTrace
                    .take(30)
                    .joinToString("\n    at ")
                AppLog.w("Freeze", "Main thread blocked over 400 ms on screen '${AppLog.screen}'. Running:\n    at $stack")
                while (!answered.get() && SystemClock.uptimeMillis() - start < 15_000) Thread.sleep(25)
                AppLog.w("Freeze", "Main thread free again after ${SystemClock.uptimeMillis() - start} ms")
            }
            Thread.sleep(600)
        }
    }

    private fun startFrameMonitor() = main.post {
        val choreographer = Choreographer.getInstance()
        var last = 0L
        var windowStart = SystemClock.uptimeMillis()
        var frames = 0
        var slow = 0
        var worst = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (AppLog.detailOn) {
                    if (last != 0L) {
                        val ms = (frameTimeNanos - last) / 1_000_000
                        frames++
                        // Over 2 frames' time at 60 Hz: a visible hitch. Long gaps (app in background) are ignored.
                        if (ms in 34..5000) {
                            slow++
                            if (ms > worst) worst = ms
                        }
                    }
                    val now = SystemClock.uptimeMillis()
                    if (now - windowStart >= 5000) {
                        if (slow > 0) {
                            AppLog.d("Frames", "$slow of $frames frames slow in ${(now - windowStart) / 1000}s (worst $worst ms) on '${AppLog.screen}'")
                        }
                        windowStart = now
                        frames = 0
                        slow = 0
                        worst = 0
                    }
                    last = frameTimeNanos
                } else {
                    last = 0L
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
    }

    private fun startPhoneState(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= 29 && power != null) {
            power.addThermalStatusListener { status ->
                if (AppLog.enabled) {
                    val text = when (status) {
                        PowerManager.THERMAL_STATUS_NONE -> "normal"
                        PowerManager.THERMAL_STATUS_LIGHT -> "light"
                        PowerManager.THERMAL_STATUS_MODERATE -> "moderate (may slow down)"
                        PowerManager.THERMAL_STATUS_SEVERE -> "severe (slowing down)"
                        else -> "critical ($status)"
                    }
                    AppLog.w("Phone", "Temperature status: $text")
                }
            }
        }
        thread(name = "siansa-phone-state", isDaemon = true) {
            while (true) {
                if (AppLog.detailOn) AppLog.d("Phone", snapshot(context, power))
                Thread.sleep(60_000)
            }
        }
    }

    fun snapshot(context: Context, power: PowerManager? = context.getSystemService(PowerManager::class.java)): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMb = rt.maxMemory() / (1024 * 1024)
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val battery = power?.isPowerSaveMode == true
        val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        return "App memory ${usedMb}/${maxMb} MB · phone free ${mem.availMem / (1024 * 1024)} MB" +
            (if (mem.lowMemory) " (LOW)" else "") +
            " · battery saver ${if (battery) "ON" else "off"} · battery optimisation ${if (unrestricted) "off for Siansa" else "on for Siansa"}"
    }
}
