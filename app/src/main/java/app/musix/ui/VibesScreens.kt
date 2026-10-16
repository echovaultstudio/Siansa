package app.musix.ui

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
import androidx.compose.foundation.layout.imePadding
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
import app.musix.data.Selector
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
        title = "Vibes",
        nav = null,
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomBarPadding(24.dp)),
        ) {
            item {
                Text(
                    "Each vibe covers part of the day. When you open the app, the vibe for the current hour builds today's mix.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
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

@Composable
private fun SelectorCard(
    selector: Selector,
    making: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMake: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
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
            Spacer(Modifier.height(6.dp))
            Text(
                selector.guidance,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onMake, enabled = !making && selector.enabled) {
                if (making) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
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
    var useAi by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
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
            useAi = existing.useAi
            enabled = existing.enabled
        } else {
            selectorId = "vibe-" + UUID.randomUUID().toString().take(8)
        }
        loaded = true
    }

    fun save() {
        val startHour = start.trim().toIntOrNull()
        val endHour = end.trim().toIntOrNull()
        problem = when {
            name.isBlank() -> "Give the vibe a name"
            startHour == null || startHour !in 0..23 -> "Start hour must be 0–23"
            endHour == null || endHour !in 0..23 -> "End hour must be 0–23"
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
            useAi = useAi,
            enabled = enabled,
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Hint("Hours are 0–23. A vibe from 22 to 5 runs past midnight.")
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
            SwitchRow("AI title and sound search", useAi) { useAi = it }
            SwitchRow("Enabled", enabled) { enabled = it }
            problem?.let { ErrorText(it, Modifier) }
            FilledTonalButton(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
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
