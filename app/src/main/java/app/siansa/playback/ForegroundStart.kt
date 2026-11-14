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
        // Playing may always ask: a resume from the notification or headphones after a long pause comes from the
        // background, and without the foreground Android cut the app's network while the screen was off, so the next
        // song couldn't load and the music stopped (the owner's, 10 Nov). Android's refusal (0.52.0-dev, a cast update)
        // is caught now (PlaybackService.onUpdateNotification, SafeNotificationProvider), so asking no longer crashes.
        playing

/** Network errors worth trying again for a while: the connection may come back (a stalled tunnel, a blocked app). */
internal fun keepsTryingAfter(errorCode: Int): Boolean =
    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

/** Checks after a network error (every 2.5 s): about two minutes. */
internal const val NETWORK_RESCUES = 48

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
