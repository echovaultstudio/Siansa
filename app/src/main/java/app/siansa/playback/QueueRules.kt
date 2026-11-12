package app.siansa.playback

import androidx.media3.common.Player

/** What a new queue starts with (beta testers #42). */
object QueueRules {
    /** Repeat is for the list it was set on: a new list starts with it off, whatever the last one had. */
    fun repeatForNewQueue(@Player.RepeatMode current: Int): Int = Player.REPEAT_MODE_OFF

    /**
     * The songs a new list plays when it starts at [start] of [songs]: from that song on (the owner, 12 Nov). The
     * songs before it used to be put in the queue too, as if already played: tapping an album's eighth song put its
     * first seven above it. Shuffled, the whole list plays, in any order.
     */
    fun <T> fromStart(songs: List<T>, start: Int, shuffle: Boolean): List<T> =
        if (shuffle || songs.isEmpty()) songs else songs.drop(start.coerceIn(0, songs.size - 1))
}
