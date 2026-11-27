package app.siansa.ui

import androidx.test.core.app.ApplicationProvider
import app.siansa.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's Play on tap (the owner, 27 Nov: "allow instant playback … or open content first"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TapPlaysTest {
    private val did = mutableListOf<String>()
    private val open = { did += "open"; Unit }
    private val play = { did += "play"; Unit }

    @Test fun anItemPlaysOnTapAndOpensWhenHeld() {
        val item = HomeItem(key = "album", title = "An album", open = open, play = play)
        val tapped = item.tapped(true)
        tapped.open()
        tapped.hold!!.invoke()
        assertEquals(listOf("play", "open"), did)
        // Off: as it was, a tap opens.
        assertSame(item, item.tapped(false))
    }

    @Test fun anItemThatCantPlayStaysAsItIs() {
        val radio = HomeItem(key = "artist", title = "An artist", open = play, hold = open)
        assertSame(radio, radio.tapped(true))
    }

    @Test fun aSleeveFollowsItToo() {
        val mix = CrateRecord(key = "mix", name = "A mix", caption = "", covers = emptyList(), songs = emptyList(), onOpen = open, onPlay = play)
        val tapped = mix.tapped(true)
        tapped.onOpen()
        tapped.onHold!!.invoke()
        assertEquals(listOf("play", "open"), did)
        assertSame(mix, mix.tapped(false))
        val noPlay = mix.copy(onPlay = null)
        assertSame(noPlay, noPlay.tapped(true))
    }

    @Test fun theSettingIsKeptAndOffAtFirst() {
        fun settings() = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertFalse(settings().state.value.homeTapPlays)
        settings().save(settings().current.copy(homeTapPlays = true))
        assertTrue(settings().state.value.homeTapPlays)
    }
}
