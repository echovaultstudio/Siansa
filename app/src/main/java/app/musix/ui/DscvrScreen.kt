package app.musix.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.SavedVibe
import app.musix.log.AppLog
import app.musix.mix.SoundDial
import app.musix.mix.VibeTuning
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
 * or the words searched as they are) and the sliders in [with]; with no words, from the sliders alone. Shared
 * by Dscvr and Search's "Make a vibe from …". [notify] shows what went wrong.
 */
internal fun makeVibe(graph: app.musix.AppGraph, nav: NavController, notify: (String) -> Unit, words: String, with: VibeTuning) {
    if (VibeMaking.busy.value) return
    VibeMaking.busy.value = true
    graph.scope.launch {
        try {
            val plan = graph.generator.vibePlan(words, with)
            // A typed vibe starts from the AI's own concepts on its dials; the sliders already set carry over.
            val start = if (words.isBlank()) with else VibeTuning.start(plan.steering, graph.generator.soundConcepts())
                .copy(energy = with.energy, tempo = with.tempo, familiar = with.familiar)
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
            withContext(Dispatchers.Main) { nav.navigate("vibe/${saved.id}") }
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
 * Flow (issue #339, renamed in #369): describe what you want to hear and get a vibe; "Shape your sound" opens the
 * optional sliders. Recent vibes below (the last five, plus pinned ones), each one tap from playing again, and
 * each can be renamed or deleted.
 */
@Composable
fun DscvrScreen(nav: NavController) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val aiProblem = rememberAiProblem()
    val busy by VibeMaking.busy.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var tuning by remember { mutableStateOf<VibeTuning?>(null) }
    val vibes by graph.savedVibes.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.savedVibes.all() }
    // The sound dials: concepts AudioMuse knows, filled in once they've loaded.
    LaunchedEffect(aiProblem) {
        if (aiProblem == null && tuning == null) tuning = VibeTuning.start(emptyList(), graph.generator.soundConcepts())
    }
    val current = tuning ?: VibeTuning()
    // The sliders are optional and folded away until opened (issue #369); only the ones moved are used.
    var shaping by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedVibe?>(null) }

    fun make(words: String, with: VibeTuning) = makeVibe(graph, nav, launch::notify, words, with)

    renaming?.let { vibe ->
        RenameVibeDialog(vibe, onDismiss = { renaming = null }) { name ->
            renaming = null
            graph.scope.launch { graph.savedVibes.rename(vibe.id, name) }
        }
    }

    Screen(title = "Flow", nav = null) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().aboveKeyboard(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomBarPadding(Space.Section)),
        ) {
            item(key = "aura") {
                AuraBox(
                    text = text,
                    onText = { text = it },
                    aiProblem = aiProblem,
                    busy = busy,
                    onGo = { make(text, current) },
                    modifier = Modifier.padding(horizontal = Space.Page),
                )
            }
            if (aiProblem == null) {
                item(key = "shape") {
                    Column(Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Section)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { shaping = !shaping }.testTag("shapeToggle"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Shape your sound", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    shapeHint(current),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (shaping) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                if (shaping) "Hide the sliders" else "Show the sliders",
                            )
                        }
                        if (shaping) {
                            Spacer(Modifier.height(Space.Medium))
                            VibeSliders(current, onChange = { tuning = it })
                            Spacer(Modifier.height(Space.Medium))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { tuning = current.cleared() }, enabled = !current.isNeutral) { Text("Reset") }
                                Spacer(Modifier.width(Space.Small))
                                Button(
                                    onClick = { make(text, current) },
                                    enabled = !busy && (text.trim().length >= 3 || !current.isNeutral),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                                    modifier = Modifier.weight(1f).testTag("vibeGenerate"),
                                ) {
                                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = OnAccent, strokeWidth = 2.dp) // spacing: exact
                                    else Icon(DscvrStars, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(Space.Small))
                                    Text(if (busy) "Generating…" else "Generate")
                                }
                            }
                        }
                    }
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

/** "Describe what you want to hear" (issue #369): the words for a vibe, made with the stars button or the keyboard's Go. */
@Composable
private fun AuraBox(
    text: String,
    onText: (String) -> Unit,
    aiProblem: String?,
    busy: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Color(0xFF4B2BD6) to Color(0xFFD6338A)
    val ready = text.trim().length >= 3 && !busy
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(lerp(colors.first, Color.Black, 0.2f), lerp(colors.second, Color.Black, 0.35f))))
            .padding(Space.Page)
            .testTag("auraBox"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(DscvrStars, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.Small))
            Text("Describe what you want to hear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(Space.Small))
        if (aiProblem != null) {
            // Nothing to type into while AudioMuse can't be reached: it would only make an empty vibe.
            AiUnavailable(aiProblem, onDark = true)
            return@Column
        }
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text("e.g. tired but hopeful, rainy Sunday, need to focus", color = Color.White.copy(alpha = 0.6f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (ready) onGo() }),
            trailingIcon = {
                IconButton(onClick = onGo, enabled = ready) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) // spacing: exact
                    else Icon(DscvrStars, "Make my vibe", tint = Color.White)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth().testTag("auraField"),
        )
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

/** Under "Shape your sound": what it's for while nothing is set, otherwise what is (issue #369). */
internal fun shapeHint(tuning: VibeTuning): String =
    if (tuning.isNeutral) "Optional. Only the sliders you move are used."
    else "Using: " + tuning.summary()

/**
 * The tuning sliders (issue #339): energy, tempo, familiar ↔ niche and the sound dials, each with a tick at
 * every step. [onSettled] runs when a slider is let go, so a vibe page can recalculate then (it reads the
 * value [onChange] last gave it, which is already stored by then).
 */
@Composable
fun VibeSliders(tuning: VibeTuning, onChange: (VibeTuning) -> Unit, onSettled: () -> Unit = {}, onDark: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        StepSlider("Calmer", "More energy", tuning.energy, VibeTuning.SLIDER_MAX, onDark, "sliderEnergy",
            onChange = { onChange(tuning.copy(energy = it)) }, onSettled = onSettled)
        StepSlider("Slower", "Faster", tuning.tempo, VibeTuning.SLIDER_MAX, onDark, "sliderTempo",
            onChange = { onChange(tuning.copy(tempo = it)) }, onSettled = onSettled)
        StepSlider("Niche", "Familiar", tuning.familiar, VibeTuning.SLIDER_MAX, onDark, "sliderFamiliar",
            onChange = { onChange(tuning.copy(familiar = it)) }, onSettled = onSettled)
        tuning.sounds.forEachIndexed { i, dial ->
            StepSlider("Less ${dial.term}", "More ${dial.term}", dial.value, VibeTuning.DIAL_MAX, onDark, "sliderSound",
                onChange = { v -> onChange(tuning.copy(sounds = tuning.sounds.toMutableList().also { it[i] = SoundDial(dial.term, v) })) },
                onSettled = onSettled)
        }
    }
}

/** One slider from −[max] to +[max] in whole steps, labelled at both ends, with a haptic tick per step. */
@Composable
private fun StepSlider(
    low: String,
    high: String,
    value: Int,
    max: Int,
    onDark: Boolean,
    tag: String,
    onChange: (Int) -> Unit,
    onSettled: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val text = if (onDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.fillMaxWidth()) {
            Text(low, style = MaterialTheme.typography.labelMedium, color = text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(high, style = MaterialTheme.typography.labelMedium, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val step = Math.round(v).coerceIn(-max, max)
                if (step != value) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(step)
                }
            },
            onValueChangeFinished = onSettled,
            valueRange = -max.toFloat()..max.toFloat(),
            steps = max * 2 - 1,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
    }
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
    var retuning by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedAs by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = tuning ?: vibe?.tuning ?: VibeTuning()
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
        if (with == v.tuning) return
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
                                runCatching { graph.subsonic.createPlaylist(vibe.shownTitle, vibe.songs.map { it.id }) }
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
                        Icon(Icons.Rounded.Tune, if (tuneOpen) "Hide the sliders" else "Tune this vibe")
                    }
                }
                if (tuneOpen) {
                    Box(Modifier.fillMaxWidth()) {
                        Column {
                            // State is written as soon as a slider moves, so reading it when let go gets the last step.
                            VibeSliders(shown, onChange = { tuning = it }, onSettled = { tuning?.let { retune(it) } })
                            if (retuning) {
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
