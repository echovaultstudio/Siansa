package app.siansa.car

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList

/**
 * Like, Shuffle and Repeat beside play, previous and next in Android Auto's player (issue #1042). They're buttons of
 * the media session, so Android shows what fits of them wherever it draws the session: the car, and from Android 13
 * the phone's own notification and lock screen too.
 *
 * They're Siansa's own buttons ([LIKE], [SHUFFLE], [REPEAT]), not Android's repeat and shuffle commands: those stay
 * turned down for other apps ([app.siansa.playback.QueueRules.takesModeChange]), since a car's Bluetooth once switched
 * repeat on by itself. One of these only happens when its button is pressed.
 */
@OptIn(UnstableApi::class)
object CarButtons {
    const val LIKE = "app.siansa.car.LIKE"
    const val SHUFFLE = "app.siansa.car.SHUFFLE"
    const val REPEAT = "app.siansa.car.REPEAT"

    /** The commands a connected app may send, so the buttons are live for it. */
    val commands: List<SessionCommand> = listOf(LIKE, SHUFFLE, REPEAT).map { SessionCommand(it, Bundle.EMPTY) }

    /** One button as it's shown: what pressing it sends, what it says and its picture. */
    data class Look(val action: String, val name: String, val icon: Int)

    /**
     * The buttons for the song playing, each showing how it's set. [liked] is null when there's nothing to like
     * (no song, a radio station, a podcast): the heart is then left out.
     */
    fun looks(liked: Boolean?, shuffle: Boolean, @Player.RepeatMode repeat: Int): List<Look> = listOfNotNull(
        liked?.let { Look(LIKE, if (it) "Liked" else "Like", if (it) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED) },
        Look(SHUFFLE, if (shuffle) "Shuffle is on" else "Shuffle is off", if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF),
        when (repeat) {
            Player.REPEAT_MODE_ALL -> Look(REPEAT, "Repeat all is on", CommandButton.ICON_REPEAT_ALL)
            Player.REPEAT_MODE_ONE -> Look(REPEAT, "Repeat this song is on", CommandButton.ICON_REPEAT_ONE)
            else -> Look(REPEAT, "Repeat is off", CommandButton.ICON_REPEAT_OFF)
        },
    )

    /** [looks] as the session's buttons. They go beside the transport buttons, never in place of previous or next. */
    fun buttons(liked: Boolean?, shuffle: Boolean, @Player.RepeatMode repeat: Int): ImmutableList<CommandButton> =
        ImmutableList.copyOf(looks(liked, shuffle, repeat).map { look ->
            CommandButton.Builder(look.icon)
                .setDisplayName(look.name)
                .setSessionCommand(SessionCommand(look.action, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        })

    /** Repeat after one press, in the order of the app's own button: off, all, this song, off. */
    @Player.RepeatMode
    fun nextRepeat(@Player.RepeatMode now: Int): Int = when (now) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
}
