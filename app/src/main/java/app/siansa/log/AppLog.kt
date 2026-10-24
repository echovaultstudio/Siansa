package app.siansa.log

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The app's diagnostic log (issues #180, #237), kept in the app's private storage for sharing when something
 * goes wrong. Each entry is one [LogEvent]: written as a readable line (siansa.log, shown under Settings › Logs)
 * and as a line of JSON (events.jsonl) with its component, event name, session and trace ids and fields.
 *
 * - Nothing is written while logging is off. The last [RECENT] events are always kept in memory, and if the
 *   app crashes they're saved (crash-last.jsonl, on the phone only) so the crash can be looked into.
 * - Writing happens on its own thread with a bounded queue, never on the caller's (the audio thread
 *   included): when the queue is full, detail is dropped first and a count of what was dropped is logged.
 * - Files rotate: the readable log and the events each keep a few parts of a set size.
 * - Passwords, tokens and keys are removed ([Redactor]) before anything is written.
 * - [detail] turns detailed entries (D) of a component on or off; [trace] entries (T) need it too.
 */
object AppLog {
    /** Each part of the readable log; with every request logged in full (issue #180), 5 MB lasted minutes. */
    private const val TEXT_BYTES = 15_000_000L
    private const val EVENT_BYTES = 10_000_000L
    const val RECENT = 500

    private val queue = ArrayBlockingQueue<Runnable>(5_000)
    private val writer = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue) { r -> Thread(r, "siansa-log").apply { isDaemon = true } }
    private val dropped = AtomicLong(0)
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private lateinit var dir: File
    private lateinit var text: RotatingFile
    private lateinit var events: RotatingFile

    /** The last events, kept even while logging is off, for the crash file and the archive. */
    val recent = RingBuffer<LogEvent>(RECENT)

    /** App version, Android version and phone model, at the top of shared logs. */
    var header = ""
        private set

    /** What the archive's manifest says about the app and phone. */
    var about: Map<String, Any?> = emptyMap()
        private set

    @Volatile
    var enabled: Boolean = false
        private set

    /** The screen being shown, added to stutter reports. */
    @Volatile
    var screen: String = "-"

    /** Everything is recorded while logging is on: frames, freezes, buffering, downloads, phone state. */
    val detailOn: Boolean get() = enabled

    /** Whether detailed entries of [component] are written. */
    fun detail(@Suppress("UNUSED_PARAMETER") component: String): Boolean = enabled

    val current: File get() = text.current
    val crashFile: File get() = File(dir, "crash-last.jsonl")

    fun init(context: Context, enabled: Boolean) {
        dir = File(context.filesDir, "logs").apply { mkdirs() }
        text = RotatingFile(dir, "siansa", "log", TEXT_BYTES, 2)
        events = RotatingFile(dir, "events", "jsonl", EVENT_BYTES, 3)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        header = "Siansa $version · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        about = linkedMapOf(
            "app_version" to version,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "session_id" to Trace.session,
        )
        setEnabled(enabled)

        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Written here, on the crashing thread: the writer thread may never get to it.
            runCatching {
                val crash = make(Level.FATAL, "Crash", "app.crash", "Uncaught in ${thread.name}", emptyMap(), error)
                recent.add(crash)
                crashFile.writeText(recent.snapshot().joinToString("\n", postfix = "\n") { it.toJson() })
                if (this.enabled) {
                    text.append(crash.toText(time))
                    events.append(crash.toJson() + "\n")
                }
            }
            system?.uncaughtException(thread, error)
        }
    }

    /** Foreground and background, and the screens' activities coming and going (issue #237). */
    fun watchLifecycle(app: Application) {
        var visible = 0
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (visible++ == 0) event(Level.INFO, "App", "app.foreground", "In the foreground")
            }
            override fun onActivityStopped(activity: Activity) {
                if (--visible == 0) event(Level.INFO, "App", "app.background", "In the background")
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) =
                event(Level.DEBUG, "App", "app.activity_created", "Screen opened", mapOf("restored" to (state != null)))
            override fun onActivityDestroyed(activity: Activity) =
                event(Level.DEBUG, "App", "app.activity_destroyed", "Screen closed", mapOf("finishing" to activity.isFinishing))
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
        // From the process starting to here (the app's own setup), on Android 7+.
        val startup = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
        event(Level.INFO, "App", "app.start", "Started in $startup ms", mapOf("startup_ms" to startup))
    }

    fun setEnabled(on: Boolean) {
        val was = enabled
        enabled = on
        if (on && !was) event(Level.INFO, "Log", "diagnostics.on", "Logging on · $header", about)
        if (!on && was) event(Level.INFO, "Log", "diagnostics.off", "Logging off")
    }

    // ---- Writing ----

    /** A detailed entry; written whenever logging is on. */
    fun d(tag: String, message: String, event: String = "log", fields: Map<String, Any?> = emptyMap()) {
        if (detail(tag)) add(Level.DEBUG, tag, event, message, fields, null)
    }

    /** Very fine detail (e.g. every step of a decode); only while [component]'s detail is on. */
    fun t(tag: String, message: String, event: String = "log", fields: Map<String, Any?> = emptyMap()) {
        if (detail(tag)) add(Level.TRACE, tag, event, message, fields, null)
    }

    fun i(tag: String, message: String, event: String = "log", fields: Map<String, Any?> = emptyMap()) =
        add(Level.INFO, tag, event, message, fields, null)

    fun w(tag: String, message: String, error: Throwable? = null, event: String = "log", fields: Map<String, Any?> = emptyMap()) =
        add(Level.WARN, tag, event, message, fields, error)

    fun e(tag: String, message: String, error: Throwable? = null, event: String = "log", fields: Map<String, Any?> = emptyMap()) =
        add(Level.ERROR, tag, event, message, fields, error)

    /** A named event with its fields (issue #237), e.g. event(INFO, "Player", "playback.first_audio", …). */
    fun event(level: Level, component: String, event: String, message: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        if (level <= Level.DEBUG && !detail(component)) return
        add(level, component, event, message, fields, error)
    }

    private fun make(level: Level, tag: String, event: String, message: String, fields: Map<String, Any?>, error: Throwable?) =
        LogEvent(System.currentTimeMillis(), level, tag, event, Trace.session, Trace.current(), Thread.currentThread().name, message, fields, error)

    private fun add(level: Level, tag: String, event: String, message: String, fields: Map<String, Any?>, error: Throwable?) {
        val entry = make(level, tag, event, message, fields, error)
        recent.add(entry)
        when (level) {
            Level.ERROR, Level.FATAL -> Log.e("Siansa/$tag", Redactor.text(message), error)
            Level.WARN -> Log.w("Siansa/$tag", Redactor.text(message), error)
            Level.INFO -> Log.i("Siansa/$tag", Redactor.text(message))
            else -> Unit
        }
        if (!enabled || !::text.isInitialized) return
        submit(level) {
            text.append(entry.toText(time))
            events.append(entry.toJson() + "\n")
        }
    }

    /** Hands a write to the writer thread; when it's behind, detail is dropped first and counted. */
    private fun submit(level: Level, write: () -> Unit) {
        val important = level >= Level.WARN
        if (!important && queue.remainingCapacity() < 500) {
            dropped.incrementAndGet()
            return
        }
        val skipped = dropped.getAndSet(0)
        try {
            writer.execute {
                if (skipped > 0) {
                    val note = make(Level.WARN, "Log", "diagnostics.dropped", "Left out $skipped detailed entries: the log couldn't keep up", mapOf("dropped" to skipped), null)
                    text.append(note.toText(time))
                    events.append(note.toJson() + "\n")
                }
                write()
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            dropped.addAndGet(skipped + 1)
        }
    }

    // ---- Reading and sharing ----

    /** The recent part of the readable log for showing on screen. */
    fun tail(maxLines: Int): List<String> = runCatching {
        if (!current.exists()) emptyList() else current.readLines().takeLast(maxLines)
    }.getOrDefault(emptyList())

    /** Every part's lines, oldest first. */
    fun allLines(): List<String> = runCatching { text.files().flatMap { it.readLines() } }.getOrDefault(emptyList())

    /** One file with the whole readable log, for sharing. */
    fun export(target: File): File {
        target.parentFile?.mkdirs()
        target.writeText("$header\n\n")
        text.files().forEach { target.appendText(it.readText()) }
        return target
    }

    /**
     * The diagnostics archive (issue #237): readable log, structured events, the recent events kept in
     * memory, the last crash's events if there was one, and a manifest. Everything is already redacted.
     */
    fun exportArchive(target: File): File {
        val recentFile = File(target.parentFile, "recent.jsonl").apply {
            parentFile?.mkdirs()
            writeText(recent.snapshot().joinToString("\n", postfix = "\n") { it.toJson() })
        }
        val parts = text.files().map { DiagnosticZip.Part(it.name, it) } +
            events.files().map { DiagnosticZip.Part(it.name, it) } +
            listOf(DiagnosticZip.Part("recent.jsonl", recentFile)) +
            listOfNotNull(crashFile.takeIf { it.exists() }?.let { DiagnosticZip.Part("crash-last.jsonl", it) })
        DiagnosticZip.write(target, parts, about + mapOf("logging_on" to enabled), System.currentTimeMillis())
        recentFile.delete()
        return target
    }

    fun clear() {
        writer.execute {
            text.clear()
            events.clear()
            crashFile.delete()
        }
    }

    fun sizeBytes(): Long = if (!::text.isInitialized) 0 else text.sizeBytes() + events.sizeBytes()

    /** The text patterns of [Redactor], for code that builds its own lines. */
    fun redact(text: String): String = Redactor.text(text)
}
