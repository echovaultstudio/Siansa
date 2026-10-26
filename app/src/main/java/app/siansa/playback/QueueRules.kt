package app.siansa.playback

import androidx.media3.common.Player

/** What a new queue starts with (beta testers #42). */
object QueueRules {
    /** Repeat is for the list it was set on: a new list starts with it off, whatever the last one had. */
    fun repeatForNewQueue(@Player.RepeatMode current: Int): Int = Player.REPEAT_MODE_OFF
}
