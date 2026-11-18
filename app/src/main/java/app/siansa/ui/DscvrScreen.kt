package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.SavedVibe
import app.siansa.log.AppLog
import app.siansa.mix.MixOffer
import app.siansa.mix.VibeTuning
import app.siansa.mix.FlowMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dscvr's mark (issue #339): the sparkle of stars that phones use for AI, one large four-pointed star with two
 * small ones. Drawn rather than taken from the icon set, so it stays apart from the small "AI named" mark.
 */
val DscvrStars: ImageVector by lazy {
    ImageVector.Builder("DscvrStars", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) { sparkle(10.5f, 13.5f, 8.5f) }
        path(fill = SolidColor(Color.Black)) { sparkle(19f, 5f, 3.6f) }
        path(fill = SolidColor(Color.Black)) { sparkle(19.5f, 18.5f, 2.4f) }
    }.build()
}

/** A four-pointed star around ([cx], [cy]) with points [r] out, its sides curving in. */
private fun androidx.compose.ui.graphics.vector.PathBuilder.sparkle(cx: Float, cy: Float, r: Float) {
    val k = r * STAR_WAIST
    moveTo(cx, cy - r)
    quadTo(cx + k, cy - k, cx + r, cy)
    quadTo(cx + k, cy + k, cx, cy + r)
    quadTo(cx - k, cy + k, cx - r, cy)
    quadTo(cx - k, cy - k, cx, cy - r)
    close()
}

/** How far the star's sides bow in: 0 is a thin cross, 0.5 a plain diamond. */
private const val STAR_WAIST = 0.22f

/** A vibe being made, so the buttons show it's busy even after leaving the page and coming back. */
private object VibeMaking {
    val busy = MutableStateFlow(false)
}

/**
 * Makes a vibe in the background and opens its page when it's ready (issue #339): from [words] (the AI's plan,
 * or the words searched as they are) and the mood and picks in [with]; with no words, from those alone. Shared
 * by Flow and Search's "Make a vibe from …". [notify] shows what went wrong; [onMade] runs once its page is open.
 */
internal fun makeVibe(
    graph: app.siansa.AppGraph,
    nav: NavController,
    notify: (String) -> Unit,
    words: String,
    with: VibeTuning,
    onMade: (SavedVibe) -> Unit = {},
) {
    if (VibeMaking.busy.value) return
    VibeMaking.busy.value = true
    graph.scope.launch {
        try {
            val plan = graph.generator.vibePlan(words, with)
            // A typed vibe starts from the AI's own sounds; the mood and the sounds and genres picked go over them.
            val start = if (words.isBlank()) with else FlowMood.over(VibeTuning.start(plan.steering, emptyList()), with)
            val songs = graph.generator.vibeSongs(plan.query, plan.steering, start)
            val saved = graph.savedVibes.save(
                SavedVibe(
                    id = "",
                    text = words.trim(),
                    title = plan.title,
                    description = plan.description,
                    songs = songs,
                    madeAt = System.currentTimeMillis(),
                    query = plan.query,
                    steered = plan.steering,
                    tuning = start,
                    ai = plan.ai,
                ),
            )
            AppLog.i("Vibe", "Made \"${saved.title}\" (${songs.size} songs)")
            withContext(Dispatchers.Main) {
                nav.navigate("vibe/${saved.id}")
                onMade(saved)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Vibe", "Couldn't make the vibe: ${e.message}")
            withContext(Dispatchers.Main) { notify(e.message ?: "Couldn't make the vibe") }
        } finally {
            VibeMaking.busy.value = false
        }
    }
}

/** True while a vibe is being made. */
@Composable
internal fun vibeMakingBusy(): Boolean = VibeMaking.busy.collectAsStateWithLifecycle().value

/**
 * Flow (issue #339, renamed in #369; the mood pad, the owner's pick of 15 Nov): say what you want to hear, or shape
 * it: drag the pad's dot for the mood, pick sounds and genres with +, choose whose music. The page takes the mood's
 * colour, and the arrow beside the words makes the vibe and plays it. Recent vibes below (the last five, plus
 * pinned ones), each one tap from playing again, and each can be renamed or deleted.
 */
@Composable
fun DscvrScreen(nav: NavController) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val aiProblem = rememberAiProblem()
    val flow = graph.flow
    // What's set lives with the mix (FlowMix), so it's still here after a look at another tab, and steers a mix playing.
    val text by flow.text.collectAsStateWithLifecycle()
    val tuning by flow.tuning.collectAsStateWithLifecycle()
    val steering by flow.steering.collectAsStateWithLifecycle()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val mix = flow.live(player)
    val hold = remember { MoodHold() }
    val look = rememberMoodLook(tuning, hold)
    val vibes by graph.savedVibes.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.savedVibes.all() }
    var renaming by remember { mutableStateOf<SavedVibe?>(null) }

    // One player (the owner, 15 Nov): nothing of Flow's own stands in the mini player's place. The arrow beside the
    // words starts the mix, with or without words, and puts the keyboard away.
    val focus = LocalFocusManager.current

    renaming?.let { vibe ->
        RenameVibeDialog(vibe, onDismiss = { renaming = null }) { name ->
            renaming = null
            graph.scope.launch { graph.savedVibes.rename(vibe.id, name) }
        }
    }

    Box(Modifier.fillMaxSize().testTag("flowPage")) {
        MoodGlow(look)
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().aboveKeyboard(),
            contentPadding = PaddingValues(top = Space.Page, bottom = bottomBarPadding(Space.Section)),
        ) {
            item(key = "top") {
                Column(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Small)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Flow", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).testTag("flowTitle"))
                        if (aiProblem == null) {
                            TextButton(onClick = { flow.reset() }, enabled = !tuning.isNeutral || text.isNotEmpty(), modifier = Modifier.testTag("flowReset")) {
                                Text("Reset")
                            }
                        }
                    }
                    // While its mix plays, the page says so: what's moved here changes what comes next.
                    if (mix != null) {
                        Text(
                            flowLiveLine(mix.name, steering),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = Space.Small).testTag("flowLive"),
                        )
                    }
                }
            }
            if (aiProblem != null) {
                // Nothing to shape while the sound search can't be reached: it would only make an empty vibe.
                item(key = "problem") { AiUnavailable(aiProblem, Modifier.padding(horizontal = Space.Page, vertical = Space.Medium)) }
            } else {
                item(key = "ask") {
                    val making by flow.making.collectAsStateWithLifecycle()
                    AskPill(
                        text, onText = { flow.text.value = it }, look = look, busy = making,
                        onGo = { focus.clearFocus(); flow.play(launch::notify) }, modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Small),
                    )
                }
                item(key = "shape") {
                    // The dot moving only sets what's shown; a finger lifted, a pick or Artists changed also steers a mix playing.
                    MoodShaper(
                        tuning, look, hold,
                        onChange = { flow.set(it, settled = false) },
                        onSettled = { flow.set(flow.tuning.value, settled = true) },
                        // The owner, 15 Nov: say right under the pad that it also steers a vibe that's playing.
                        padNote = FLOW_PAD_NOTE,
                        modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Medium),
                    )
                }
            }
            val recent = vibes.orEmpty()
            if (recent.isNotEmpty()) {
                item(key = "recent-title") { SectionTitle("Your vibes") }
                items(recent, key = { "vibe-" + it.id }) { vibe ->
                    VibeRow(
                        vibe,
                        onOpen = { nav.navigate("vibe/${vibe.id}") },
                        onPin = { graph.scope.launch { graph.savedVibes.setPinned(vibe.id, !vibe.pinned) } },
                        onRename = { renaming = vibe },
                        onDelete = {
                            AppLog.i("Vibe", "Deleted \"${vibe.shownTitle}\"")
                            graph.scope.launch { graph.savedVibes.remove(vibe.id) }
                        },
                    )
                }
            }
        }
    }
}

/** Right under Flow's pad. */
internal const val FLOW_PAD_NOTE = "Drag while it plays to change the vibe."

/** Under Flow's title while its mix plays: which mix the page is steering. */
internal fun flowLiveLine(name: String, steering: Boolean): String =
    "Playing “$name”. " + if (steering) "Changing what comes next…" else "Move anything here to change what comes next."

/**
 * The mood's colour glowing down from the top of the page, behind everything: all of it at the top, none at its end,
 * on [GlassBar.colour]'s curve in many dithered steps under the same grain as the title bar's wash, so it shows no
 * lines (the owner, 15 Nov: a plain radial fade did).
 */
@Composable
private fun MoodGlow(look: MoodLook) {
    val dark = darkPage()
    Box(Modifier.fillMaxWidth().height(MOOD_GLOW).testTag("moodGlow")) {
        GlassWash(if (dark) look.deep else look.colour, MOOD_GLOW, amount = { if (dark) 0.85f else 0.22f })
    }
}

private val MOOD_GLOW = 320.dp

/**
 * "Or say it" (issue #369): the words for a vibe in one pill, and the round arrow at its end that plays the vibe, as
 * does the keyboard's Go. The arrow is how a vibe starts, with words or with only the mood and picks (the owner, 15 Nov).
 */
@Composable
private fun AskPill(text: String, onText: (String) -> Unit, look: MoodLook, busy: Boolean, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
            .padding(start = Space.Medium, end = Space.Tight, top = Space.Tight, bottom = Space.Tight)
            .testTag("auraBox"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        Icon(DscvrStars, null, tint = look.colour, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text("Or say it: rainy Sunday, need to focus…", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = text,
                onValueChange = { onText(it.take(200)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!busy) onGo() }),
                modifier = Modifier.fillMaxWidth().testTag("auraField"),
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(look.colour)
                .clickable(enabled = !busy, onClickLabel = "Play this vibe", role = androidx.compose.ui.semantics.Role.Button, onClick = onGo)
                .testTag("auraGo"),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = look.ink, strokeWidth = 2.dp) // spacing: exact
            else Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Play this vibe", tint = look.ink, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * A recent vibe: tap to open it, the pin to show it on Home (as an option, issue #339), and ⋮ to rename or
 * delete it (issue #369).
 */
@Composable
private fun VibeRow(vibe: SavedVibe, onOpen: () -> Unit, onPin: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val colors = tileColors(vibe.text.ifEmpty { vibe.title })
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = Space.Page, end = Space.Tight, top = Space.Small, bottom = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GeneratedCover(vibe.shownTitle, colors, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(vibe.shownTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(vibe.text.takeIf { it.isNotEmpty() }?.let { "“$it”" }, "${vibe.songs.size} songs").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPin) {
            Icon(
                if (vibe.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                if (vibe.pinned) "Unpin from Home" else "Pin to Home",
                tint = if (vibe.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Gives a vibe a name of your own (issue #369); an empty name goes back to the one it was made with. */
@Composable
internal fun RenameVibeDialog(vibe: SavedVibe, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(vibe.id) { mutableStateOf(vibe.shownTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename vibe") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("renameVibe"),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A vibe from Dscvr: its songs, Save as playlist, Pin to Home, and the sliders to tune it again (issue #339). */
@Composable
fun VibeScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val vibes by graph.savedVibes.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.savedVibes.all() }
    val vibe = vibes?.firstOrNull { it.id == id }
    var tuning by remember(id) { mutableStateOf<VibeTuning?>(null) }
    var tuneOpen by rememberSaveable { mutableStateOf(false) }
    // What this mix's own songs have to offer: its sounds for the dials, and each song's place on the pad.
    var offer by remember(id) { mutableStateOf<MixOffer?>(null) }
    val songIds = vibe?.songs?.map { it.id }
    LaunchedEffect(songIds, tuneOpen) {
        val v = vibe ?: return@LaunchedEffect
        if (!tuneOpen || v.songs.isEmpty()) return@LaunchedEffect
        offer = graph.generator.mixOffer(v.songs)
    }
    var retuning by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedAs by rememberSaveable { mutableStateOf<String?>(null) }
    // What's picked: the AI's own sounds and mine, never a fixed list (the owner, 11 Nov; picked with +, 15 Nov).
    val shown = tuning ?: vibe?.tuning ?: VibeTuning()
    val hold = remember { MoodHold() }
    val look = rememberMoodLook(shown, hold)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (renaming && vibe != null) {
        RenameVibeDialog(vibe, onDismiss = { renaming = false }) { name ->
            renaming = false
            scope.launch { graph.savedVibes.rename(vibe.id, name) }
        }
    }

    fun retune(with: VibeTuning) {
        val v = vibe ?: return
        if (with.set == v.tuning.set) return  // the same dials set the same: nothing to search again
        retuning = true
        scope.launch {
            try {
                val songs = graph.generator.vibeSongs(v.query, v.steered, with)
                graph.savedVibes.save(v.copy(songs = songs, tuning = with, madeAt = System.currentTimeMillis()))
                savedAs = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Vibe", "Couldn't tune \"${v.title}\": ${e.message}")
                launch.notify(e.message ?: "Couldn't tune the vibe")
            } finally {
                retuning = false
            }
        }
    }

    val colors = tileColors(vibe?.text?.ifEmpty { vibe.title } ?: id)
    CollectionPage(
        nav = nav,
        title = vibe?.shownTitle ?: if (vibes == null) "Loading…" else "Vibe not found",
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = if (vibes == null) null else vibe?.songs.orEmpty(),
        error = if (vibes != null && vibe == null) "This vibe is no longer kept" else null,
        cover = { m -> GeneratedCover(vibe?.shownTitle ?: "", colors, m) },
        // Its name is on its cover, and Play takes the cover's colour, as every page with a drawn cover (the owner, 10 Nov).
        showTitle = false,
        coverColour = colors.first,
        // Rename or delete the vibe (issue #369).
        actions = {
            if (vibe != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menu = false
                        AppLog.i("Vibe", "Deleted \"${vibe.shownTitle}\"")
                        scope.launch { graph.savedVibes.remove(vibe.id) }
                        nav.popBackStack()
                    })
                }
            }
        },
        header = {
            if (vibe != null) {
                Text(vibe.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.Small))
                CollectionByline(
                    null, null, null,
                    listOfNotNull(
                        vibe.text.takeIf { it.isNotEmpty() }?.let { "From “$it”" },
                        "${vibe.songs.size} songs",
                        vibe.tuning.summary().takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                )
                Spacer(Modifier.height(Space.Tight))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !saving && savedAs == null && vibe.songs.isNotEmpty(),
                        onClick = {
                            saving = true
                            scope.launch {
                                runCatching { graph.source.createPlaylist(vibe.shownTitle, vibe.songs.map { it.id }) }
                                    .onSuccess {
                                        savedAs = vibe.shownTitle
                                        AppLog.i("Vibe", "Saved \"${vibe.shownTitle}\" with ${vibe.songs.size} songs as a playlist")
                                        snack.showSnackbar("Saved as playlist “${vibe.shownTitle}”")
                                    }
                                    .onFailure { snack.showSnackbar("Couldn't save the playlist: ${it.message}") }
                                saving = false
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                        Spacer(Modifier.width(Space.Tight))
                        Text(if (savedAs != null) "Saved" else "Save as playlist", fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = { scope.launch { graph.savedVibes.setPinned(vibe.id, !vibe.pinned) } }) {
                        Icon(if (vibe.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, null)
                        Spacer(Modifier.width(Space.Tight))
                        Text(if (vibe.pinned) "On Home" else "Pin to Home", fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { tuneOpen = !tuneOpen }, modifier = Modifier.testTag("vibeTune")) {
                        Icon(Icons.Rounded.Tune, if (tuneOpen) "Hide Shape your sound" else "Shape your sound")
                    }
                }
                if (tuneOpen) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(bottom = Space.Small)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Shape your sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(
                                    enabled = !shown.isNeutral,
                                    onClick = { shown.cleared().let { tuning = it; retune(it) } },
                                    modifier = Modifier.testTag("vibeReset"),
                                ) { Text("Reset") }
                            }
                            // State is written as soon as the dot moves or a pick changes, so reading it then gets the last one.
                            MoodShaper(
                                shown, look, hold,
                                onChange = { tuning = it },
                                onSettled = { tuning?.let { retune(it) } },
                                dots = offer?.dots.orEmpty(),
                                inMix = offer?.sounds.orEmpty(),
                            )
                            if (retuning) {
                                Spacer(Modifier.height(Space.Small))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp) // spacing: exact
                                    Spacer(Modifier.width(Space.Small))
                                    Text("Recalculating…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
