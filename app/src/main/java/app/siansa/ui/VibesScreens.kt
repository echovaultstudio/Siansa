package app.siansa.ui

import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.horizontalScroll

import androidx.compose.material3.FilterChip

import app.siansa.data.daysLabel

import app.siansa.data.DAY_SHORT

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Selector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** The list of time-of-day selectors ("vibes") that drive the daily mixes. */
@Composable
fun VibesScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val selectors by graph.selectors.state.collectAsStateWithLifecycle()
    var making by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { graph.selectors.all() }

    Screen(
        title = "Daily Vibe settings",
        nav = nav,
        actions = {
            IconButton(onClick = { nav.navigate("selector/new") }) { Icon(Icons.Rounded.Add, "Add vibe") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reset to defaults") }, onClick = {
                        menu = false
                        confirmReset = true
                    })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomBarPadding(Space.Section)),
        ) {
            item {
                Text(
                    "Each vibe covers part of the day. When you open the app, the vibe for the current hour builds today's mix.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
                )
            }
            item { TodaysMixPanel() }
            item { SituationPanel() }
            item { CarouselPanel(nav) }
            error?.let { item { ErrorText(it) } }
            items(selectors.orEmpty(), key = { it.id }) { selector ->
                SelectorCard(
                    selector = selector,
                    making = making == selector.id,
                    onOpen = { nav.navigate("selector/${selector.id}") },
                    onToggle = { on -> scope.launch { graph.selectors.save(selector.copy(enabled = on)) } },
                    onMake = {
                        if (making == null) {
                            scope.launch {
                                making = selector.id
                                error = null
                                try {
                                    val mix = graph.generator.ensure(selector, force = true)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.navigate("mix/${mix.id}") }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    making = null
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset vibes?") },
            text = { Text("This replaces your vibes with Morning, Daytime, Evening and Night.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { graph.selectors.resetToDefaults() }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Home's Today's mix (issue #776): whether it's made by itself, and its own tuning over the vibe's, in one place.
 * A chip "Vibe's own" leaves a setting to the vibe that makes the mix.
 */
@Composable
fun TodaysMixPanel() {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val daily = settings.dailyMix
    val scope = rememberCoroutineScope()
    fun set(next: app.siansa.data.DailyMix) = graph.settings.setDailyMix(next)
    // A tuning change with Auto update on remakes the mix (issue #776), in the app's scope so leaving the page
    // doesn't cancel it; the tile on Home shows the new one.
    fun tune(next: app.siansa.data.DailyMix) {
        set(next)
        if (next.autoUpdate && settings.audioMuseReady) graph.scope.launch { runCatching { graph.generator.ensureCurrent(force = true) } }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small).testTag("todaysMix")) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text("Today's mix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "The mix at the top of Home, made by the vibe for the hour. Settings here apply to it whichever vibe makes it; " +
                    "\"Vibe's own\" keeps what that vibe says.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("Auto update", daily.autoUpdate) { set(daily.copy(autoUpdate = it)) }
            Hint(
                if (daily.autoUpdate) "The mix follows the time of day: a new one is made as the day or the hour's vibe moves on, and when a setting here changes."
                else "The mix you last made with ↻ stays, whatever the day or the hour, until you press ↻ again.",
            )
            TuningRow("Songs", daily.length, app.siansa.data.DailyMix.LENGTHS, { "$it" }, "dailyLength") { tune(daily.copy(length = it)) }
            TuningRow("Surprise", daily.surprise, listOf(0, 15, 30, 50, 70), { "$it %" }, "dailySurprise") { tune(daily.copy(surprise = it)) }
            Hint("How much of the mix comes from outside your usual artists and sound.")
            TuningRow("Leave out most played", daily.leaveOutTopPlayed, app.siansa.data.DailyMix.LEAVE_OUT, { if (it == 0) "None" else "Top $it" }, "dailyLeaveOut") { tune(daily.copy(leaveOutTopPlayed = it)) }
            TuningRow("Days before a repeat", daily.repeatDays, app.siansa.data.DailyMix.REPEAT_DAYS, { if (it == 0) "Any" else "$it" }, "dailyRepeat") { tune(daily.copy(repeatDays = it)) }
        }
    }
}

/** A tuning of Today's mix: "Vibe's own" (null) or one of [choices], as chips. */
@Composable
internal fun TuningRow(label: String, value: Int?, choices: List<Int>, text: (Int) -> String, tag: String, onPick: (Int?) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        FilterChip(selected = value == null, onClick = { onPick(null) }, label = { Text("Vibe's own") })
        choices.forEach { c -> FilterChip(selected = value == c, onClick = { onPick(c) }, label = { Text(text(c)) }) }
    }
}

@Composable
private fun SelectorCard(
    selector: Selector,
    making: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMake: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small).clickable(onClick = onOpen)) {
        Column(Modifier.padding(Space.Page)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selector.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        selector.hoursLabel + if (selector.useAi) " · AI titles" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = selector.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(Space.Small))
            Text(
                selector.guidance,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.Small))
            FilledTonalButton(onClick = onMake, enabled = !making && selector.enabled) {
                if (making) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(Space.Small))
                Text("Make a mix now")
            }
        }
    }
}

/** Create or edit one selector. id "new" starts a blank one. */
@Composable
fun SelectorEditor(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var selectorId by remember { mutableStateOf(id) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("18") }
    var end by remember { mutableStateOf("22") }
    var guidance by remember { mutableStateOf("") }
    var baseQuery by remember { mutableStateOf("") }
    var avoid by remember { mutableStateOf("") }
    var length by remember { mutableStateOf(40f) }
    var repeatDays by remember { mutableStateOf("3") }
    var leaveOut by remember { mutableStateOf(50f) }
    var surprise by remember { mutableStateOf(30f) }
    var useAi by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
    // Days and time of day, each optional and on its own (issue #348).
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var anyTime by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        val existing = if (id == "new") null else graph.selectors.get(id)
        if (existing != null) {
            name = existing.name
            start = existing.startHour.toString()
            end = existing.endHour.toString()
            guidance = existing.guidance
            baseQuery = existing.baseQuery
            avoid = existing.avoid.joinToString(", ")
            length = existing.length.toFloat()
            repeatDays = existing.repeatDays.toString()
            leaveOut = existing.leaveOutTopPlayed.toFloat()
            surprise = existing.surprise.toFloat()
            useAi = existing.useAi
            enabled = existing.enabled
            days = existing.days.toSet()
            anyTime = existing.anyTime || existing.startHour == existing.endHour
        } else {
            selectorId = "vibe-" + UUID.randomUUID().toString().take(8)
        }
        loaded = true
    }

    fun save() {
        // With "any time", the hours aren't used, so they needn't be filled in.
        val startHour = if (anyTime) start.trim().toIntOrNull() ?: 0 else start.trim().toIntOrNull()
        val endHour = if (anyTime) end.trim().toIntOrNull() ?: 0 else end.trim().toIntOrNull()
        problem = when {
            name.isBlank() -> "Give the vibe a name"
            !anyTime && (startHour == null || startHour !in 0..23) -> "Start hour must be 0–23"
            !anyTime && (endHour == null || endHour !in 0..23) -> "End hour must be 0–23"
            baseQuery.isBlank() && guidance.isBlank() -> "Add guidance or a sound search"
            else -> null
        }
        if (problem != null || startHour == null || endHour == null) return
        val selector = Selector(
            id = selectorId,
            name = name.trim(),
            startHour = startHour,
            endHour = endHour,
            guidance = guidance.trim(),
            baseQuery = baseQuery.trim(),
            avoid = avoid.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            length = length.roundToInt(),
            repeatDays = repeatDays.trim().toIntOrNull()?.coerceIn(0, 30) ?: 3,
            leaveOutTopPlayed = leaveOut.roundToInt(),
            surprise = surprise.roundToInt(),
            useAi = useAi,
            enabled = enabled,
            days = days.sorted(),
            anyTime = anyTime,
        )
        scope.launch {
            graph.selectors.save(selector)
            nav.popBackStack()
        }
    }

    Screen(
        title = if (id == "new") "New vibe" else "Edit vibe",
        nav = nav,
        actions = {
            if (id != "new") {
                IconButton(onClick = {
                    scope.launch {
                        graph.selectors.delete(id)
                        nav.popBackStack()
                    }
                }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
            IconButton(onClick = { save() }) { Icon(Icons.Rounded.Check, "Save") }
        },
    ) { padding ->
        if (!loaded) return@Screen
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .aboveKeyboard()
                .verticalScroll(rememberScrollState())
                .padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // Days (issue #348): none picked means every day.
            Text("Days", style = MaterialTheme.typography.bodyLarge)
            DayChips(days, onAllWeek = { days = emptySet() }) { day -> days = if (day in days) days - day else days + day }
            Hint(if (days.isEmpty()) "Every day. Pick days to make this vibe only for them, e.g. a Friday afternoon vibe." else "Only on " + daysLabel(days) + ".")
            SwitchRow("Any time of day", anyTime) { anyTime = it }
            if (!anyTime) Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                OutlinedTextField(
                    start, { start = it.filter(Char::isDigit).take(2) },
                    label = { Text("From hour") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    end, { end = it.filter(Char::isDigit).take(2) },
                    label = { Text("To hour") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!anyTime) Hint("Hours are 0–23. A vibe from 22 to 5 runs past midnight.")
            Hint("When several vibes fit, the most specific wins: one set for a day before an everyday one, one for some hours before an all-day one.")
            OutlinedTextField(
                guidance, { guidance = it },
                label = { Text("Guidance for the AI") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("How this time of day should feel. The AI writes a title and a sound search from it.")
            OutlinedTextField(
                baseQuery, { baseQuery = it },
                label = { Text("Fallback sound search") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Used when AI is off or unavailable, e.g. \"calm atmospheric night music, soft and slow\".")
            OutlinedTextField(
                avoid, { avoid = it },
                label = { Text("Never use these words") },
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Comma separated. An AI answer containing one is thrown away.")
            Text("Mix length: ${length.roundToInt()} tracks", style = MaterialTheme.typography.bodyLarge)
            Slider(value = length, onValueChange = { length = it }, valueRange = 15f..100f, steps = 16)
            OutlinedTextField(
                repeatDays, { repeatDays = it.filter(Char::isDigit).take(2) },
                label = { Text("Don't repeat songs played in the last … days") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            // Less of what you always hear (issue #236).
            Text(
                if (leaveOut.roundToInt() == 0) "Leave out your most played songs: off" else "Leave out your ${leaveOut.roundToInt()} most played songs",
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(value = leaveOut, onValueChange = { leaveOut = it }, valueRange = 0f..80f, steps = 7)
            Text("Surprise: ${surprise.roundToInt()}% from outside your usual artists", style = MaterialTheme.typography.bodyLarge)
            Slider(value = surprise, onValueChange = { surprise = it }, valueRange = 0f..60f, steps = 11)
            Hint("The surprise part blends the vibe's sound away from your most played songs, so each day brings artists you don't hear often.")
            SwitchRow("AI title and sound search", useAi) { useAi = it }
            SwitchRow("Enabled", enabled) { enabled = it }
            problem?.let { ErrorText(it, Modifier) }
            FilledTonalButton(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

/**
 * Monday to Sunday as chips; tap one to pick it or leave it out (issue #348). With [onAllWeek], an "All week" chip
 * comes first, on while no day is picked, and tapping it clears the days (issue #512).
 */
@Composable
internal fun DayChips(days: Set<Int>, onAllWeek: (() -> Unit)? = null, onToggle: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("dayChips"),
        horizontalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        if (onAllWeek != null) {
            FilterChip(
                selected = days.isEmpty() || days.size == 7,
                onClick = onAllWeek,
                label = { Text("All week") },
                modifier = Modifier.testTag("day-all"),
            )
        }
        DAY_SHORT.forEachIndexed { i, label ->
            FilterChip(
                selected = (i + 1) in days,
                onClick = { onToggle(i + 1) },
                label = { Text(label) },
                modifier = Modifier.testTag("day-${i + 1}"),
            )
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
