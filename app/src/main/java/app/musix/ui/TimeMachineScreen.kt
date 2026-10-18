package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.log.AppLog
import app.musix.mix.TIME_MACHINE_FIRST_YEAR
import app.musix.mix.TimeMachine
import app.musix.mix.memoryName
import app.musix.mix.monthsAvailable
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** The even gap between the time machine's year, months and buttons. */
internal val TIME_MACHINE_GAP: Dp = 12.dp

/**
 * The time machine (issue #186), under Your listening: pick a year and a month (or the whole year) and see
 * the songs you played most then, from Koito and Last.fm, only ones in your library. Songs play, have the ⋮
 * menu and swipe to the queue like everywhere else. "Create memory" makes them a playlist with a picture.
 */
@Composable
fun TimeMachineScreen(nav: NavController) {
    MusixDarkTheme { TimeMachinePage(nav) }
}

@Composable
private fun TimeMachinePage(nav: NavController) {
    val graph = LocalGraph.current
    val today = remember { LocalDate.now() }
    // A year ago by default: the most likely time to want to go back to.
    var year by rememberSaveable { mutableIntStateOf(today.year - 1) }
    var month by rememberSaveable { mutableStateOf<Int?>(today.monthValue) }
    // A new year or month shows the spinner straight away, not the last list (issue #189).
    val result by loadStateFresh("timemachine-$year-$month") { graph.insights.timeMachine(year, month) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Brand.Plum)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(32.dp))) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Brand.VioletDeep, Brand.AmberDeep.copy(alpha = 0.45f), Brand.Plum)))
                        .statusBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Text(
                        "Time machine",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Text(
                        "The songs you played most, any month or year.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(TIME_MACHINE_GAP / 2))
                    TimePicker(
                        year = year,
                        month = month,
                        today = today,
                        onYear = { y ->
                            year = y
                            // A month that hasn't happened yet in the new year becomes the whole year.
                            if (month != null && month!! !in monthsAvailable(y, today)) month = null
                        },
                        onMonth = { month = it },
                    )
                }
            }
            item {
                LoadBox(result) { tm -> TimeMachineSongs(tm, onCreate = { creating = true }) }
            }
            val tm = result.valueOrNull()
            if (tm != null && tm.tracks.isNotEmpty()) {
                val songs = tm.tracks.mapNotNull { it.song }
                itemsIndexed(tm.tracks, key = { i, t -> "$i-${t.song?.id}" }) { i, t ->
                    val song = t.song ?: return@itemsIndexed
                    SongRow(song = song, onClick = { graph.player.play(songs, i) }, leading = "${i + 1}")
                }
                item {
                    Text(
                        tm.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
        }
    }
    result.valueOrNull()?.let { tm ->
        if (creating) CreateMemory(tm, nav) { creating = false }
    }
}

/** The year, with arrows either side, and the months under it (the whole year first). */
@Composable
internal fun TimePicker(year: Int, month: Int?, today: LocalDate, onYear: (Int) -> Unit, onMonth: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("timePicker"), verticalArrangement = Arrangement.spacedBy(TIME_MACHINE_GAP)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("year"), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onYear(year - 1) }, enabled = year > TIME_MACHINE_FIRST_YEAR) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Year before", tint = Color.White)
            }
            Text(
                "$year",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onYear(year + 1) }, enabled = year < today.year) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Year after", tint = Color.White)
            }
        }
        val available = monthsAvailable(year, today)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("months"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = month == null, onClick = { onMonth(null) }, label = { Text("Whole year") })
            for (m in available) {
                val name = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.')
                FilterChip(selected = month == m, onClick = { onMonth(m) }, label = { Text(name) })
            }
        }
    }
}

/** What the time machine found: Play and Create memory, or why there's nothing. */
@Composable
private fun TimeMachineSongs(tm: TimeMachine, onCreate: () -> Unit) {
    val graph = LocalGraph.current
    if (tm.tracks.isEmpty()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nothing from your library was played in ${tm.label}.", style = MaterialTheme.typography.bodyLarge)
            Text(tm.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle("Top songs of ${tm.label}", top = 8.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = TIME_MACHINE_GAP).testTag("memoryButtons"),
            horizontalArrangement = Arrangement.spacedBy(TIME_MACHINE_GAP),
        ) {
            Button(
                onClick = { graph.player.play(tm.tracks.mapNotNull { it.song }) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
            OutlinedButton(onClick = onCreate) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text("Create memory")
            }
        }
    }
}

/**
 * "Create memory": asks where to keep it and what to call it ("Time Machine - March 2019"), then makes the
 * playlist with all the songs and gives it a picture of that time's top albums.
 */
@Composable
private fun CreateMemory(tm: TimeMachine, nav: NavController, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launch = rememberPlayLauncher()
    MemoryDialog(initial = memoryName(tm.label), onDismiss = onDone) { name, onPhone ->
        onDone()
        val songs = tm.tracks.mapNotNull { it.song }
        scope.launch {
            val made = graph.playlists.create(name, songs, onPhone)
            val playlist = made.getOrNull()
            if (made.isFailure) {
                launch.notify("Couldn't create the memory: ${made.exceptionOrNull()?.message}")
                return@launch
            }
            if (playlist != null) {
                MemoryCover.make(context.applicationContext, graph, songs, tm.label)?.let { bytes ->
                    graph.playlists.setPicture(playlist.id, bytes, "image/jpeg")
                        .onFailure { AppLog.w("TimeMachine", "Memory made without its picture: ${it.message}") }
                }
                AppLog.i("TimeMachine", "Memory \"$name\": ${songs.size} songs, ${if (onPhone) "on the phone" else "on Navidrome"}")
                launch.notify("Memory created")
                nav.navigate("playlist/${playlist.id}")
            } else {
                launch.notify("Memory created")
            }
        }
    }
}
