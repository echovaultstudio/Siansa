package app.siansa.data

import android.content.SharedPreferences
import java.util.concurrent.Executors

/**
 * Saving to SharedPreferences off the main thread, for the small things saved as music plays (a cover compared, a
 * cover's alias, a notice tapped). `apply()` saves in the background too, but Android makes the main thread wait for
 * every save still pending whenever a service starts or stops: on the TV a song's start (the playback service starting)
 * froze the screen for 1.2 s while such saves finished (0.52.0-dev, issue #860). A `commit()` on a thread of our own
 * leaves nothing for Android to wait for. One thread, so saves to one file keep their order.
 */
object QuietWrites {
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "siansa-prefs").apply { isDaemon = true } }

    /** Makes the changes in [edit] to [prefs] and saves them on Siansa's own thread. */
    fun save(prefs: SharedPreferences, edit: SharedPreferences.Editor.() -> Unit) {
        thread.execute { runCatching { prefs.edit().apply(edit).commit() } }
    }
}
