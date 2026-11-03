package app.siansa.playback

import android.app.ActivityManager

/**
 * Whether the media notification may ask Android to start the playback service in the foreground (issue #602).
 * Android 12+ refuses that from the background unless the sound is playing, and Media3 doesn't catch the refusal
 * on every path, so a song waiting for data in the background (a slow network) crashed the app.
 */
internal fun mayStartForeground(startInForegroundRequired: Boolean, playing: Boolean, importance: Int): Boolean =
    !startInForegroundRequired ||
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        // Playing is only enough while the service is still in the foreground (the app counts as running a foreground
        // service): once Android has taken that away, asking again from the background is refused, and Media3 crashed
        // on the refusal (0.52.0-dev, a cast update arriving in the background).
        (playing && importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)

/** Whether [e] is Android refusing to start a service in the foreground from the background (Android 12+). */
internal fun refusedForeground(e: Throwable): Boolean =
    android.os.Build.VERSION.SDK_INT >= 31 && e is android.app.ForegroundServiceStartNotAllowedException

/**
 * The media notification, as Media3 makes it, with the foreground start that follows a late notification (a cover
 * loaded after the song changed, a cast update) caught: Media3 only catches Android's refusal on its first path, so
 * on this one it crashed the app (0.52.0-dev). The music plays on; the notification catches up on the next change.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SafeNotificationProvider(
    private val inner: androidx.media3.session.MediaNotification.Provider,
    private val onRefused: () -> Unit,
) : androidx.media3.session.MediaNotification.Provider {
    override fun createNotification(
        mediaSession: androidx.media3.session.MediaSession,
        mediaButtonPreferences: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: androidx.media3.session.MediaNotification.ActionFactory,
        onNotificationChangedCallback: androidx.media3.session.MediaNotification.Provider.Callback,
    ): androidx.media3.session.MediaNotification = inner.createNotification(mediaSession, mediaButtonPreferences, actionFactory) { notification ->
        try {
            onNotificationChangedCallback.onNotificationChanged(notification)
        } catch (e: IllegalStateException) {
            if (!refusedForeground(e)) throw e
            onRefused()
        }
    }

    override fun handleCustomCommand(session: androidx.media3.session.MediaSession, action: String, extras: android.os.Bundle): Boolean =
        inner.handleCustomCommand(session, action, extras)
}
