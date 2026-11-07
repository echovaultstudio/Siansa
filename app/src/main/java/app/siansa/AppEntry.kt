package app.siansa

import android.content.Context
import android.content.Intent

/**
 * The screen the playback notification and the media session open: the phone app's [MainActivity], or in the TV app
 * (issue #860, the `tv` module) its own TV screen, which only that app has.
 */
object AppEntry {
    /** The TV app's activity, by name: its code lives in the `tv` module, which the phone app doesn't build. */
    const val TV_ACTIVITY = "app.siansa.tvapp.TvActivity"

    fun intent(context: Context): Intent =
        if (BuildConfig.TV_APP) Intent().setClassName(context, TV_ACTIVITY) else Intent(context, MainActivity::class.java)
}
