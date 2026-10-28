package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.Switch
import app.siansa.mix.CardKind
import app.siansa.mix.CardLook
import app.siansa.mix.CardSource
import app.siansa.mix.CustomCard
import app.siansa.mix.PinnedCard
import app.siansa.mix.cardProblem
import app.siansa.mix.pinnedCardProblem
import kotlinx.coroutines.launch

/** A card of your own from Made for you (issue #274): today's songs for it, ready to play like an album. */
@Composable
fun CardScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val card by loadState("card-$id") { graph.cards.get(id) ?: error("This card was removed") }
    val c = card.valueOrNull()
    val songs by loadStateFresh("card-songs-$id-$round-${c?.hashCode()}") {
        c?.let { graph.cards.songs(it, fresh = round > 0) }
    }
    val title = c?.title ?: "Made for you"
    val colors = tileColors(id)
    CollectionPage(
        nav = nav,
        title = title,
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = songs.valueOrNull(),
        error = (card as? Load.Err)?.message ?: (songs as? Load.Err)?.message,
        saveAsPlaylist = true,
        cover = { m -> GeneratedCover(title, colors, m) },
        header = {
            CollectionByline(null, null, null, listOfNotNull("Made for you", c?.detail, songs.valueOrNull()?.let { "${it.size} songs" }).joinToString(" · "))
        },
        actions = {
            IconButton(onClick = { round++ }) { Icon(Icons.Rounded.Refresh, "New songs") }
        },
    )
}

/**
 * Settings › Cards: Made for you cards (issue #274) and custom cards pinned to Home (issue #622), each under its
 * own heading on one page.
 */
@Composable
fun CardsSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val cards by graph.cards.state.collectAsStateWithLifecycle()
    val pinned by graph.pinnedCards.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CustomCard?>(null) }
    // The custom card being made or changed; a blank one is a new card.
    var editing by remember { mutableStateOf<PinnedCard?>(null) }
    LaunchedEffect(Unit) { graph.cards.all(); graph.pinnedCards.all() }

    Screen(title = "Cards", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Heading("Made for you")
            Hint("Cards shown on Home under Made for you, after the mixes. Each gets new songs every day; tap a name to change it.")
            val list = cards.orEmpty()
            if (list.isEmpty()) Hint("No cards yet.")
            list.forEachIndexed { i, card ->
                if (i > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().testTag("card-${card.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { renaming = card }) {
                        Text(card.title, style = MaterialTheme.typography.bodyLarge)
                        Hint("${card.kind.label} · ${card.detail}")
                    }
                    IconButton(onClick = { renaming = card }) { Icon(Icons.Rounded.Edit, "Rename ${card.title}") }
                    IconButton(onClick = { scope.launch { graph.cards.move(card.id, -1) } }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { scope.launch { graph.cards.move(card.id, 1) } }, enabled = i < list.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { scope.launch { graph.cards.remove(card.id) } }) { Icon(Icons.Rounded.Delete, "Remove ${card.title}") }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.testTag("addCard")) {
                Icon(Icons.Rounded.Add, null)
                Text("Add a card", Modifier.padding(start = Space.Small))
            }

            Spacer(Modifier.height(Space.Medium))
            HorizontalDivider()
            Heading("Custom cards")
            Hint("Cards of your own on Home, in its Your cards section (move it under Arrange Home). Pick how a card looks and where its songs come from: a sound described to AudioMuse, or songs whose tags match. Tap a card to change it.")
            val pins = pinned.orEmpty()
            if (pins.isEmpty()) Hint("No custom cards yet.")
            pins.forEachIndexed { i, card ->
                if (i > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().testTag("pinned-${card.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(lookIcon(card.look), null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = Space.Medium).clickable { editing = card }) {
                        Text(card.title, style = MaterialTheme.typography.bodyLarge)
                        Hint("${card.look.label} · ${card.detail}")
                    }
                    Switch(checked = card.shown, onCheckedChange = { on -> scope.launch { graph.pinnedCards.setShown(card.id, on) } }, modifier = Modifier.testTag("pinnedShown-${card.id}"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { editing = card }) { Icon(Icons.Rounded.Edit, "Change ${card.title}") }
                    IconButton(onClick = { scope.launch { graph.pinnedCards.move(card.id, -1) } }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { scope.launch { graph.pinnedCards.move(card.id, 1) } }, enabled = i < pins.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { scope.launch { graph.pinnedCards.remove(card.id) } }) { Icon(Icons.Rounded.Delete, "Remove ${card.title}") }
                }
            }
            Button(onClick = { editing = PinnedCard.new() }, modifier = Modifier.testTag("addPinnedCard")) {
                Icon(Icons.Rounded.Add, null)
                Text("Add a custom card", Modifier.padding(start = Space.Small))
            }
        }
    }

    if (adding) AddCardDialog(onDismiss = { adding = false }) { card -> scope.launch { graph.cards.add(card) }; adding = false }
    renaming?.let { card ->
        var name by remember(card.id) { mutableStateOf(card.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename card") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name on Home") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { graph.cards.rename(card.id, name) }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    editing?.let { card ->
        val isNew = pinned.orEmpty().none { it.id == card.id }
        PinnedCardDialog(card, isNew, settings.audioMuseReady, onDismiss = { editing = null }) { saved ->
            scope.launch { if (isNew) graph.pinnedCards.add(saved) else graph.pinnedCards.update(saved) }
            editing = null
        }
    }
}

/** The small picture for a look, in Settings and the editor. */
internal fun lookIcon(look: CardLook) = when (look) {
    CardLook.TILES -> Icons.Rounded.ViewCarousel
    CardLook.ALBUMS -> Icons.Rounded.Album
    CardLook.BANNER -> Icons.Rounded.ViewDay
    CardLook.LIST -> Icons.Rounded.FormatListBulleted
    CardLook.BUTTON -> Icons.Rounded.PlayCircle
}

/** Making or changing a custom card (issue #622): its name, look and contents, all on one sheet. */
@Composable
private fun PinnedCardDialog(start: PinnedCard, isNew: Boolean, audioMuseReady: Boolean, onDismiss: () -> Unit, onSave: (PinnedCard) -> Unit) {
    val graph = LocalGraph.current
    var card by remember(start.id) { mutableStateOf(start) }
    var from by remember(start.id) { mutableStateOf(start.fromYear?.toString().orEmpty()) }
    var to by remember(start.id) { mutableStateOf(start.toYear?.toString().orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val genres by loadState("card-genres") { if (graph.library.isSynced) graph.library.genres(40).map { it.name } else emptyList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a custom card" else "Change the card") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                OutlinedTextField(card.title, { card = card.copy(title = it); problem = null }, singleLine = true, label = { Text("Name on Home") }, modifier = Modifier.fillMaxWidth().testTag("pinnedTitle"))

                Text("Look", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    CardLook.entries.forEach { look ->
                        FilterChip(
                            selected = card.look == look,
                            onClick = { card = card.copy(look = look) },
                            label = { Text(look.label) },
                            leadingIcon = { Icon(lookIcon(look), null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("look-${look.name}"),
                        )
                    }
                }
                Hint(card.look.hint)

                Text("Songs", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    CardSource.entries.forEach { source ->
                        FilterChip(
                            selected = card.source == source,
                            onClick = { card = card.copy(source = source); problem = null },
                            label = { Text(source.label) },
                            modifier = Modifier.testTag("source-${source.name}"),
                        )
                    }
                }
                Hint(card.source.hint)
                when (card.source) {
                    CardSource.SOUND -> {
                        if (!audioMuseReady) Hint("Needs AudioMuse-AI, set up in Settings › Connections.")
                        OutlinedTextField(card.sound, { card = card.copy(sound = it); problem = null }, label = { Text("The sound") }, placeholder = { Text("rainy jazz piano") }, modifier = Modifier.fillMaxWidth().testTag("pinnedSound"))
                    }
                    CardSource.LIBRARY -> {
                        Text("Genre", style = MaterialTheme.typography.bodyMedium)
                        val names = genres.valueOrNull().orEmpty()
                        if (names.isEmpty()) Hint("Sync your library to pick a genre from a list.")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            names.forEach { g ->
                                FilterChip(selected = card.genre.equals(g, ignoreCase = true), onClick = { card = card.copy(genre = if (card.genre.equals(g, ignoreCase = true)) "" else g); problem = null }, label = { Text(g) })
                            }
                        }
                        OutlinedTextField(card.genre, { card = card.copy(genre = it); problem = null }, singleLine = true, label = { Text("Genre") }, modifier = Modifier.fillMaxWidth())
                        Text("Years", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            listOf(1960, 1970, 1980, 1990, 2000, 2010, 2020).forEach { d ->
                                FilterChip(
                                    selected = from == "$d" && to == "${d + 9}",
                                    onClick = { from = "$d"; to = "${d + 9}"; problem = null },
                                    label = { Text("${d % 100}s".padStart(3, '0')) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            OutlinedTextField(from, { from = it.filter(Char::isDigit).take(4); problem = null }, label = { Text("From") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(to, { to = it.filter(Char::isDigit).take(4); problem = null }, label = { Text("To") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(card.artist, { card = card.copy(artist = it); problem = null }, singleLine = true, label = { Text("Artist contains") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(card.album, { card = card.copy(album = it); problem = null }, singleLine = true, label = { Text("Album contains") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(card.words, { card = card.copy(words = it); problem = null }, singleLine = true, label = { Text("Words in the title") }, modifier = Modifier.fillMaxWidth())
                        SwitchRow("Favourites only", card.favourites) { card = card.copy(favourites = it); problem = null }
                    }
                }
                problem?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val done = card.copy(fromYear = from.toIntOrNull(), toYear = to.toIntOrNull())
                problem = pinnedCardProblem(done, audioMuseReady)
                if (problem == null) onSave(done)
            }, modifier = Modifier.testTag("pinnedSave")) { Text(if (isNew) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picking what a new card shows: a kind, and a genre or years where it needs one. */
@Composable
private fun AddCardDialog(onDismiss: () -> Unit, onAdd: (CustomCard) -> Unit) {
    val graph = LocalGraph.current
    var kind by remember { mutableStateOf(CardKind.FORGOTTEN) }
    var genre by remember { mutableStateOf<String?>(null) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val genres by loadState("card-genres") { if (graph.library.isSynced) graph.library.genres(40).map { it.name } else emptyList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a card") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                CardKind.entries.forEach { k ->
                    Row(Modifier.fillMaxWidth().clickable { kind = k; problem = null }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k; problem = null })
                        Column {
                            Text(k.label, style = MaterialTheme.typography.bodyLarge)
                            Hint(k.hint)
                        }
                    }
                }
                when (kind) {
                    CardKind.GENRE -> {
                        val names = genres.valueOrNull().orEmpty()
                        if (names.isEmpty()) Hint("Sync your library first to pick a genre.")
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            names.forEach { g -> FilterChip(selected = genre == g, onClick = { genre = g }, label = { Text(g) }) }
                        }
                    }
                    CardKind.YEARS -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            listOf(1960, 1970, 1980, 1990, 2000, 2010, 2020).forEach { d ->
                                FilterChip(
                                    selected = from == "$d" && to == "${d + 9}",
                                    onClick = { from = "$d"; to = "${d + 9}" },
                                    label = { Text("${d % 100}s".padStart(3, '0')) },
                                )
                            }
                        }
                        Row(Modifier.padding(top = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            OutlinedTextField(from, { from = it.filter(Char::isDigit).take(4) }, label = { Text("From") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(to, { to = it.filter(Char::isDigit).take(4) }, label = { Text("To") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                    }
                    else -> Unit
                }
                problem?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val card = CustomCard.new(kind, genre = genre.takeIf { kind == CardKind.GENRE }, fromYear = from.toIntOrNull().takeIf { kind == CardKind.YEARS }, toYear = to.toIntOrNull().takeIf { kind == CardKind.YEARS })
                problem = cardProblem(card)
                if (problem == null) onAdd(card)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
