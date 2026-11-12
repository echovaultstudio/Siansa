package app.siansa.playback

import androidx.media3.common.Player

/** What a new queue starts with (beta testers #42). */
object QueueRules {
    /**
     * Whether a change of repeat or shuffle asked for by the app [asker] is taken: only Siansa's own ([own]). The
     * owner's repeat came on by itself at a song change with the phone on his car's Bluetooth, never tapped (12 Nov):
     * a car or headset can ask the phone to change it, and Siansa has no repeat or shuffle button outside the app.
     */
    fun takesModeChange(asker: String, own: String): Boolean = asker == own

    /** Repeat is for the list it was set on: a new list starts with it off, whatever the last one had. */
    fun repeatForNewQueue(@Player.RepeatMode current: Int): Int = Player.REPEAT_MODE_OFF
}
