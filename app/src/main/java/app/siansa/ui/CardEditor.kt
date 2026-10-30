package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.log.AppLog
import app.siansa.mix.CardContents
import app.siansa.mix.CardLook
import app.siansa.mix.CardRule
import app.siansa.mix.CardSort
import app.siansa.mix.CardSource
import app.siansa.mix.MAX_SIZE
import app.siansa.mix.PinnedCard
import app.siansa.mix.RuleField
import app.siansa.mix.RuleKind
import app.siansa.mix.RuleOp
import app.siansa.mix.pinnedCardProblem
import kotlinx.coroutines.launch

/**
 * Making or changing a custom card (issue #663), on a page of its own: its name, whether it lists songs or whole
 * albums, where they come from, the rules they must match (each "is", "isn't", "contains", "doesn't contain", "at
 * least"… a value), their order and how many, and how the card looks on Home. [id] "new" starts a new card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditorScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val isNew = id == "new"
    var card by remember { mutableStateOf<PinnedCard?>(if (isNew) PinnedCard.new() else null) }
    LaunchedEffect(id) {
        // A card made before rules gets its single fields as rules here, so they can be changed one by one.
        if (!isNew) card = graph.pinnedCards.get(id)?.migrated() ?: PinnedCard.new()
    }
    var problem by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val audioMuseReady = graph.settings.current.audioMuseReady
    val genres by loadState("card-genres") { if (graph.library.isSynced) graph.library.genres(40).map { it.name } else emptyList() }
    val labels by loadState("card-labels") { if (graph.library.isSynced) graph.library.labels(40) else emptyList() }

    fun save() {
        val done = card ?: return
        problem = pinnedCardProblem(done, audioMuseReady)
        if (problem != null) return
        scope.launch {
            if (isNew) graph.pinnedCards.add(done) else graph.pinnedCards.update(done)
            nav.popBackStack()
        }
    }

    Screen(
        title = if (isNew) "New custom card" else "Change the card",
        nav = nav,
        actions = { TextButton(onClick = ::save, modifier = Modifier.testTag("cardEditorSave")) { Text(if (isNew) "Add" else "Save") } },
    ) { padding ->
        val c = card ?: return@Screen
        fun change(new: PinnedCard) { card = new; problem = null }
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Page)
                .padding(bottom = Space.Section)
                .navigationBarsPadding()
                .testTag("cardEditor"),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            OutlinedTextField(c.title, { change(c.copy(title = it)) }, singleLine = true, label = { Text("Name on Home") }, modifier = Modifier.fillMaxWidth().testTag("pinnedTitle"))

            EditorHeading("Shows")
            ChipRow(CardContents.entries, c.contents, { it.label }, "contents") { contents ->
                // Albums come from the library, and only by rules that albums have.
                change(
                    c.copy(
                        contents = contents,
                        source = if (contents == CardContents.ALBUMS) CardSource.LIBRARY else c.source,
                        rules = c.rules.filter { if (contents == CardContents.ALBUMS) it.field.albums else it.field.songs },
                        look = if (contents == CardContents.ALBUMS && c.look == CardLook.ALBUMS) CardLook.TILES else c.look,
                    ),
                )
            }
            Hint(c.contents.hint)

            if (c.contents == CardContents.SONGS) {
                EditorHeading("Comes from")
                ChipRow(CardSource.entries, c.source, { it.label }, "source") { change(c.copy(source = it)) }
                Hint(c.source.hint)
            }

            if (c.source == CardSource.SOUND && c.contents == CardContents.SONGS) {
                if (!audioMuseReady) Hint("Needs AudioMuse-AI, set up in Settings › Connections.")
                OutlinedTextField(c.sound, { change(c.copy(sound = it)) }, label = { Text("The sound") }, placeholder = { Text("rainy jazz piano") }, modifier = Modifier.fillMaxWidth().testTag("pinnedSound"))
            } else {
                EditorHeading("Rules")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text("Keep ${c.contents.label.lowercase()} that match", style = MaterialTheme.typography.bodyMedium)
                    FilterChip(selected = c.matchAll, onClick = { change(c.copy(matchAll = true)) }, label = { Text("every rule") }, modifier = Modifier.testTag("matchAll"))
                    FilterChip(selected = !c.matchAll, onClick = { change(c.copy(matchAll = false)) }, label = { Text("any rule") }, modifier = Modifier.testTag("matchAny"))
                }
                if (c.rules.isEmpty()) Hint("No rules yet. Add one: a genre, a record label, years, an artist…")
                c.rules.forEachIndexed { i, rule ->
                    RuleCard(
                        rule = rule,
                        suggestions = when (rule.field) {
                            RuleField.GENRE -> genres.valueOrNull().orEmpty()
                            RuleField.LABEL -> labels.valueOrNull().orEmpty()
                            RuleField.RELEASE_TYPE -> RELEASE_TYPES
                            else -> emptyList()
                        },
                        onChange = { changed -> change(c.copy(rules = c.rules.toMutableList().apply { set(i, changed) })) },
                        onRemove = { change(c.copy(rules = c.rules.toMutableList().apply { removeAt(i) })) },
                        modifier = Modifier.testTag("rule-$i"),
                    )
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.testTag("addRule")) {
                    Icon(Icons.Rounded.Add, null)
                    Text("Add a rule", Modifier.padding(start = Space.Small))
                }

                EditorHeading("Order")
                ChipRow(CardSort.entries, c.sort, { it.label }, "sort") { change(c.copy(sort = it)) }
                NumberField("How many, at most", c.size.toString(), Modifier.fillMaxWidth().testTag("cardSize")) { v ->
                    change(c.copy(size = v.toIntOrNull()?.coerceIn(0, MAX_SIZE) ?: 0))
                }
            }

            EditorHeading("Look on Home")
            // An album card's tiles are its albums, so it has no separate album row.
            val looks = if (c.contents == CardContents.ALBUMS) CardLook.entries - CardLook.ALBUMS else CardLook.entries
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                looks.forEach { look ->
                    FilterChip(
                        selected = c.look == look,
                        onClick = { change(c.copy(look = look)) },
                        label = { Text(lookLabel(look, c.contents)) },
                        leadingIcon = { Icon(lookIcon(look), null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("look-${look.name}"),
                    )
                }
            }
            Hint(lookHint(c.look, c.contents))

            problem?.let { ErrorText(it, Modifier.testTag("cardProblem")) }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().testTag("cardEditorSaveBottom")) { Text(if (isNew) "Add the card" else "Save") }
        }
        if (picking) {
            RuleFieldDialog(c.contents, onDismiss = { picking = false }) { field ->
                picking = false
                AppLog.d("Cards", "Rule added: ${field.label}")
                change(c.copy(rules = c.rules + CardRule.of(field)))
            }
        }
    }
}

@Composable
private fun EditorHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.Small))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, tag: String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onPick(item) }, label = { Text(label(item)) }, modifier = Modifier.testTag("$tag-$item"))
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, suffix: String = "", onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }.take(7)) },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/**
 * One rule as a small card: what it looks at, how it compares, and the value. Text takes several values separated
 * by commas (any of them), with the library's own values to tap; numbers take one, or two for "between".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuleCard(rule: CardRule, suggestions: List<String>, onChange: (CardRule) -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Space.Medium, end = Space.Tight, bottom = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.field.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove the rule") }
            }
            Column(Modifier.padding(end = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    RuleOp.forKind(rule.field.kind).forEach { op ->
                        FilterChip(selected = rule.op == op, onClick = { onChange(rule.copy(op = op)) }, label = { Text(op.label) }, modifier = Modifier.testTag("op-${op.name}"))
                    }
                }
                when (rule.field.kind) {
                    RuleKind.FLAG -> Unit
                    RuleKind.TEXT -> {
                        OutlinedTextField(rule.value, { onChange(rule.copy(value = it)) }, singleLine = true, label = { Text(rule.field.label) },
                            supportingText = { Text("Several? Separate them with commas: any of them counts.") },
                            modifier = Modifier.fillMaxWidth().testTag("ruleValue"))
                        if (suggestions.isNotEmpty()) {
                            val chosen = rule.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            FlowRow(Modifier.heightIn(max = 136.dp), horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                                suggestions.take(24).forEach { s ->
                                    val on = chosen.any { it.equals(s, ignoreCase = true) }
                                    FilterChip(selected = on, onClick = {
                                        val next = if (on) chosen.filterNot { it.equals(s, ignoreCase = true) } else chosen + s
                                        onChange(rule.copy(value = next.joinToString(", ")))
                                    }, label = { Text(s) })
                                }
                            }
                        }
                    }
                    RuleKind.NUMBER -> {
                        if (rule.op == RuleOp.BETWEEN) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                                NumberField("From", rule.value, Modifier.weight(1f).testTag("ruleValue"), rule.field.unit) { onChange(rule.copy(value = it)) }
                                NumberField("To", rule.value2, Modifier.weight(1f).testTag("ruleValue2"), rule.field.unit) { onChange(rule.copy(value2 = it)) }
                            }
                        } else {
                            NumberField(rule.field.label, rule.value, Modifier.fillMaxWidth().testTag("ruleValue"), rule.field.unit) { onChange(rule.copy(value = it)) }
                        }
                        if (rule.field == RuleField.YEAR) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                                listOf(1970, 1980, 1990, 2000, 2010, 2020).forEach { d ->
                                    FilterChip(selected = rule.op == RuleOp.BETWEEN && rule.value == "$d" && rule.value2 == "${d + 9}",
                                        onClick = { onChange(rule.copy(op = RuleOp.BETWEEN, value = "$d", value2 = "${d + 9}")) },
                                        label = { Text("${d % 100}s".padStart(3, '0')) })
                                }
                            }
                        }
                    }
                    RuleKind.AGE -> {
                        NumberField("Days", rule.value, Modifier.fillMaxWidth().testTag("ruleValue"), "days") { onChange(rule.copy(value = it)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                            listOf(7 to "A week", 30 to "A month", 90 to "3 months", 365 to "A year").forEach { (days, name) ->
                                FilterChip(selected = rule.value == "$days", onClick = { onChange(rule.copy(value = "$days")) }, label = { Text(name) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Picking what a new rule looks at: the song's own details, then its album's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleFieldDialog(contents: CardContents, onDismiss: () -> Unit, onPick: (RuleField) -> Unit) {
    val fields = RuleField.forContents(contents)
    val groups = if (contents == CardContents.ALBUMS) listOf("About the album" to fields)
    else listOf("About the song" to fields.filter { it in SONG_OWN }, "About its album" to fields.filter { it !in SONG_OWN })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("ruleFields"), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                groups.filter { it.second.isNotEmpty() }.forEach { (heading, list) ->
                    Text(heading, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                        list.forEach { field ->
                            FilterChip(selected = false, onClick = { onPick(field) }, label = { Text(field.label) }, modifier = Modifier.testTag("field-${field.name}"))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A song's own details; the rest come from its album. */
private val SONG_OWN = setOf(RuleField.TITLE, RuleField.ARTIST, RuleField.GENRE, RuleField.YEAR, RuleField.LENGTH, RuleField.FAVOURITE, RuleField.ALBUM)

/** Release types as the servers tag them, to tap. */
private val RELEASE_TYPES = listOf("Album", "EP", "Single", "Compilation", "Live", "Soundtrack", "Remix", "DJ-mix")

/** A look's name for what the card lists: an album card's tiles are albums. */
internal fun lookLabel(look: CardLook, contents: CardContents): String = when {
    contents == CardContents.ALBUMS && look == CardLook.TILES -> "Album tiles"
    contents == CardContents.ALBUMS && look == CardLook.LIST -> "Album list"
    else -> look.label
}

internal fun lookHint(look: CardLook, contents: CardContents): String = when {
    contents == CardContents.ALBUMS && look == CardLook.TILES -> "A row of the albums, to swipe through"
    contents == CardContents.ALBUMS && look == CardLook.LIST -> "The first five albums listed, the rest a tap away"
    contents == CardContents.ALBUMS && look == CardLook.BUTTON -> "A slim card that plays the albums in turn when tapped"
    else -> look.hint
}
