package app.siansa.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why the app last closed, read from Android at each start (Android 11+): a crash, a freeze (ANR) with
 * what the app was doing, being killed for memory, the user swiping it away, and so on. Crashes and
 * freezes the app couldn't log itself (the process was already gone) show up here, so a problem that
 * "just closed the app" still leaves a trace. Each exit is logged once.
 */
object ExitReasons {
    private const val PREFS = "diagnostics"
    private const val LAST_SEEN = "last_exit_ms"

    fun logNew(context: Context) {
        // Only while logging is on, so an exit that happened with logging off is still reported once
        // logging is switched on (Android keeps the recent history).
        if (Build.VERSION.SDK_INT < 30 || !AppLog.enabled) return
        runCatching { logNow(context) }.onFailure { AppLog.w("Exit", "Couldn't read why the app last closed: ${it.message}") }
    }

    @RequiresApi(30)
    private fun logNow(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(LAST_SEEN, 0L)
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            .filter { it.timestamp > lastSeen && it.processName == context.packageName }
            .sortedBy { it.timestamp }
        if (exits.isEmpty()) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        exits.forEach { e ->
            val line = "Last closed ${time.format(Date(e.timestamp))}: ${reason(e.reason)}" +
                (e.description?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                " · was ${importance(e.importance)} · memory ${e.pss / 1024} MB"
            when (e.reason) {
                ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_LOW_MEMORY,
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ->
                    AppLog.e("Exit", line + trace(e))
                ApplicationExitInfo.REASON_SIGNALED -> AppLog.w("Exit", line)
                else -> AppLog.i("Exit", line)
            }
        }
        prefs.edit().putLong(LAST_SEEN, exits.last().timestamp).apply()
    }

    /** For a freeze (ANR) or native crash, the start of what Android recorded about the threads. */
    @RequiresApi(30)
    private fun trace(e: ApplicationExitInfo): String {
        if (e.reason != ApplicationExitInfo.REASON_ANR && e.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) return ""
        val text = runCatching { e.traceInputStream?.bufferedReader()?.use { r -> r.lineSequence().take(60).joinToString("\n    ") } }.getOrNull()
        return if (text.isNullOrBlank()) "" else "\n    $text"
    }

    private fun reason(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_ANR -> "FROZE (not responding, Android closed it)"
        ApplicationExitInfo.REASON_CRASH -> "CRASHED"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASHED in native code (audio decoder or image library)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed by Android because the phone ran low on memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed by Android for using too much CPU or battery"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by you (swiped away or force-stopped)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "stopped by you"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by the system"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "restarted after a permission change"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "closed because something it relied on stopped"
        ApplicationExitInfo.REASON_OTHER -> "closed by the system (other)"
        else -> "closed (reason $code)"
    }

    private fun importance(code: Int): String = when {
        code <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "on screen"
        code <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "playing in the background"
        code <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "running in the background"
        else -> "in the background, idle"
    }
}
