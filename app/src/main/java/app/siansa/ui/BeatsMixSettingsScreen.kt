package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.log.AppLog
import app.siansa.net.BeatsListens
import app.siansa.net.BeatsMixSettings
import app.siansa.net.BeatsMixSettings.Kind
import app.siansa.net.BeatsMixSettings.Value
import kotlinx.coroutines.launch

/**
 * What goes into your mixes from Siansa Beats (Settings › Mixes & radios › Mix settings; the owner, 17 Nov:
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
                    note = "Saved: your mixes are made again"
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
            Hint("What goes into the mixes Siansa Beats makes for you. Siansa Beats keeps these, and its own Mixes page changes the same.")
            val d = loaded
            when {
                problem != null -> Text(problem.orEmpty(), color = MaterialTheme.colorScheme.error)
                d == null -> Hint("Loading…")
                else -> MixSettingsBody(d, note, graph.beatsGenres.saved().orEmpty().map { it.name }, ::save)
            }
        }
    }
}

/** The settings themselves, in their groups; how long mixes are kept; and "As they come". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MixSettingsBody(d: BeatsMixSettings.Settings, note: String?, genres: List<String>, save: (Map<String, Value>, Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        d.grouped().forEach { (title, settings) ->
            HorizontalDivider()
            Heading(title)
            settings.filter { shown(it.name, d) }.forEach { setting ->
                val v = d.value(setting.name) ?: setting.default
                when (setting.kind) {
                    Kind.ON -> SwitchRow(setting.label, v.on) { save(mapOf(setting.name to Value(on = it)), null) }
                    Kind.NUMBER -> NumberRow(setting, v.number) { save(mapOf(setting.name to Value(number = it)), null) }
                    Kind.WORDS -> WordsRow(setting, v.words, if (setting.name == "leave_genres") genres else emptyList()) {
                        save(mapOf(setting.name to Value(words = it)), null)
                    }
                }
            }
        }
        HorizontalDivider()
        Heading("Keep mixes for")
        Hint("How long a mix you played still plays from your shortcuts. For every listener.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            KEEP_DAYS.forEach { days ->
                FilterChip(selected = d.keepDays == days, onClick = { save(emptyMap(), days) },
                    label = { Text(if (days == 1) "1 day" else "$days days") }, modifier = Modifier.testTag("mixKeep-$days"))
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
            OutlinedButton(onClick = { save(d.settings.associate { it.name to it.default }, null) }, modifier = Modifier.testTag("mixSettingsReset")) {
                Text("As they come")
            }
            note?.let { Hint(it) }
        }
    }
}

/** A setting that only matters while another is on (how many For now mixes, while For now is made). */
internal fun shown(name: String, d: BeatsMixSettings.Settings): Boolean {
    val needs = NEEDS[name] ?: return true
    return d.value(needs)?.on != false
}

private val NEEDS = mapOf(
    "now_count" to "now", "daily_count" to "daily", "repeat_days" to "repeat", "forgotten_days" to "rediscover",
    "weekend_apart" to "time_of_day",
)

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
