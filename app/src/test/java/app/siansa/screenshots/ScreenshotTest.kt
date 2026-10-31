package app.siansa.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.siansa.data.HomeLook
import app.siansa.data.NavTab
import app.siansa.data.Song
import app.siansa.data.SyncPhase
import app.siansa.data.SyncProgress
import app.siansa.data.TopBarStyle
import app.siansa.mix.CardRule
import app.siansa.mix.MixShape
import app.siansa.mix.RuleField
import app.siansa.mix.RuleOp
import app.siansa.playback.dlna.DlnaDevice
import app.siansa.ui.ArrangeList
import app.siansa.ui.BottomTabs
import app.siansa.ui.DlnaSettingsContent
import app.siansa.ui.GradientTile
import app.siansa.ui.HomeItem
import app.siansa.ui.HomeLookRow
import app.siansa.ui.MixShapeControls
import app.siansa.ui.PlayerMiniHeader
import app.siansa.ui.ProgressCard
import app.siansa.ui.RuleCard
import app.siansa.ui.SiansaTheme
import app.siansa.ui.SourceCardForTest
import app.siansa.ui.Space
import app.siansa.ui.SpacingRow
import app.siansa.ui.SyncStrip
import app.siansa.ui.ThemeChoice
import app.siansa.ui.ThemeMode
import app.siansa.ui.TopBarChoice
import app.siansa.ui.bottomBarStops
import app.siansa.ui.icon
import app.siansa.ui.playerColors
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests (issue #710): parts of the app drawn on the build machine (Robolectric, no emulator) in light and
 * dark, at normal and 130 % font size, and compared with the approved images in app/src/test/screenshots.
 *
 * - CI compares them on every pull request (`-Proborazzi.test.verify=true`): a picture that changed fails the unit
 *   tests, and the run's reports artifact keeps the before, after and difference images (outputs/roborazzi).
 * - After a change meant to look different, label the pull request `record screenshots`: CI draws the pictures again
 *   on GitHub's runner and commits them to the pull request, so the review shows them (.github/workflows/screenshots.yml).
 *   Recording on another machine (`gradle :app:recordRoborazziDebug`) is fine for a look, but may differ by a pixel.
 *
 * Each scene uses plain sample data (no server, no covers), so the pictures only change when the app's look does.
 * The hard cases are in every scene: long words, light mode and a larger font.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the scenes need no server, player or background work, which the app's own start-up sets up.
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScreenshotTest(private val scene: Scene, private val dark: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createComposeRule()

    @Test fun looksAsApproved() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SiansaTheme(ThemeChoice(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxWidth().testTag(SHOT)) { scene.content() }
                    }
                }
            }
        }
        compose.onNodeWithTag(SHOT).captureRoboImage("src/test/screenshots/${scene.name}_${if (dark) "dark" else "light"}_${(fontScale * 100).toInt()}.png")
    }

    /** One part of the app to draw, named for its picture. */
    class Scene(val name: String, val content: @Composable () -> Unit) {
        override fun toString() = name
    }

    companion object {
        private const val SHOT = "screenshot"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1} font={2}")
        fun cases(): List<Array<Any>> = SCENES.flatMap { scene ->
            listOf(false, true).flatMap { dark -> listOf(1f, 1.3f).map { font -> arrayOf<Any>(scene, dark, font) } }
        }

        private val song = Song(id = "1", title = "So Nearly", artist = "Lonely The Brave", album = "The Day's War")

        /**
         * Now playing's own colours, white on the cover's dark colours in light mode too: the player's colour scheme
         * (PlayerScreen) and its white text (Root, around the player's sheet).
         */
        @Composable
        private fun OnPlayer(content: @Composable () -> Unit) {
            MaterialTheme(colorScheme = playerColors(), typography = MaterialTheme.typography) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Box(Modifier.fillMaxWidth().background(Color(0xFF221C2E))) { content() }
                }
            }
        }

        private fun items(vararg titles: String) =titles.mapIndexed { i, t -> HomeItem(key = "item-$i", title = t, subtitle = "40 tracks", open = {}) }

        val SCENES = listOf(
            // Home's Radios: long words must stay whole ("Melancholy", issue #697).
            Scene("home_radio_tiles") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                        GradientTile("Melancholy", Color(0xFF4A5A78) to Color(0xFF232B3D), onClick = {}, size = 150.dp)
                        GradientTile("Workout", Color(0xFFC62828) to Color(0xFF4A0E0E), onClick = {}, size = 150.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                        GradientTile("Rainy Window, Early Hours", Color(0xFFB0306A) to Color(0xFF4A1230), onClick = {}, size = 150.dp, caption = "40 tracks")
                        GradientTile("Unbelievably long playlistname", Color(0xFF6A3FB5) to Color(0xFF2A1650), onClick = {}, size = 150.dp)
                    }
                }
            },
            // Home rows in their other looks (issue #655).
            Scene("home_banner") {
                HomeLookRow(HomeLook.BANNER, "today", "Rainy Window, Early Hours", items("Evian", "Revenant", "Infinity Pool"))
            },
            Scene("home_list_and_button") {
                Column {
                    HomeLookRow(HomeLook.LIST, "recent", "Recently played", items("Anemoia", "Silent Attraction", "The Tree Cutting Song"))
                    HomeLookRow(HomeLook.BUTTON, "liked", "Liked songs", items("Evian"))
                }
            },
            // The tabs along the bottom, on the bar's fade (light mode, issue #699).
            Scene("bottom_tabs") {
                Column(Modifier.background(Brush.verticalGradient(*bottomBarStops(MaterialTheme.colorScheme.surface)))) {
                    BottomTabs(NavTab.default, "home") {}
                }
            },
            // Now playing: the small header when scrolled down (issue #683).
            Scene("player_mini_header") {
                OnPlayer { PlayerMiniHeader(song, isPlaying = true, progress = 0.4f, liked = false, onHeart = {}, onPlayPause = {}, onTop = {}) }
            },
            // The queue's mix controls ("Surprises", issue #677).
            Scene("queue_mix_shape") {
                OnPlayer { Box(Modifier.padding(vertical = Space.Medium)) { MixShapeControls(MixShape.STEADY, 50) { _, _ -> } } }
            },
            // Settings › Style (issue #681).
            Scene("style_top_bar") { TopBarChoice(TopBarStyle.BLACK) {} },
            // Arrange Home's spacing ("Compact", issue #694).
            Scene("arrange_spacing") { SpacingRow(gap = 1) {} },
            // A list to rearrange (Nav Bar, Mini Player; issue #701).
            Scene("arrange_list") {
                ArrangeList(
                    order = NavTab.entries.toList(),
                    shown = NavTab.default.toSet(),
                    label = { it.label },
                    icon = { it.icon() },
                    key = { it.route },
                    canHide = { it !in NavTab.required },
                    canShowMore = true,
                    onChange = { _, _ -> },
                )
            },
            // Home › Source filter: a server's card with its sync line (issue #693).
            Scene("source_card") {
                Column(Modifier.padding(Space.Page)) { SourceCardForTest(syncLine = "Synced · 312 books · 4 podcasts") }
            },
            Scene("source_sync_running") {
                Column(Modifier.padding(Space.Page)) {
                    SyncStrip("compilations (2 of 4) · 2,099 albums", running = true, fraction = 0.4f, done = false, failed = false)
                }
            },
            // The Library sync card, finished (one tick, issue #693).
            Scene("sync_card_done") {
                Column(Modifier.padding(Space.Page)) {
                    ProgressCard(SyncProgress(phase = SyncPhase.DONE, artists = 1195, albums = 2455, songs = 43194, changedAlbums = 12))
                }
            },
            // A speaker's settings behind the cog in Play on (beta testers #23; "Speaker plays the queue" left in #721).
            Scene("cast_speaker_settings") {
                Column(Modifier.padding(Space.Section)) {
                    DlnaSettingsContent(
                        DlnaDevice("uuid:jbl", "JBL BAR 800MK2", null, "http://speaker/transport", null, formats = setOf("audio/mpeg", "audio/flac", "audio/wav")),
                    )
                }
            },
            // Custom card editor: a rule (issue #663).
            Scene("card_rule") {
                Column(Modifier.padding(Space.Page)) {
                    RuleCard(CardRule(RuleField.LABEL, RuleOp.CONTAINS, "Ninja Tune"), suggestions = listOf("Ninja Tune", "Warp", "Hospital Records"), onChange = {}, onRemove = {})
                }
            },
        )
    }
}
