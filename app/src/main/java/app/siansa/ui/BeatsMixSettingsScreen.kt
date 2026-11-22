package app.siansa.ui

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.log.AppLog
import app.siansa.net.BeatsListens
import app.siansa.net.BeatsMixSettings
import app.siansa.net.BeatsMixSettings.Kind
import app.siansa.net.BeatsMixSettings.Value
import kotlinx.coroutines.launch

/**
 * What goes into your mixes from Siansa Beats (Settings › Mixes › Mix settings; the owner, 17 Nov:
 * "make settings or toggles for everything that is currently automatically chosen"). Kept by Siansa Beats, the same as
 * its own Mixes page shows: switches, numbers on a slider, and genres and artists to leave out. A change is saved at
 * once and Home's mixes are made again.
 */
@Composable
fun BeatsMixSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val s by graph.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<BeatsMixSettings.Settings?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var making by remember { mutableStateOf(false) }
    val listener = BeatsListens.listener(s)
    LaunchedEffect(listener) {
        runCatching { graph.beatsMixSettings.load(listener) }
            .onSuccess { loaded = it; problem = null }
            .onFailure {
                problem = if ((it as? app.siansa.net.ApiException)?.code == 404) "Needs Siansa Beats 0.56 or newer." else "Siansa Beats didn't answer: ${it.message}"
                AppLog.w("Beats", "Mix settings weren't read: ${it.message}")
            }
    }
    fun save(changes: Map<String, Value>, keepDays: Int? = null) {
        val now = loaded ?: return
        loaded = now.copy(values = now.values + changes, keepDays = keepDays ?: now.keepDays)
        note = "Saving…"
        scope.launch {
            runCatching { graph.beatsMixSettings.save(listener, changes, now.settings.associate { it.name to it.kind }, keepDays) }
                .onSuccess {
                    loaded = it
                    note = "Saved. Make the mixes again to see them now."
                    AppLog.i("Beats", "Mix settings: ${changes.keys.joinToString()}${keepDays?.let { d -> ", kept $d days" }.orEmpty()}")
                    graph.beatsMixes.refresh(force = true)
                }
                .onFailure { loaded = now; note = "Not saved: ${it.message}"; AppLog.w("Beats", "Mix settings weren't saved: ${it.message}") }
        }
    }
    Screen(title = "Mix settings", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page)
                .testTag("beatsMixSettings"),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Hint("Siansa Beats makes your mixes by itself, from what you play, the time of day, the season and the light outside. Change only what you want.")
            val d = loaded
            when {
                problem != null -> Text(problem.orEmpty(), color = MaterialTheme.colorScheme.error)
                d == null -> Hint("Loading…")
                else -> MixSettingsBody(d, note, graph.beatsGenres.saved().orEmpty().map { it.name }, ::save,
                    again = ({
                        if (!making) {
                            making = true
                            note = "Making them again…"
                            scope.launch {
                                runCatching { graph.beatsMixSettings.again(listener, java.time.ZoneId.systemDefault().id) }
                                    .onSuccess { times ->
                                        AppLog.i("Beats", "Mixes made again by hand ($times today)")
                                        graph.beatsMixes.refresh(force = true)
                                        note = "Made again: they're on Home"
                                    }
                                    .onFailure {
                                        note = if ((it as? app.siansa.net.ApiException)?.code == 404) "Needs Siansa Beats 0.60.4 or newer." else "Not made again: ${it.message}"
                                        AppLog.w("Beats", "The mixes weren't made again: ${it.message}")
                                    }
                                making = false
                            }
                        }
                    }))
            }
        }
    }
}

/**
 * The settings themselves, as Siansa Beats' own Mix settings page has them (the owner, 22 Nov: "a total revamp…
 * 100% organised and not bloated and easy to understand"): three parts of one line each. A mix's line has its switch
 * and says AUTO while it's as it comes, TUNED once something of it is changed; a tap opens its choices under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MixSettingsBody(
    d: BeatsMixSettings.Settings, note: String?, genres: List<String>, save: (Map<String, Value>, Int?) -> Unit,
    // Makes the mixes again now (null: not shown).
    again: (() -> Unit)? = null,
    // The lines open at first: for the screenshot test.
    opened: Set<String> = emptySet(),
) {
    var open by rememberSaveable { mutableStateOf(opened) }
    Column {
        d.parts().forEachIndexed { index, part ->
            if (index > 0) Spacer(Modifier.height(Space.Small))
            SheetPart(part.title)
            if (part.about.isNotEmpty()) Hint(part.about)
            Spacer(Modifier.height(Space.Small))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = Space.Page)) {
                part.lines.forEachIndexed { at, line ->
                    if (at > 0) SheetRule()
                    val under = d.under(line).filter { shown(it.name, d) }
                    val keeps = line.key == "siansa"
                    val isOpen = line.key in open && (under.isNotEmpty() || keeps)
                    MixLine(line, d, isOpen, opens = under.isNotEmpty() || keeps,
                        onSwitch = { on -> line.on?.let { save(mapOf(it to Value(on = on)), null) } },
                        onOpen = { open = if (line.key in open) open - line.key else open + line.key })
                    if (isOpen) {
                        Column(Modifier.padding(bottom = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                            under.forEach { given ->
                                val setting = given.copy(label = BeatsMixSettings.WORDS[given.name] ?: given.label)
                                val v = d.value(setting.name) ?: setting.default
                                when (setting.kind) {
                                    Kind.ON -> SwitchRow(setting.label, v.on) { save(mapOf(setting.name to Value(on = it)), null) }
                                    Kind.NUMBER -> NumberRow(setting, v.number) { save(mapOf(setting.name to Value(number = it)), null) }
                                    Kind.WORDS -> WordsRow(setting, v.words, if (setting.name == "leave_genres") genres else emptyList()) {
                                        save(mapOf(setting.name to Value(words = it)), null)
                                    }
                                }
                            }
                            if (keeps) {
                                Text("Keep a mix playable for", style = MaterialTheme.typography.bodyLarge)
                                Hint("How long a mix you played still plays from your shortcuts. For every listener.")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                                    KEEP_DAYS.forEach { days ->
                                        FilterChip(selected = d.keepDays == days, onClick = { save(emptyMap(), days) },
                                            label = { Text(if (days == 1) "1 day" else "$days days") }, modifier = Modifier.testTag("mixKeep-$days"))
                                    }
                                }
                            }
                            if (d.tuned(line)) {
                                TextButton(onClick = { save(d.under(line).associate { it.name to it.default }, null) }, modifier = Modifier.testTag("mixLineAuto-${line.key}")) {
                                    Text("Back to automatic")
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.Section))
        // The two buttons wrap onto two lines rather than squeeze one onto two lines of its own.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            if (again != null) {
                Button(onClick = again, modifier = Modifier.testTag("mixSettingsAgain")) { Text("Make the mixes again") }
            }
            OutlinedButton(onClick = { save(d.settings.associate { it.name to it.default }, null) }, modifier = Modifier.testTag("mixSettingsReset")) {
                Text("All back to automatic")
            }
        }
        note?.let { Spacer(Modifier.height(Space.Small)); Hint(it) }
    }
}

/** One line of the settings: its name (with AUTO or TUNED for a mix), what's chosen in a few words, its switch, and whether it opens. */
@Composable
private fun MixLine(line: BeatsMixSettings.Line, d: BeatsMixSettings.Settings, open: Boolean, opens: Boolean, onSwitch: (Boolean) -> Unit, onOpen: () -> Unit) {
    val tuned = d.tuned(line)
    Row(
        Modifier.fillMaxWidth().heightIn(min = MIX_LINE)
            .then(if (opens) Modifier.clickable(onClickLabel = if (open) "Close ${line.name}" else "Open ${line.name}", onClick = onOpen) else Modifier)
            .testTag("mixLine-${line.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Space.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line.on != null) {
                    val colour = if (tuned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        if (tuned) "TUNED" else "AUTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colour,
                        modifier = Modifier.border(1.dp, colour.copy(alpha = if (tuned) 1f else 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = Space.Tight).testTag("mixLineTag-${line.key}"),
                    )
                }
            }
            Text(d.note(line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (line.on != null) {
            Spacer(Modifier.width(Space.Medium))
            Switch(checked = d.value(line.on)?.on == true, onCheckedChange = onSwitch, modifier = Modifier.testTag("mixLineSwitch-${line.key}"))
        }
        Spacer(Modifier.width(Space.Small))
        if (opens) Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else Spacer(Modifier.width(MIX_ICON))
    }
}

private val MIX_LINE = 56.dp
private val MIX_ICON = 24.dp

/** A setting that only matters while another is on (how many For now mixes, while For now is made). */
internal fun shown(name: String, d: BeatsMixSettings.Settings): Boolean {
    val needs = NEEDS[name] ?: return true
    return d.value(needs)?.on != false
}

private val NEEDS = mapOf("together_count" to "together", "weekend_apart" to "time_of_day")

private val KEEP_DAYS = listOf(1, 3, 7, 14, 30, 90)

/** A number: its words and value, and a slider from its low to its high, saved when let go. */
@Composable
private fun NumberRow(setting: BeatsMixSettings.Setting, value: Int, onPick: (Int) -> Unit) {
    var moving by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().testTag("mixSetting-${setting.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(setting.label.replace(" … ", " ${moving.toInt()} ").replace("…", "${moving.toInt()}"),
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if ("…" !in setting.label) Text("${moving.toInt()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        val range = (setting.high - setting.low).coerceAtLeast(1)
        Slider(
            value = moving,
            onValueChange = { moving = it },
            onValueChangeFinished = { if (moving.toInt() != value) onPick(moving.toInt()) },
            valueRange = setting.low.toFloat()..setting.high.toFloat(),
            steps = if (range <= 20) range - 1 else 0,
        )
    }
}

/** Names to leave out: each a chip to take back, a field to add one, and the genres that match what's typed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsRow(setting: BeatsMixSettings.Setting, words: List<String>, suggestions: List<String>, onChange: (List<String>) -> Unit) {
    var typed by remember { mutableStateOf("") }
    fun add(word: String) {
        val w = word.trim()
        if (w.isNotEmpty() && words.none { it.equals(w, ignoreCase = true) }) onChange(words + w)
        typed = ""
    }
    Column(Modifier.fillMaxWidth().testTag("mixSetting-${setting.name}"), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        Text(setting.label, style = MaterialTheme.typography.bodyLarge)
        if (words.isEmpty()) Hint("None")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            words.forEach { w ->
                InputChip(selected = false, onClick = { onChange(words - w) }, label = { Text(w) },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Take $w back") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text(if (setting.name == "leave_genres") "A genre" else "An artist") },
                modifier = Modifier.weight(1f))
            TextButton(onClick = { add(typed) }, enabled = typed.isNotBlank()) { Text("Add") }
        }
        val matching = if (typed.length >= 2) suggestions.filter { it.contains(typed, ignoreCase = true) && words.none { w -> w.equals(it, true) } }.take(6) else emptyList()
        if (matching.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                matching.forEach { g -> SuggestionChip(onClick = { add(g) }, label = { Text(g) }) }
            }
        }
    }
}
