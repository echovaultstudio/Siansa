package app.siansa.playback

import android.app.ActivityManager

/**
 * Whether the media notification may ask Android to start the playback service in the foreground (issue #602).
 * Android 12+ refuses that from the background unless the sound is playing, and Media3 doesn't catch the refusal
 * on every path, so a song waiting for data in the background (a slow network) crashed the app.
 */
internal fun mayStartForeground(startInForegroundRequired: Boolean, playing: Boolean, importance: Int): Boolean =
    !startInForegroundRequired || playing || importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
