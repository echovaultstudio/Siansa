package app.siansa.car

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Like, Shuffle and Repeat in Android Auto's player (issue #1042). */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CarButtonsTest {
    @Test fun theButtonsAreLikeShuffleRepeatInThatOrder() {
        val looks = CarButtons.looks(liked = false, shuffle = false, repeat = Player.REPEAT_MODE_OFF)
        assertEquals(listOf(CarButtons.LIKE, CarButtons.SHUFFLE, CarButtons.REPEAT), looks.map { it.action })
        assertEquals(listOf("Like", "Shuffle is off", "Repeat is off"), looks.map { it.name })
        assertEquals(listOf(CommandButton.ICON_HEART_UNFILLED, CommandButton.ICON_SHUFFLE_OFF, CommandButton.ICON_REPEAT_OFF), looks.map { it.icon })
    }

    @Test fun eachButtonShowsHowItsSet() {
        val looks = CarButtons.looks(liked = true, shuffle = true, repeat = Player.REPEAT_MODE_ONE)
        assertEquals(listOf("Liked", "Shuffle is on", "Repeat this song is on"), looks.map { it.name })
        assertEquals(listOf(CommandButton.ICON_HEART_FILLED, CommandButton.ICON_SHUFFLE_ON, CommandButton.ICON_REPEAT_ONE), looks.map { it.icon })
        assertEquals(CommandButton.ICON_REPEAT_ALL, CarButtons.looks(null, false, Player.REPEAT_MODE_ALL).last().icon)
    }

    @Test fun aRadioStationHasNoHeart() {
        assertEquals(listOf(CarButtons.SHUFFLE, CarButtons.REPEAT), CarButtons.looks(liked = null, shuffle = false, repeat = Player.REPEAT_MODE_OFF).map { it.action })
    }

    @Test fun repeatGoesRoundAsInTheApp() {
        assertEquals(Player.REPEAT_MODE_ALL, CarButtons.nextRepeat(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, CarButtons.nextRepeat(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, CarButtons.nextRepeat(Player.REPEAT_MODE_ONE))
    }

    // Beside previous and next, never in their place: a button in a transport slot would take that button away.
    @Test fun theButtonsNeverTakeThePlaceOfPreviousOrNext() {
        val buttons = CarButtons.buttons(liked = true, shuffle = false, repeat = Player.REPEAT_MODE_OFF)
        assertEquals(3, buttons.size)
        assertTrue(buttons.all { it.slots.asList() == listOf(CommandButton.SLOT_OVERFLOW) })
        assertEquals(CarButtons.commands.map { it.customAction }, buttons.map { it.sessionCommand!!.customAction })
        assertTrue(buttons.all { it.isEnabled && it.iconResId != 0 })
    }
}
