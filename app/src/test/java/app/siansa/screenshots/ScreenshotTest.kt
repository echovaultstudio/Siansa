package app.siansa.screenshots

import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.siansa.data.HomeLook
import app.siansa.data.NavTab
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
import app.siansa.ui.ChromecastSettingsContent
import app.siansa.ui.DlnaSettingsContent
import app.siansa.ui.GradientTile
import app.siansa.ui.HomeItem
import app.siansa.ui.HomeLookRow
import app.siansa.ui.ProgressCard
import app.siansa.playback.BeatMatchMode
import app.siansa.ui.BeatMatchSection
import app.siansa.net.SourceKind
import app.siansa.ui.Hint
import app.siansa.ui.NewServerButtons
import app.siansa.ui.ApiKeyField
import app.siansa.ui.TestRow
import app.siansa.data.AppSettings
import app.siansa.net.SubsonicApi
import app.siansa.ui.ServerKindChips
import app.siansa.ui.MenuRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Shuffle
import app.siansa.ui.FoldSection
import app.siansa.ui.SwitchRow
import app.siansa.ui.TuningRow
import app.siansa.data.DailyMix
import app.siansa.ui.CROSSFADE_GAP
import app.siansa.ui.RuleCard
import app.siansa.ui.SiansaTheme
import app.siansa.ui.SourceCardForTest
import app.siansa.ui.Space
import app.siansa.ui.SpacingRow
import app.siansa.ui.cardFill
import app.siansa.ui.SyncStrip
import app.siansa.ui.ThemeChoice
import app.siansa.ui.ThemeMode
import app.siansa.ui.TopBarChoice
import app.siansa.ui.bottomBarStops
import app.siansa.ui.icon
import app.siansa.ui.playerColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import app.siansa.ui.nowPlayingCardColor
import app.siansa.ui.pageBottomColor
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
            // All genres: three small tiles a row, where three lines didn't fit the height and were cut off ("Drum And B…",
            // 10 Nov): they shrink to fit.
            Scene("genre_tiles_small") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                        GradientTile("Drum And Bass", Color(0xFF2C7A8C) to Color(0xFF173A47), onClick = {}, size = 104.dp)
                        GradientTile("Alternative Rock", Color(0xFF4A5AC8) to Color(0xFF22285A), onClick = {}, size = 104.dp)
                        GradientTile("Progressive Breaks", Color(0xFFB0306A) to Color(0xFF4A1230), onClick = {}, size = 104.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                        GradientTile("Hip Hop / Electro", Color(0xFF3A8C4A) to Color(0xFF173A20), onClick = {}, size = 104.dp)
                        GradientTile("Contemporary Jazz", Color(0xFFE08A2C) to Color(0xFFB04A20), onClick = {}, size = 104.dp)
                        GradientTile("Idm", Color(0xFF4A5AC8) to Color(0xFF22285A), onClick = {}, size = 104.dp)
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
            // Cover rows for a Home section (issue #789): albums with a third line, and a radio with colours instead of a cover.
            Scene("home_cover_rows") {
                HomeLookRow(HomeLook.COVER_ROWS, "recent", "Recently played", listOf(
                    HomeItem("a1", "Romance", "Fontaines D.C.", null, open = {}, play = {}, meta = "11 songs · 2024"),
                    HomeItem("a2", "An album title long enough to be cut off at the edge", "Unbelievably Long Artist Name Collective", null, open = {}, play = {}, meta = "24 songs"),
                    HomeItem("r1", "Melancholy radio", colors = Color(0xFF4B2BD6) to Color(0xFF8FD3C8), open = {}),
                ), openAll = {})
            },
            // Play button cards (issue #730): only the ▶ plays, no arrow at the end. A Home section, a custom card, the same
            // card while it plays, and long titles that must end in "…" before the card's edge.
            Scene("home_play_buttons") {
                val songs = (1..24).map { app.siansa.data.Song(id = "s$it", title = "Song $it", artist = "Artist") }
                val card = app.siansa.mix.PinnedCard(id = "pin-scene-a", title = "Shuffle it", look = app.siansa.mix.CardLook.BUTTON)
                val long = app.siansa.mix.PinnedCard(id = "pin-scene-b", title = "Unbelievably long playlistname for rainy evenings", look = app.siansa.mix.CardLook.BUTTON)
                Column {
                    HomeLookRow(HomeLook.BUTTON, "added", "Recently added", items("Evian", "Revenant"), openAll = {}, play = {})
                    app.siansa.ui.PinnedPlayButton(card, songs, open = {}, play = { _, _, _ -> })
                    app.siansa.ui.PinnedPlayButton(long, songs, open = {}, play = { _, _, _ -> }, playback = app.siansa.ui.ListPlayback(loaded = true, playing = true) {})
                }
            },
            // The tabs along the bottom, on the bar's fade (light mode, issue #699).
            Scene("bottom_tabs") {
                Column(Modifier.background(Brush.verticalGradient(*bottomBarStops(MaterialTheme.colorScheme.surface)))) {
                    BottomTabs(NavTab.default, "home") {}
                }
            },
            // Now playing's cards take the cover's colour (#717): what a bright, a dark, a black-and-white and a colourless
            // cover give, each card on the page's colour under it, with the cards' white text.
            Scene("player_card_colours") {
                OnPlayer {
                    Column(Modifier.fillMaxWidth()) {
                        listOf(Color(0xFFE53935), Color(0xFFFFD54F), Color(0xFF1E2A78), Color(0xFF9E9E9E), Color.Black).forEach { cover ->
                            // The page's tint, as rememberCoverColor makes it from the cover's colour.
                            val tint = lerp(cover, Color.Black, 0.35f)
                            Box(Modifier.fillMaxWidth().background(pageBottomColor(tint)).padding(horizontal = Space.Page, vertical = Space.Small)) {
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(nowPlayingCardColor(tint)).padding(Space.Page),
                                    verticalArrangement = Arrangement.spacedBy(Space.Tight),
                                ) {
                                    Text("About the artist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Lonely The Brave are a rock band from Cambridge, England.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            },
            // The queue's Shape your mix (the owner, 10 Nov): folded, and open with the glide bar and the Surprises bar.
            Scene("queue_mix_shape") {
                OnPlayer {
                    Column(Modifier.padding(vertical = Space.Medium)) {
                        val places = app.siansa.ui.DjBarPlaces(app.siansa.ui.DjBarSpot.default, starting = false)
                        app.siansa.ui.MixShapePanel(places, MixShape.BUILD, 55, open = false, onOpen = {}, onPlace = {}, onSurprise = {})
                        app.siansa.ui.MixShapePanel(places, MixShape.BUILD, 55, open = true, onOpen = {}, onPlace = {}, onSurprise = {})
                        app.siansa.ui.MixShapePanel(places.copy(starting = true), null, null, open = true, onOpen = {}, onPlace = {}, onSurprise = {})
                    }
                }
            },
            // Shape your sound (the owner, 11 Nov): the pad with a mix's songs on it, the Niche ↔ Familiar pill, the dials
            // (the AI's and the mix's own, with a long name) and the mix's sounds to add; on the page, and on a dark one.
            Scene("shape_your_sound") {
                val tuning = app.siansa.mix.VibeTuning(
                    energy = -1, tempo = 1, familiar = -2,
                    sounds = listOf(
                        app.siansa.mix.SoundDial("dance", -5),
                        app.siansa.mix.SoundDial("Experimental Field Recordings", 2, genre = true),
                    ),
                )
                val dots = (0 until 24).map { app.siansa.mix.PadDot(0.12f + (it * 7 % 11) / 22f, 0.1f + (it * 5 % 9) / 18f) }
                val more = listOf("Drone", "Neo-Classical Downtempo").map { app.siansa.mix.MixSound(it, 4) }
                Column(Modifier.padding(Space.Page)) {
                    app.siansa.ui.VibeSliders(tuning, onChange = {}, dots = dots, soundsTitle = "Sounds in this mix", more = more)
                }
            },
            // The same before there's a mix (Flow): nothing set, no songs on the pad, one sound; on a dark page.
            Scene("shape_your_sound_unset") {
                OnPlayer {
                    Column(Modifier.padding(Space.Page)) {
                        app.siansa.ui.VibeSliders(app.siansa.mix.VibeTuning(sounds = listOf(app.siansa.mix.SoundDial("piano", 0))), onChange = {})
                    }
                }
            },
            // Settings › Style (issue #681).
            Scene("style_top_bar") { TopBarChoice(TopBarStyle.BLACK) {} },
            // Themes › Pages and cards (issue #761): grey, warm, blue grey and the theme's own, with long labels.
            Scene("themes_pages_and_cards") {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Box(Modifier.padding(Space.Page)) {
                    app.siansa.ui.SurfaceSwatches(app.siansa.ui.SurfaceColour.GREY, themeOwn = app.siansa.ui.themeScheme(app.siansa.ui.ThemeColours.SIANSA, dark, surfaces = app.siansa.ui.SurfaceColour.THEME), dark = dark, onPick = {})
                }
            },
            // Grey cards on a grey page (issue #761): what Home's tiles and a menu sheet sit on now.
            Scene("grey_cards") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHighest).forEach { c ->
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c).padding(Space.Page)) {
                            Text("Unbelievably long playlistname", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            // Arrange Home's spacing ("Compact", issue #694).
            Scene("arrange_spacing") { SpacingRow(gap = 1) {} },
            // The cards' colour (the owner, 13 Nov): the four small cards to pick from, Soft chosen; and Today's mix in each
            // look, as the cog's dialog shows it.
            Scene("card_looks") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                    app.siansa.ui.CardLookChooser(app.siansa.data.CardLook.SOFT, app.siansa.data.CardLook.forHome) {}
                    app.siansa.data.CardLook.forHome.forEach { app.siansa.ui.CardLookSample(it, Modifier.fillMaxWidth(), big = true) }
                }
            },
            // A card painted after its own padding, as Today's mix is (the owner, 13 Nov: the wash sat a padding inside
            // the solid, a grey frame): the colour must reach the rounded edge.
            Scene("card_fill_edge") {
                androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalCardLook provides app.siansa.data.CardLook.SOFT) {
                    val paint = app.siansa.ui.cardPaint(Color(0xFFD6338A) to Color(0xFF4A1470))
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Medium)
                            .clip(RoundedCornerShape(16.dp)).cardFill(paint, RoundedCornerShape(16.dp)),
                    ) { Text("To the edge", Modifier.padding(Space.Page), color = paint.text) }
                }
            },
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
            // Play on's rows (issue #114): a Cast device's own name with what it is under it, never what the TV shows.
            Scene("cast_play_on_rows") {
                Column {
                    app.siansa.ui.TargetRow(Icons.Rounded.Tv, "Living Room SHIELD with an uncharacteristically long name", app.siansa.ui.CastKind.Tv.detail, selected = true, onSettings = {}) {}
                    app.siansa.ui.TargetRow(Icons.Rounded.Speaker, "Kitchen and living room speakers", app.siansa.ui.CastKind.Group.detail, selected = false, onSettings = {}) {}
                    app.siansa.ui.TargetRow(Icons.Rounded.Speaker, "JBL BAR 800MK2", "DLNA · JBL", selected = false, onSettings = {}) {}
                }
            },
            // The Chromecast settings behind the cog in Play on (issue #114): sound quality, lossless by default.
            Scene("cast_chromecast_settings") {
                Column(Modifier.padding(Space.Section)) {
                    ChromecastSettingsContent()
                }
            },
            // The top of Home (issue #872): the notices with the round menu button in line at their end, while casting;
            // then with no notices and the source filter on (a ring round the button, its lines in the accent colour).
            Scene("home_header_menu") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    app.siansa.ui.HomeTopBar(
                        listOf(app.siansa.mix.Notices.yourMonth(java.time.LocalDate.of(2026, 11, 2))!!, app.siansa.mix.Notices.newAlbums(6, "a")!!, app.siansa.mix.Notices.carryOn("b", "Dune")),
                        onOpen = {},
                    ) {
                        app.siansa.ui.HomeMenuButton(app.siansa.ui.rememberHomeMenuState(), casting = true, filterActive = false, onPick = {})
                    }
                    app.siansa.ui.HomeTopBar(emptyList(), onOpen = {}) {
                        app.siansa.ui.HomeMenuButton(app.siansa.ui.rememberHomeMenuState(), casting = false, filterActive = true, onPick = {})
                    }
                }
            },
            // Home's menu (the owner, 12 Nov): the top row's chips give way to one pill of glass with the four places as
            // circles, Sources lit under the finger of a glide; and while casting with the source filter on, Play on…
            // and Sources ringed in the accent colour. Fully open, and half way with the chips still fading under it.
            Scene("home_menu") {
                val quiet = app.siansa.ui.HomeMenuInfo(app.siansa.ui.PlayOnLine("Play on…", "TV or speaker", false), "All libraries", false)
                val busy = app.siansa.ui.HomeMenuInfo(app.siansa.ui.PlayOnLine("Playing on Living Room SHIELD", "Chromecast", true), "Downloaded only · 2 users", true)
                val chips = listOf(app.siansa.mix.Notices.newAlbums(24, "a")!!)
                Column(Modifier.padding(vertical = Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                    for ((open, info, lit) in listOf(Triple(1f, quiet, app.siansa.data.HomeMenuItem.SOURCES), Triple(1f, busy, null), Triple(0.45f, quiet, null))) {
                        val state = app.siansa.ui.rememberHomeMenuState().also { it.open = true; it.highlighted = lit }
                        app.siansa.ui.HomeTopBar(
                            chips, onOpen = {}, menuOpen = open,
                            menuBar = { app.siansa.ui.HomeMenuBar(state, info, app.siansa.data.HomeMenuItem.default, open = open, onPick = {}) },
                        ) { app.siansa.ui.HomeMenuButton(state, casting = info.playOn.active, filterActive = info.filterActive, onPick = {}) }
                    }
                }
            },
            // Menu rows (issue #778): no badge on shuffle, a word badge where one says something.
            Scene("menu_rows") {
                Column {
                    MenuRow(Icons.Rounded.Shuffle, "Shuffle: off") {}
                    MenuRow(Icons.Rounded.Bedtime, "Sleep at the end of the chapter, uncharacteristically", badge = "on") {}
                }
            },
            // A music server being added (issue #828): its type, then Save or Cancel; nothing is added before Save.
            Scene("new_server") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    ServerKindChips(SourceKind.JELLYFIN) {}
                    Hint(SourceKind.JELLYFIN.detail)
                    NewServerButtons(onSave = {}, onCancel = {})
                }
            },
            // The OpenSubsonic API key on the server form (issue #493), with Test saying plainly the server doesn't take keys.
            Scene("server_api_key") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    ApiKeyField(AppSettings(navidromeApiKey = "a-very-long-api-key-from-the-server-0123456789")) {}
                    TestRow("✗ " + SubsonicApi.NO_API_KEYS) {}
                }
            },
            // Beat-matched blends with the echo switch (issue #759).
            Scene("crossfade_beat_match") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(CROSSFADE_GAP)) { BeatMatchSection(BeatMatchMode.DJ_RADIO, bars = 8, echo = true, bassSwap = true, keepKey = false, onMode = {}, onBars = {}, onEcho = {}, onBassSwap = {}, onKeepKey = {}) }
            },
            // Today's mix settings (issue #776): the switch and tuning chips, and a folded settings section with its
            // summary, then one opened (the arrow turned).
            Scene("todays_mix_settings") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    SwitchRow("Auto update", true) {}
                    TuningRow("Songs", 40, DailyMix.LENGTHS, { "$it" }, "dailyLength") {}
                    TuningRow("Leave out most played", null, DailyMix.LEAVE_OUT, { if (it == 0) "None" else "Top $it" }, "dailyLeaveOut") {}
                    FoldSection("Streaming quality", "Wi-Fi Original · mobile Opus 128", tag = "quality", startOpen = false) { Text("Body") }
                    FoldSection("Beat-matched blends", "DJ radio · 16 bars · echo", tag = "blends", startOpen = true) {
                        Text("Incomprehensibilities of the uncharacteristically long settings body")
                    }
                }
            },
            // Cover rows (issue #782): albums with one playing and long names cut off, then books and an episode with
            // their progress along the cover.
            Scene("cover_rows") {
                fun a(row: app.siansa.mix.CoverRow, playing: Boolean = false) = app.siansa.ui.CoverRowAction(row, playing, {}, {}, listOf("Add to queue" to {}))
                Column {
                    app.siansa.ui.CoverRowsShelf("Recently added albums", listOf(
                        a(app.siansa.mix.CoverRow("1", app.siansa.mix.CoverRow.Kind.ALBUM, null, "Fontaines D.C.", "Romance", "11 songs · 2024")),
                        a(app.siansa.mix.CoverRow("2", app.siansa.mix.CoverRow.Kind.ALBUM, null, "Vince Staples", "Dark Times", "13 songs · 2024"), playing = true),
                        a(app.siansa.mix.CoverRow("3", app.siansa.mix.CoverRow.Kind.ALBUM, null, "Unbelievably Long Artist Name Collective", "An album title long enough to be cut off", "24 songs")),
                        a(app.siansa.mix.CoverRow("4", app.siansa.mix.CoverRow.Kind.ALBUM, null, "Rammstein", "Zeit", "11 songs")),
                    )) {}
                    app.siansa.ui.CoverRowsShelf("Keep listening", listOf(
                        a(app.siansa.mix.CoverRow("5", app.siansa.mix.CoverRow.Kind.BOOK, null, "Andy Weir", "Project Hail Mary", "16 h 10 min · 6 h left", progress = 0.62f)),
                        a(app.siansa.mix.CoverRow("6", app.siansa.mix.CoverRow.Kind.EPISODE, null, "Rocket Hour", "Ep. 479 · Exploring the Dopamine Chamber", "58 min · 46 min left", progress = 0.2f)),
                    )) {}
                }
            },
            // The notices row at the top of Home (issue #784), with a long book title cut off.
            Scene("home_notices") {
                app.siansa.ui.HomeTopBar(
                    listOf(
                        app.siansa.mix.Notice(app.siansa.mix.NoticeKind.NEEDS_YOU, "n", "Library sync failed", "x"),
                        app.siansa.mix.Notices.carryOn("b", "The Extraordinarily Long Chronicles of Wandering Mapmakers"),
                        app.siansa.mix.Notices.newAlbums(3, "a")!!,
                    ),
                    onOpen = {},
                ) {
                    app.siansa.ui.HomeMenuButton(app.siansa.ui.rememberHomeMenuState(), casting = false, filterActive = false, onPick = {})
                }
            },
            // Now playing's play button (issue #919): record grooves while a DJ mix plays, and a slide's choice, far enough
            // to start (up, with its name) and not yet (left), on the player's colours.
            Scene("play_button_grooves_and_slides") {
                OnPlayer {
                    // Each in a band of its own, with room on the side its choice and name go.
                    Column(Modifier.fillMaxWidth().padding(vertical = Space.Section)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = Space.Section), contentAlignment = Alignment.Center) {
                            app.siansa.ui.PlayButton(playing = false, slides = app.siansa.ui.PlaySlides(), djMix = true, onToggle = {}, onStop = {}, onSlide = {})
                        }
                        Box(Modifier.fillMaxWidth().padding(top = Space.Large * 3, bottom = Space.Section), contentAlignment = Alignment.Center) {
                            app.siansa.ui.PlayButton(
                                playing = true, slides = app.siansa.ui.PlaySlides(), djMix = false, onToggle = {}, onStop = {}, onSlide = {},
                                preview = app.siansa.ui.SlideState(app.siansa.ui.SlideWay.UP, armed = true),
                            )
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = Space.Section, horizontal = Space.Large), contentAlignment = Alignment.CenterEnd) {
                            app.siansa.ui.PlayButton(
                                playing = true, slides = app.siansa.ui.PlaySlides(), djMix = false, onToggle = {}, onStop = {}, onSlide = {},
                                preview = app.siansa.ui.SlideState(app.siansa.ui.SlideWay.LEFT, armed = false),
                            )
                        }
                    }
                }
            },
            // A slide's choice in the middle of the cover (issue #933): far enough to start, not yet, and one that can't run.
            Scene("slide_label_in_cover") {
                OnPlayer {
                    Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                        listOf(
                            app.siansa.ui.SlideLabel(app.siansa.ui.PlaySlide.DJ_MIX, app.siansa.ui.SlideWay.UP, armed = true),
                            app.siansa.ui.SlideLabel(app.siansa.ui.PlaySlide.SONIC_MATCH, app.siansa.ui.SlideWay.RIGHT, armed = false),
                            app.siansa.ui.SlideLabel(app.siansa.ui.PlaySlide.SONG_PATH, app.siansa.ui.SlideWay.LEFT, armed = false, why = "Needs AudioMuse"),
                        ).forEach { label ->
                            Box(
                                Modifier.size(220.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFB0476A), Color(0xFF3A1D6E)))),
                            ) { app.siansa.ui.SlideCoverLabel(label, Modifier.fillMaxSize()) }
                        }
                    }
                }
            },
            // The DJ mix bar (issues #1033, #1035), the seek bar's width: opening out of the seek bar's line; open with no
            // mix playing (Start in the middle, then on Start); while a mix plays (Euphoric now), on Steady build and on
            // Stop; and an album's DJ mix, only Stop.
            Scene("dj_bar") {
                OnPlayer {
                    Column(Modifier.padding(horizontal = Space.Section, vertical = Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                        val all = app.siansa.ui.DjBarSpot.default
                        val now = app.siansa.ui.DjBarSpot.EUPHORIC
                        app.siansa.ui.DjBar(all, chosen = null, now = null, starting = true, open = 0.4f)
                        app.siansa.ui.DjBar(all, chosen = null, now = null, starting = true, open = 1f)
                        app.siansa.ui.DjBar(all, chosen = 2, now = null, starting = true, open = 1f)
                        app.siansa.ui.DjBar(all, chosen = 1, now = now, starting = false, open = 1f)
                        app.siansa.ui.DjBar(all, chosen = 2, now = now, starting = false, open = 1f)
                        app.siansa.ui.DjBar(listOf(app.siansa.ui.DjBarSpot.MIX), chosen = 0, now = null, starting = false, open = 1f)
                    }
                }
            },
            // The cover while on the DJ mix bar (issue #1035): on Start with no mix playing, and on Stop while one plays.
            Scene("dj_bar_cover") {
                OnPlayer {
                    Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                        val all = app.siansa.ui.DjBarSpot.default
                        listOf(true, false).forEach { starting ->
                            val label = app.siansa.ui.SlideLabel(
                                app.siansa.ui.PlaySlide.DJ_MIX, app.siansa.ui.SlideWay.UP, armed = true,
                                bar = app.siansa.ui.DjBarView(all, chosen = 2, starting = starting, rise = 1f),
                            )
                            Box(
                                Modifier.size(300.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2FA39A), Color(0xFF3A1D6E)))),
                            ) { app.siansa.ui.SlideCoverLabel(label, Modifier.fillMaxSize()) }
                        }
                    }
                }
            },
            // Settings › Style › Home: how many shortcuts Home shows (issue #934).
            Scene("home_style_shortcuts") {
                Column(Modifier.padding(vertical = Space.Page)) { app.siansa.ui.ShortcutCountRow(6) {} }
            },
            // Arrange Home's switch for a section's title (issue #961), on and off.
            Scene("home_section_title_switch") {
                Column(Modifier.padding(Space.Page)) {
                    app.siansa.ui.SectionTitleSwitch(true) {}
                    app.siansa.ui.SectionTitleSwitch(false) {}
                }
            },
            // An album's details with "Continue from 8", and its bar (issue #965, the owner's layout): at rest; continuing (a ring
            // round Play); sliding shuffle onto Play (the rest dims, Play a record); while the album's DJ mix plays; and DJ mix
            // on the cover, on Play and before.
            // An artist's bar (the owner, 9 Nov): the heart on the left, in line with the details; Radio, Shuffle and Play
            // on the right. Not a favourite, a favourite, and an artist without songs of their own.
            Scene("artist_bar") {
                Column(Modifier.padding(Space.Page)) {
                    androidx.compose.material3.Text("12 releases · 140 songs")
                    listOf(false, true).forEach { on ->
                        app.siansa.ui.ArtistButtons(
                            heart = { m -> app.siansa.ui.FavouriteHeart(on, m, MaterialTheme.colorScheme.onSurface, 28.dp) {} },
                            onRadio = {}, onShuffle = {}, playing = false, onPlay = {},
                        )
                    }
                    app.siansa.ui.ArtistButtons(
                        heart = { m -> app.siansa.ui.FavouriteHeart(false, m, MaterialTheme.colorScheme.onSurface, 28.dp) {} },
                        onRadio = {}, onShuffle = null, playing = false, onPlay = null,
                    )
                }
            },
            // Dynamic colours on pages (issue #1028): Radio, Shuffle and Play in the cover's colour, on the page as it is in
            // light and dark, for a bright yellow title, a rust cover and a black and white one (silver, or graphite in light).
            Scene("page_colours") {
                val surface = MaterialTheme.colorScheme.surface
                Column(Modifier.padding(vertical = Space.Page)) {
                    listOf(Color(0xFFE8E21E), Color(0xFFAD6B41), Color(0xFF808080)).forEach { seed ->
                        val page = app.siansa.ui.pageBackdrop(surface, Color(0xFF3A2A20))
                        val colours = app.siansa.ui.DynamicAccent.of(seed, page)!!
                        Box(Modifier.fillMaxWidth().background(page).padding(horizontal = Space.Page)) {
                            app.siansa.ui.WithAccent(colours.controls, colours.onControls) {
                                app.siansa.ui.ArtistButtons(
                                    heart = { m -> app.siansa.ui.FavouriteHeart(false, m, MaterialTheme.colorScheme.onSurface, 28.dp) {} },
                                    onRadio = {}, onShuffle = {}, playing = false, onPlay = {},
                                )
                            }
                        }
                    }
                }
            },
            // An artist's release types as tabs at the top of their page (issue #1029): All chosen; Singles chosen; every
            // type with DJ-mixes chosen, scrolled into view and fading at the edges; and pinned under the title bar, white on
            // its colour.
            Scene("artist_tabs") {
                val few = listOf(app.siansa.data.AlbumCategory.ALBUM, app.siansa.data.AlbumCategory.EP, app.siansa.data.AlbumCategory.SINGLE)
                val all = app.siansa.data.AlbumCategory.entries.toList()
                Column(Modifier.padding(vertical = Space.Page)) {
                    app.siansa.ui.ReleaseTypeTabs(few, null) {}
                    app.siansa.ui.ReleaseTypeTabs(few, app.siansa.data.AlbumCategory.SINGLE) {}
                    app.siansa.ui.ReleaseTypeTabs(all, app.siansa.data.AlbumCategory.DJMIX) {}
                    Box(Modifier.fillMaxWidth().background(Color(0xFF3A2A20))) {
                        app.siansa.ui.ReleaseTypeTabs(all, null, content = Color.White) {}
                    }
                }
            },
            // An artist's name on their page (the owner, 10 Nov): at rest on the picture, riding up with the page, halfway into
            // the title bar (shrinking, sideways past the back arrow), and settled there as the bar's title. A long name too.
            Scene("artist_name_glide") {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val hero = 150.dp
                val heroPx = with(density) { hero.toPx() }
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    listOf(
                        "Joy Orbison" to 0f, "Joy Orbison" to heroPx * 0.25f, "Joy Orbison" to heroPx * 0.62f,
                        "Joy Orbison" to heroPx * 2f, "Tayo Meets Acid Rockers Uptown" to heroPx * 0.62f,
                    ).forEach { (name, scrolled) ->
                        Box(Modifier.fillMaxWidth().height(170.dp)) {
                            app.siansa.ui.HeroPage(
                                title = name, heroHeight = hero, scrolledPx = scrolled, onBack = {}, color = Color(0xFF4A2A26),
                                image = { m -> Box(m.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF7A5A4C), Color(0xFF26171A))))) },
                            ) { page -> Box(page.fillMaxSize()) }
                        }
                    }
                }
            },
            // The lyrics button under the player's controls: at rest, greyed out without lyrics, and lit while the lyrics
            // are open, when it closes them (the owner, 9 Nov).
            Scene("lyrics_button") {
                OnPlayer {
                    Row(Modifier.background(Color(0xFF2A1F3D)).padding(Space.Page), horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
                        app.siansa.ui.LyricsButton(enabled = true) {}
                        app.siansa.ui.LyricsButton(enabled = false) {}
                        app.siansa.ui.LyricsButton(enabled = true, open = true) {}
                    }
                }
            },
            Scene("album_bar") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                    app.siansa.ui.CollectionByline("Thornhill", null, null, "Album · 2019", note = "Continue from 8")
                    val download: @Composable () -> Unit = { androidx.compose.material3.Icon(Icons.Rounded.DownloadForOffline, "Download", Modifier.size(28.dp)) }
                    app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = download, onSimilar = {}, onShuffle = {}, onPlay = {}, onDjMix = {}, albumBar = true, onAddToPlaylist = {})
                    app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = download, onSimilar = {}, onShuffle = {}, onPlay = {}, onDjMix = {}, albumBar = true, onAddToPlaylist = {},
                        resume = app.siansa.ui.PlayResume(9, "10") to 24)
                    app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = download, onSimilar = {}, onShuffle = {}, onPlay = {}, onDjMix = {}, albumBar = true, onAddToPlaylist = {},
                        djSlide = app.siansa.ui.DjSlide(armed = true), dim = 0.3f)
                    app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = download, onSimilar = {}, onShuffle = {}, onPlay = {}, onDjMix = {}, albumBar = true, onAddToPlaylist = {},
                        mixOn = true)  // paused: a turning glint never lets the picture settle
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
                        listOf(true, false).forEach { armed ->
                            Box(
                                Modifier.size(160.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFD4813A), Color(0xFF1E2A6B)))),
                            ) {
                                app.siansa.ui.SlideCoverLabel(
                                    app.siansa.ui.SlideLabel(app.siansa.ui.PlaySlide.DJ_MIX, app.siansa.ui.SlideWay.RIGHT, armed), Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            },
            // Now playing's dynamic colours (issue #970): a seek bar and Play in each cover's colour, on its backdrop; a cool
            // grey cover, a sepia one and a black and white one (issue #994) are a blueish silver, tan and silver.
            Scene("dynamic_colours") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    listOf(
                        Color(0xFFB2CE05) to Color(0xFFB8C64F), Color(0xFF09A2E5) to Color(0xFF203440), Color(0xFFAD6B41) to Color(0xFF6A4E31),
                        Color(0xFF6A4744) to Color(0xFFCDC9C7), Color(0xFFE9E1CF) to Color(0xFFF4F1EA), Color(0xFF808080) to Color(0xFF808080),
                        Color(0xFFB8BCC4) to Color(0xFF9A9CA4), Color(0xFF947D72) to Color(0xFF4A3F3A), Color(0xFF7F7F7F) to Color(0xFF1C1C1C),
                    ).forEach { (seed, average) ->
                        val backdrop = app.siansa.ui.DynamicAccent.backdropOf(average)
                        val c = app.siansa.ui.DynamicAccent.of(seed, backdrop)
                        Row(
                            Modifier.fillMaxWidth().background(backdrop).padding(Space.Medium),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
                        ) {
                            app.siansa.ui.WithAccent(c?.controls, c?.onControls) {
                                Box(Modifier.weight(1f).height(3.dp).background(app.siansa.ui.Accent))
                                Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(app.siansa.ui.Accent), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, null, tint = app.siansa.ui.OnAccent)
                                }
                            }
                            app.siansa.ui.WithAccent(c?.text, c?.onControls) { Text("Living room TV", color = app.siansa.ui.Accent) }
                        }
                    }
                }
            },
            // Settings › Style › Now playing: the play button slides, each way and what it does.
            Scene("play_slides_settings") {
                Column(Modifier.padding(Space.Page)) {
                    app.siansa.ui.PlaySlidesSettings(app.siansa.ui.PlaySlides(left = app.siansa.ui.PlaySlide.START_A_MIX)) {}
                }
            },
            // Song radio's choices (issue #916): an icon and the name each, no descriptions; and the mixing docs link under
            // Crossfade and levelling.
            Scene("radio_choices_and_mixing_docs") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    app.siansa.ui.RadioModeChoices {}
                    // Without Siansa Beats a DJ mix isn't offered: greyed, with why (the owner, 10 Nov).
                    app.siansa.ui.RadioModeChoices("Needs Siansa Beats") {}
                    app.siansa.ui.MixingDocsLink()
                }
            },
            // The Settings start page (the owner, 13 Nov): you and your server, the services with their dots, and the
            // first group of tiles; a long server name and a service that doesn't answer.
            Scene("settings_start") {
                val s = app.siansa.data.AppSettings(
                    navidromeUrl = "https://music.home.example", navidromeUser = "maximilian-the-longest-login", navidromePassword = "x",
                    beatsUrl = "http://beats:8090", audiobookshelfUrl = "http://abs", audiobookshelfToken = "t",
                    lastFmEnabled = true, lastFmApiKey = "k", lastFmSecret = "s", lastFmUser = "max",
                    listenBrainzEnabled = true, listenBrainzToken = "lb",
                )
                val health = mapOf(
                    app.siansa.ui.ServiceHealth.SERVER to app.siansa.ui.ServiceCheck(true, "Connected to Navidrome 0.64.2"),
                    "beats" to app.siansa.ui.ServiceCheck(true, "Siansa Beats 0.40.1: 46,033 songs analysed"),
                    "books" to app.siansa.ui.ServiceCheck(true, "Connected to Audiobookshelf 2.19"),
                    "lastfm" to app.siansa.ui.ServiceCheck(false, "Not signed in · 12 listens waiting"),
                    "listenbrainz" to app.siansa.ui.ServiceCheck(true, "Set up"),
                )
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    app.siansa.ui.YouCard(
                        s,
                        listOf(app.siansa.data.UserEntry(0, "maximilian-the-longest-login", true), app.siansa.data.UserEntry(1, "Kids", true, serverName = "Kids on the Unbelievably Long Server Name")),
                        app.siansa.data.LibraryStatus(1_762_000_000_000L, 2_100, 5_300, 46_033),
                        app.siansa.data.SyncProgress(),
                        health[app.siansa.ui.ServiceHealth.SERVER],
                    ) {}
                    app.siansa.ui.ServicesCard(s, health) {}
                    app.siansa.ui.HubGroupLabel("Playing")
                    app.siansa.ui.HubTiles(listOf(
                        app.siansa.ui.HubTile(androidx.compose.material.icons.Icons.Rounded.Equalizer, "Sound", "a"),
                        app.siansa.ui.HubTile(androidx.compose.material.icons.Icons.Rounded.Schedule, "Radios & mixes", "b"),
                        app.siansa.ui.HubTile(androidx.compose.material.icons.Icons.Rounded.SwapVert, "Streaming & storage", "c"),
                        app.siansa.ui.HubTile(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.MenuBook, "Audiobooks & podcasts", "d"),
                    )) {}
                }
            },
            // Settings › Services' "For the best Siansa" (the owner, 13 Nov): every kind of line, with Siansa Beats on and
            // AudioMuse-AI on too, so it offers to turn AudioMuse-AI off.
            Scene("settings_services_best") {
                val s = app.siansa.data.AppSettings(
                    navidromeUrl = "https://music.home.example", navidromeUser = "max", navidromePassword = "x",
                    beatsUrl = "http://beats:8090", audioMuseUrl = "http://am:8000", audiobookshelfUrl = "http://abs", audiobookshelfToken = "t",
                    lastFmEnabled = true, lastFmApiKey = "k", lastFmSecret = "s", lastFmUser = "max",
                )
                val health = mapOf("lastfm" to app.siansa.ui.ServiceCheck(false, "Not signed in · 12 listens waiting"))
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    app.siansa.ui.SettingsCard(tag = "best") {
                        app.siansa.ui.HubGroupLabel("FOR THE BEST SIANSA")
                        app.siansa.ui.bestLines(s, health).forEach { line ->
                            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                                Box(Modifier.width(Space.Section), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text(when (line.mark) { "ok" -> "✓"; "bad" -> "✕"; "warn" -> "!"; "dash" -> "–"; else -> "○" }, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                                Text(line.words, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            // Stations and podcast feeds in Your Library (issue #353): rows with their ⋮ menu, long names cut off.
            Scene("stations_list") {
                Column {
                    app.siansa.ui.LibraryStationRowForTest("Radio Paradise", "Radio · From your server", "server-1")
                    app.siansa.ui.LibraryStationRowForTest("Unbelievably Long Community Radio Station Name for Rainy Evenings", "Radio · On this phone", "own-2")
                    app.siansa.ui.LibraryStationRowForTest("Rocket Hour", "Podcast · Ada Lovelace · 479 episodes", "pod-3", podcast = true)
                }
            },
            // A podcast's page (issue #353): its header and episodes, one playing, a long title on two lines.
            Scene("podcast_page") {
                Column {
                    app.siansa.ui.PodcastHeader("The Extraordinarily Long Chronicles of Wandering Mapmakers", "Ada Lovelace and Friends", null, "pod-1", 479)
                    app.siansa.ui.EpisodeRow("Ep. 479 · Exploring the Dopamine Chamber with Uncharacteristically Long Words", "5 Mar 2024 · 58 min", playing = true) {}
                    app.siansa.ui.EpisodeRow("Ep. 478 · Short one", "4 Mar 2024 · 1 h 2 min", playing = false) {}
                }
            },
            // Adding a station or podcast (issue #353): the fields with their hint, then with an error.
            Scene("add_station") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    app.siansa.ui.AddStationFields("https://somafm.com/groovesalad.pls", "", busy = false, error = null, hint = app.siansa.ui.AddKind.STATION.hint, onLink = {}, onName = {}, onDone = {})
                    app.siansa.ui.AddStationFields("https://example.com/", "Mine", busy = false, error = "That link isn't a radio stream or a podcast feed", hint = app.siansa.ui.AddKind.PODCAST.hint, onLink = {}, onName = {}, onDone = {})
                }
            },
            // Editing a station (issue #353): one added on this phone with long words and a picture link, then one from
            // the server, whose name and link can't be changed here.
            Scene("edit_station") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    app.siansa.ui.AddStationFields(
                        "http://mp3channels.webradio.rockantenne.de/heavy-metal-with-an-unbelievably-long-address", "Rock Antenne Heavy Metal Unbelievably Long Station Name",
                        busy = false, error = null, hint = app.siansa.ui.editHint(false),
                        picture = "https://www.rockantenne.de/logos/station-heavy-metal/apple-touch-icon.png", onLink = {}, onName = {}, onDone = {},
                    )
                    app.siansa.ui.AddStationFields(
                        "https://stream.radioparadise.com/aac-320", "Radio Paradise", busy = false, error = null, hint = app.siansa.ui.editHint(true),
                        picture = "", fixed = true, onLink = {}, onName = {}, onDone = {},
                    )
                }
            },
            // Now playing for a live station (issue #353): no seek bar, only that it's live.
            Scene("player_live") {
                OnPlayer { Box(Modifier.padding(horizontal = Space.Page, vertical = Space.Medium)) { app.siansa.ui.LiveBar() } }
            },
            // The shortcut grid look for any card or Home row, and its size chips (the owner, 10 Nov).
            Scene("tile_grid") {
                Column {
                    app.siansa.ui.TileGrid(
                        "Recently played with a long title",
                        listOf("Halftime radio", "Supercalifragilistic expialidocious", "Liked songs", "Happy").mapIndexed { i, t ->
                            app.siansa.ui.GridTile("t$i", t, null, Color(0xFF7A1F2B) to Color(0xFF2B5FD6), icon = if (i == 0) Icons.Rounded.PlayArrow else null, onClick = {})
                        },
                        4,
                    )
                    app.siansa.ui.GridSizeChips(6, Modifier.padding(Space.Page)) {}
                }
            },
            // A page's Play in the title bar once its own has scrolled away, and a radio's (the owner, 10 Nov).
            Scene("bar_play") {
                Row(Modifier.background(Color(0xFF7A1F2B)).padding(Space.Page), horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
                    app.siansa.ui.BarPlayButton(radio = false, colour = Color(0xFFFFB547), onColour = Color(0xFF1A1200), playing = false) {}
                    app.siansa.ui.BarPlayButton(radio = false, colour = Color(0xFF9DB4C8), onColour = Color(0xFF120D00), playing = true) {}
                    app.siansa.ui.BarPlayButton(radio = true, colour = Color(0xFFE53935), onColour = Color.White, playing = false) {}
                }
            },
            // A list's sort (liked songs, playlists): Your Library's arrows and menu, not chips; and the line under a
            // drawn cover with its heart and New picks, the same on radios, mood playlists and cards (the owner, 10 Nov).
            Scene("song_sort_and_picks") {
                Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    Column {
                        app.siansa.ui.CollectionByline(null, null, null, "Playlist · 100 songs · 6:41:38")
                        app.siansa.ui.SongSortPicker(app.siansa.data.ListSort.DEFAULT, "Playlist order") {}
                    }
                    app.siansa.ui.PicksRow("Made for you · supercalifragilistic expialidocious sounds · 30 songs", onNewPicks = {})
                    app.siansa.ui.PicksRow("Your card · 30 songs", onNewPicks = null)
                }
            },
            // A genre's description: three lines and "Show more", or all of a short one (the owner, 10 Nov).
            Scene("genre_about") {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    app.siansa.ui.GenreAboutText(
                        "Indie pop is a music genre and subculture that combines guitar pop with a DIY ethic in opposition to the " +
                            "style and tone of mainstream pop music. It originated from British post-punk in the late 1970s.",
                        "Wikipedia", onSource = {},
                    )
                    app.siansa.ui.GenreAboutText("Kayōkyoku is Japanese popular music of the Shōwa era.", "Wikipedia", onSource = null)
                }
            },
            // The time machine: its title (tap it for the years), what was played, the months, and the big numbers in the
            // listening pages' one faint tile. A long artist name is cut, not wrapped.
            Scene("time_machine_header") {
                Column(Modifier.background(Color(0xFF111111)).padding(vertical = Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                    Column {
                        app.siansa.ui.TimeMachineName()
                        app.siansa.ui.TimePicker(
                            2025, 10, java.time.LocalDate.of(2026, 11, 12), onYear = {}, onMonth = {},
                            under = { app.siansa.ui.TimeMachineWords("1,461 plays · 94 hours", "Most played: The Presidents of the United States of America and friends") },
                        )
                    }
                    app.siansa.ui.StatTiles(
                        app.siansa.mix.ListeningReport(
                            title = "", subtitle = "", minutes = 5699, plays = 1461, artistCount = 534, trackCount = 1176, newArtists = 223,
                            topArtists = emptyList(), topTracks = emptyList(), topAlbums = emptyList(), byHour = IntArray(24),
                            rising = emptyList(), fading = emptyList(), genresUp = emptyList(), genresDown = emptyList(), source = "",
                        ),
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
