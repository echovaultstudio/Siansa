package app.musix.log

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * An optional log kept in the app's private storage, for sharing when something goes wrong.
 * Nothing is written while logging is off. Passwords, tokens and API keys are removed before writing.
 */
object AppLog {
    private const val MAX_BYTES = 5_000_000L
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "musix-log").apply { isDaemon = true } }
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private lateinit var dir: File
    /** App version, Android version and phone model, at the top of shared logs. */
    var header = ""
        private set

    @Volatile
    var enabled: Boolean = false
        private set


    /** The screen being shown, added to stutter reports. */
    @Volatile
    var screen: String = "-"

    /** Everything is recorded while logging is on: frames, freezes, buffering, downloads, phone state. */
    val detailOn: Boolean get() = enabled

    val current: File get() = File(dir, "musix.log")
    private val previous: File get() = File(dir, "musix.1.log")

    fun init(context: Context, enabled: Boolean) {
        dir = File(context.filesDir, "logs").apply { mkdirs() }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        header = "Musix $version · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        setEnabled(enabled)

        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (this.enabled) writeNow("E", "Crash", "Uncaught in ${thread.name}\n" + stack(error))
            system?.uncaughtException(thread, error)
        }
    }

    fun setEnabled(on: Boolean) {
        val was = enabled
        enabled = on
        if (on && !was) i("Log", "Logging on · $header")
    }

    /** Detailed entries (frames, downloads, phone state); written whenever logging is on. */
    fun d(tag: String, message: String) {
        if (detailOn) add("D", tag, message, null)
    }

    fun i(tag: String, message: String) = add("I", tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = add("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = add("E", tag, message, error)

    private fun add(level: String, tag: String, message: String, error: Throwable?) {
        val text = redact(if (error != null) "$message\n${stack(error)}" else message)
        when (level) {
            "E" -> Log.e("Musix/$tag", text)
            "W" -> Log.w("Musix/$tag", text)
            else -> Log.i("Musix/$tag", text)
        }
        if (!enabled) return
        val line = "${time.format(Date())} $level/$tag: $text\n"
        writer.execute { append(line) }
    }

    private fun writeNow(level: String, tag: String, message: String) {
        runCatching { append("${time.format(Date())} $level/$tag: ${redact(message)}\n") }
    }

    @Synchronized
    private fun append(line: String) {
        runCatching {
            val file = current
            if (file.length() > MAX_BYTES) {
                previous.delete()
                file.renameTo(previous)
            }
            file.appendText(line)
        }
    }

    /** The recent part of the log for showing on screen. */
    fun tail(maxLines: Int): List<String> = runCatching {
        if (!current.exists()) emptyList() else current.readLines().takeLast(maxLines)
    }.getOrDefault(emptyList())

    /** Both log parts' lines, oldest first. */
    fun allLines(): List<String> = runCatching {
        listOf(previous, current).filter { it.exists() }.flatMap { it.readLines() }
    }.getOrDefault(emptyList())

    /** One file with both log parts, for sharing. */
    fun export(target: File): File {
        target.parentFile?.mkdirs()
        target.writeText("$header\n\n")
        if (previous.exists()) target.appendText(previous.readText())
        if (current.exists()) target.appendText(current.readText())
        return target
    }

    fun clear() {
        writer.execute {
            previous.delete()
            current.delete()
        }
    }

    fun sizeBytes(): Long = (if (current.exists()) current.length() else 0) + (if (previous.exists()) previous.length() else 0)

    private fun stack(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    private val secrets = listOf(
        Regex("([?&](?:t|s|p|u|token|apiKey|api_key|password)=)[^&\\s\"]+", RegexOption.IGNORE_CASE),
        Regex("(Authorization:?\\s*(?:Bearer|Basic)\\s+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(audiomuse_jwt=)[^;\\s]+"),
        Regex("(sk-or-[A-Za-z0-9-]{4})[A-Za-z0-9-]+"),
        Regex("(://)[^/@\\s]+:[^/@\\s]+@"),
    )

    fun redact(text: String): String = secrets.fold(text) { acc, regex ->
        regex.replace(acc) { m -> m.groupValues[1] + if (m.groupValues[1] == "://") "" else "•••" }
    }
}
