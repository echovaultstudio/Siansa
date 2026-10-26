package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Carousel
import app.siansa.data.CarouselMix
import app.siansa.data.carouselProblem
import app.siansa.data.daysLabel
import app.siansa.data.normalDays
import kotlinx.coroutines.launch

/**
 * "Home carousel" in the Vibes panel (issue #253): a carousel of 3 or 4 mixes for each part of the day, shown
 * on Home in its hours. Tap one to change its name, hours and mixes.
 */
@Composable
fun CarouselPanel(nav: NavController) {
    val graph = LocalGraph.current
    val carousels by graph.carousels.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.carousels.all() }
    Card(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small).testTag("carouselPanel")) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text("Home carousel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Hint("3 or 4 mixes of your own for each part of the day, side by side on Home. Each sounds different and never shares songs with the others.")
            carousels.orEmpty().forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("carousel/${c.id}") }.padding(vertical = Space.Small).testTag("carousel-${c.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.title} · %02d:00–%02d:00".format(c.startHour, c.endHour), style = MaterialTheme.typography.bodyLarge)
                        // Which days (issue #512): "All week", "Weekdays", "Sat, Sun"…
                        Hint(if (c.days.isEmpty()) "All week" else daysLabel(c.days))
                        Hint(c.usable.joinToString(" · ") { it.name })
                    }
                }
            }
        }
    }
}

/** Changing one home carousel: its name, hours, and the name and sound of each of its 3 or 4 mixes. */
@Composable
fun CarouselEditor(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Carousel?>(null) }
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val names = remember { mutableStateListOf("", "", "", "") }
    val sounds = remember { mutableStateListOf("", "", "", "") }
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun fill(c: Carousel) {
        title = c.title
        start = c.startHour.toString()
        end = c.endHour.toString()
        days = c.days.toSet()
        repeat(Carousel.MAX) { i ->
            names[i] = c.mixes.getOrNull(i)?.name.orEmpty()
            sounds[i] = c.mixes.getOrNull(i)?.sound.orEmpty()
        }
    }

    LaunchedEffect(id) {
        graph.carousels.get(id)?.let { loaded = it; fill(it) }
    }

    fun save() {
        val c = loaded ?: return
        val startHour = start.trim().toIntOrNull()
        val endHour = end.trim().toIntOrNull()
        val changed = c.copy(
            title = title.trim(),
            startHour = startHour ?: -1,
            endHour = endHour ?: -1,
            days = normalDays(days),
            mixes = (0 until Carousel.MAX).map { CarouselMix(names[it].trim(), sounds[it].trim()) }.filter { it.name.isNotEmpty() || it.sound.isNotEmpty() },
        )
        problem = when {
            startHour == null || startHour !in 0..23 -> "From hour must be 0–23."
            endHour == null || endHour !in 0..23 -> "Until hour must be 0–23."
            else -> carouselProblem(changed)
        }
        if (problem != null) return
        scope.launch {
            graph.carousels.save(changed)
            nav.popBackStack()
        }
    }

    Screen(
        title = loaded?.title ?: "Home carousel",
        nav = nav,
        actions = { IconButton(onClick = { save() }) { Icon(Icons.Rounded.Check, "Save") } },
    ) { padding ->
        if (loaded == null) return@Screen
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .aboveKeyboard()
                .verticalScroll(rememberScrollState())
                .padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Name on Home") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // Days (issue #512): all week, or only the days picked.
            Text("Days", style = MaterialTheme.typography.bodyLarge)
            DayChips(days, onAllWeek = { days = emptySet() }) { day -> days = if (day in days) days - day else days + day }
            Hint(
                if (normalDays(days).isEmpty()) "All week. Pick days to use this carousel only on them, e.g. a weekend morning."
                else "Only on " + daysLabel(days) + ". On other days, another carousel for these hours shows, if there is one."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                OutlinedTextField(
                    start, { start = it.filter(Char::isDigit).take(2) },
                    label = { Text("From hour") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    end, { end = it.filter(Char::isDigit).take(2) },
                    label = { Text("Until hour") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Hint("Give each mix a name and say what it should sound like, e.g. \"upbeat energetic groove, punchy drums\". Make them different: the mixes never share songs. The fourth is optional.")
            repeat(Carousel.MAX) { i ->
                HorizontalDivider()
                Text("Mix ${i + 1}${if (i == Carousel.MAX - 1) " (optional)" else ""}", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(names[i], { names[i] = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sounds[i], { sounds[i] = it }, label = { Text("What it sounds like") }, modifier = Modifier.fillMaxWidth())
            }
            problem?.let { ErrorText(it) }
            TextButton(onClick = {
                scope.launch {
                    graph.carousels.reset(id)
                    graph.carousels.get(id)?.let { loaded = it; fill(it) }
                    problem = null
                }
            }) { Text("Back to the suggested mixes") }
            Hint("Saved changes make today's mixes again the next time you open Home.")
        }
    }
}
