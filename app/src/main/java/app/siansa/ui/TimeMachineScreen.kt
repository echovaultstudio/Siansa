package app.siansa.ui

import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MusicOff
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.log.AppLog
import app.siansa.mix.RankedTrack
import app.siansa.mix.TIME_MACHINE_FIRST_YEAR
import app.siansa.mix.TimeMachine
import app.siansa.mix.memoryName
import app.siansa.mix.monthsAvailable
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** The even gap between the time machine's year, months and buttons. */
internal val TIME_MACHINE_GAP: Dp = Space.Medium

/**
 * The time machine (issue #186), under Your listening: pick a year and a month (or the whole year) and see
 * the songs you played most then, from Koito and Last.fm. Songs play, have the ⋮ menu and swipe to the queue
 * like everywhere else. Ones not in your library are greyed out, and a switch hides them (issue #234).
 * "Create memory" makes the library ones a playlist with a picture.
 */
@Composable
fun TimeMachineScreen(nav: NavController) {
    SiansaDarkTheme { TimeMachinePage(nav) }
}

@Composable
private fun TimeMachinePage(nav: NavController) {
    val graph = LocalGraph.current
    val today = remember { LocalDate.now() }
    // A year ago by default: the most likely time to want to go back to.
    var year by rememberSaveable { mutableIntStateOf(today.year - 1) }
    var month by rememberSaveable { mutableStateOf<Int?>(today.monthValue) }
    // A new year or month shows the spinner straight away, not the last list (issue #189). Then what's already
    // there shows, and each service's plays are added as they arrive (issue #319).
    val result by produceState<Load<TimeMachine>>(Load.Loading, year, month) {
        value = Load.Loading
        try {
            graph.insights.timeMachineUpdates(year, month).collect { value = Load.Ok(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (value !is Load.Ok) value = Load.Err(e.message ?: "Couldn't look back in time")
        }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val showMissing = settings.timeMachineMissing

    Box(Modifier.fillMaxSize().background(Brand.Plum)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Large))) {
            item {
                ListeningHeader(nav, "Time machine", "The songs you played most, any month or year.") {
                    Spacer(Modifier.height(Space.Small))
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
                LoadBox(result) { tm ->
                    TimeMachineSongs(tm, showMissing, onShowMissing = graph.settings::setTimeMachineMissing, onCreate = { creating = true })
                }
            }
            val tm = result.valueOrNull()
            if (tm != null && tm.tracks.isNotEmpty()) {
                val songs = tm.songs
                // The rank is the place in the list as shown, with or without the missing songs.
                val shown = tm.tracks.withIndex().filter { showMissing || it.value.song != null }
                itemsIndexed(shown, key = { _, (i, t) -> "$i-${t.song?.id}" }) { place, (_, t) ->
                    val song = t.song
                    if (song == null) {
                        MissingSongRow(t, leading = "${place + 1}")
                    } else {
                        SongRow(song = song, onClick = { graph.player.play(songs, songs.indexOf(song)) }, leading = "${place + 1}")
                    }
                }
                item {
                    Text(
                        tm.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Section),
                    )
                }
            }
        }
    }
    result.valueOrNull()?.let { tm ->
        if (creating) CreateMemory(tm, nav) { creating = false }
    }
}

/**
 * Where to scroll a row of chips so the chip at [left] (from the row's start, padding included) and [width] sits in
 * the middle of a [viewport]-wide window, within 0..[max] (issue #496).
 */
internal fun chipScrollTarget(left: Int, width: Int, viewport: Int, max: Int): Int =
    (left + width / 2 - viewport / 2).coerceIn(0, max.coerceAtLeast(0))

/** The year, with arrows either side, and the months under it (the whole year first). */
@Composable
internal fun TimePicker(year: Int, month: Int?, today: LocalDate, onYear: (Int) -> Unit, onMonth: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("timePicker"), verticalArrangement = Arrangement.spacedBy(TIME_MACHINE_GAP)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.Small).testTag("year"), verticalAlignment = Alignment.CenterVertically) {
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
        // The chosen month's chip is scrolled into view, centred (issue #496): October sat far off to the right.
        val scroll = rememberScrollState()
        val chips = remember { mutableStateMapOf<Int, Pair<Int, Int>>() } // month (0 = whole year) → left, width
        val pad = with(LocalDensity.current) { Space.Page.roundToPx() }
        var shown by remember { mutableStateOf(false) }
        val key = month ?: 0
        LaunchedEffect(year, key, chips[key], scroll.viewportSize) {
            val (left, width) = chips[key] ?: return@LaunchedEffect
            if (scroll.viewportSize <= 0) return@LaunchedEffect
            val to = chipScrollTarget(left + pad, width, scroll.viewportSize, scroll.maxValue)
            // Straight there when the page opens; a short glide when you pick one.
            if (shown) scroll.animateScrollTo(to) else scroll.scrollTo(to)
            shown = true
        }
        Row(
            Modifier.horizontalScroll(scroll).padding(horizontal = Space.Page).testTag("months"),
            horizontalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            FilterChip(
                selected = month == null,
                onClick = { onMonth(null) },
                label = { Text("Whole year") },
                modifier = Modifier.onPlaced { chips[0] = it.positionInParent().x.toInt() to it.size.width }.testTag("month-0"),
            )
            for (m in available) {
                val name = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.')
                FilterChip(
                    selected = month == m,
                    onClick = { onMonth(m) },
                    label = { Text(name) },
                    modifier = Modifier.onPlaced { chips[m] = it.positionInParent().x.toInt() to it.size.width }.testTag("month-$m"),
                )
            }
        }
    }
}

/** What the time machine found: Play, Create memory and the switch for songs not in your library, or why there's nothing. */
@Composable
private fun TimeMachineSongs(tm: TimeMachine, showMissing: Boolean, onShowMissing: (Boolean) -> Unit, onCreate: () -> Unit) {
    val graph = LocalGraph.current
    if (tm.tracks.isEmpty()) {
        Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text("Nothing was played in ${tm.label}.", style = MaterialTheme.typography.bodyLarge)
            Text(tm.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (tm.songs.isEmpty()) {
        Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text("None of the songs you played in ${tm.label} are in your library.", style = MaterialTheme.typography.bodyLarge)
            MissingSwitch(tm.missing, showMissing, onShowMissing)
        }
        return
    }
    Column(Modifier.padding(top = Space.Small)) {
        SectionTitle("Top songs of ${tm.label}", top = 8.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.Page).padding(bottom = TIME_MACHINE_GAP).testTag("memoryButtons"),
            horizontalArrangement = Arrangement.spacedBy(TIME_MACHINE_GAP),
        ) {
            Button(
                onClick = { graph.player.play(tm.songs) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(Space.Tight))
                Text("Play")
            }
            OutlinedButton(onClick = onCreate) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(Space.Small))
                Text("Create memory")
            }
        }
        if (tm.missing > 0) {
            Box(Modifier.padding(horizontal = Space.Page).padding(bottom = Space.Small)) { MissingSwitch(tm.missing, showMissing, onShowMissing) }
        }
    }
}

/** Shows or hides the songs that aren't in your library (issue #234); remembered for next time. */
@Composable
private fun MissingSwitch(count: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.testTag("missingSwitch")) { SwitchRow("Songs not in your library ($count)", checked, onChange) }
}

/**
 * A song you played that isn't in your library: greyed out with an empty cover, "Not in your library" under
 * the artist, and nothing to play. Lines up with [SongRow].
 */
@Composable
private fun MissingSongRow(track: RankedTrack, leading: String) {
    val launch = rememberPlayLauncher()
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .clickable { launch.notify("${track.title} isn't in your library") }
            .padding(horizontal = Space.Page, vertical = Space.Small)
            .testTag("missingSong"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            leading,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.artist} · Not in your library",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        val songs = tm.songs
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
