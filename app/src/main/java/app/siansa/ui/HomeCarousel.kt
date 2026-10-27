package app.siansa.ui

import app.siansa.data.Song
import app.siansa.data.SavedVibe
import app.siansa.data.pinnedVibes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Carousel
import app.siansa.data.carouselFor
import app.siansa.data.Mix
import app.siansa.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** Which carousel mixes are being made right now (selector ids), shared so leaving Home doesn't start them twice. */
private object CarouselBuilding {
    val ids = MutableStateFlow<Set<String>>(emptySet())
}

/**
 * Home's carousel (issue #253): the 3 or 4 mixes set in Vibes for this part of the day ("Your morning":
 * Wake up, Caffeine kick, Mellow morning…), one big card each to swipe through. They're made one after
 * another the first time Home shows them each day, each leaving out the songs of the ones before.
 */
@Composable
fun HomeCarousel(nav: NavController, aiDown: String?) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val carousels by graph.carousels.state.collectAsStateWithLifecycle()
    val mixes by graph.mixes.state.collectAsStateWithLifecycle()
    val building by CarouselBuilding.ids.collectAsStateWithLifecycle()
    val hour by produceState(LocalTime.now().hour) {
        while (true) {
            delay(60_000)
            value = LocalTime.now().hour
        }
    }
    LaunchedEffect(Unit) { graph.carousels.all() }
    // Vibes pinned on Dscvr come after the mixes, as an option (issue #339).
    val saved by graph.savedVibes.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.savedVibes.all() }
    val pinned = pinnedVibes(saved.orEmpty())
    // For this hour and this day of the week (issue #512).
    val found = carouselFor(carousels.orEmpty(), hour, LocalDate.now().dayOfWeek.value)?.takeIf { it.usable.size >= Carousel.MIN }
    if (found == null) {
        if (pinned.isNotEmpty()) PinnedVibesCarousel(nav, pinned)
        return
    }
    val carousel: Carousel = found
    val today = LocalDate.now().toString()
    // Today's mixes as they were made: showing other music in the source filter doesn't make them again (issue #486).
    val made: List<Mix?> = carousel.usable.indices.map { i ->
        val id = "$today-${carousel.selectorFor(i).id}"
        mixes.orEmpty().firstOrNull { it.id == id }
    }

    fun make(index: Int, open: Boolean) {
        val selectorId = carousel.selectorFor(index).id
        if (selectorId in CarouselBuilding.ids.value) return
        aiDown?.let { problem ->
            if (open) launch.notify("AI backend is currently not available :( $problem")
            return
        }
        CarouselBuilding.ids.value += selectorId
        graph.scope.launch {
            try {
                val mix = graph.generator.ensureCarousel(carousel, index)
                if (open) withContext(Dispatchers.Main) { nav.navigate("mix/${mix.id}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Home", "Carousel mix ${carousel.usable[index].name} failed: ${e.message}")
                if (open) withContext(Dispatchers.Main) { launch.notify(e.message ?: "Couldn't make the mix") }
            } finally {
                CarouselBuilding.ids.value -= selectorId
            }
        }
    }

    // The day's mixes, one after another (each leaves out the songs of the ones made before it).
    LaunchedEffect(carousel.id, carousel.version, today, aiDown) {
        if (aiDown != null) return@LaunchedEffect
        val missing = carousel.usable.indices.filter { i -> graph.mixes.get("$today-${carousel.selectorFor(i).id}") == null }
        if (missing.isEmpty() || CarouselBuilding.ids.value.isNotEmpty()) return@LaunchedEffect
        graph.scope.launch {
            for (i in missing) {
                val selectorId = carousel.selectorFor(i).id
                CarouselBuilding.ids.value += selectorId
                try {
                    graph.generator.ensureCarousel(carousel, i)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Home", "Carousel mix ${carousel.usable[i].name} failed: ${e.message}")
                } finally {
                    CarouselBuilding.ids.value -= selectorId
                }
            }
        }
    }

    val mixCount = carousel.usable.size
    val pager = rememberPagerState { mixCount + pinned.size }
    // Nothing under the dots: the next section's gap is the room below, as under every other row on Home (#560).
    Column(Modifier.fillMaxWidth().testTag("homeCarousel")) {
        SectionTitle(carousel.title)
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Space.Page),
            pageSpacing = Space.Medium,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            if (page >= mixCount) {
                pinned.getOrNull(page - mixCount)?.let { PinnedVibeCard(nav, it) }
                return@HorizontalPager
            }
            val mix = made[page]
            val selector = carousel.selectorFor(page)
            CarouselCard(
                name = carousel.usable[page].name,
                caption = when {
                    mix != null -> "${mix.songs.size} tracks · ${mix.description}"
                    selector.id in building -> "Making it…"
                    aiDown != null -> "AI backend not available :("
                    else -> carousel.usable[page].sound
                },
                colors = tileColors(selector.id.substringBeforeLast("-v") + page),
                coverArt = mix?.songs?.firstOrNull()?.coverArt,
                busy = selector.id in building,
                onOpen = { if (mix != null) nav.navigate("mix/${mix.id}") else make(page, open = true) },
                // Carries on where the mix was left (issue #269).
                onPlay = if (mix != null) ({ graph.mixResume.play(mix) }) else null,
                songs = mix?.songs.orEmpty(),
            )
        }
        CarouselDots(count = mixCount + pinned.size, current = pager.currentPage)
    }
}

/** Pinned vibes on their own, when no carousel covers this hour (issue #339). */
@Composable
private fun PinnedVibesCarousel(nav: NavController, pinned: List<SavedVibe>) {
    val pager = rememberPagerState { pinned.size }
    Column(Modifier.fillMaxWidth().testTag("homeCarousel")) {
        SectionTitle("Your vibes")
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Space.Page),
            pageSpacing = Space.Medium,
            modifier = Modifier.fillMaxWidth(),
        ) { page -> pinned.getOrNull(page)?.let { PinnedVibeCard(nav, it) } }
        CarouselDots(count = pinned.size, current = pager.currentPage)
    }
}

/** A vibe pinned on Dscvr: opens its page, plays its songs (issue #339). */
@Composable
private fun PinnedVibeCard(nav: NavController, vibe: SavedVibe) {
    val graph = LocalGraph.current
    CarouselCard(
        name = vibe.shownTitle,
        caption = "${vibe.songs.size} tracks · " + vibe.description,
        colors = tileColors(vibe.text.ifEmpty { vibe.title }),
        coverArt = vibe.songs.firstOrNull()?.coverArt,
        busy = false,
        onOpen = { nav.navigate("vibe/${vibe.id}") },
        onPlay = { graph.player.play(vibe.songs) },
        songs = vibe.songs,
    )
}

@Composable
private fun CarouselCard(
    name: String,
    caption: String,
    colors: Pair<Color, Color>,
    coverArt: String?,
    busy: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)?,
    // Its songs, so the button shows ⏸ while they're playing (#562).
    songs: List<Song> = emptyList(),
) {
    val graph = LocalGraph.current
    val card = rememberCardPlay(songs)
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .clickable(onClick = onOpen)
            .testTag("carouselCard"),
    ) {
        if (coverArt != null) {
            Cover(coverArt, 300, Modifier.size(96.dp).align(Alignment.TopEnd).padding(Space.Page), RoundedCornerShape(8.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(Space.Page).fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(Space.Page)) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp) // spacing: exact
                onPlay != null -> FilledIconButton(
                    onClick = { if (card.loaded) graph.player.togglePlay() else onPlay() },
                    modifier = Modifier.size(48.dp).testTag("carouselPlay"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = OnAccent),
                ) { Icon(if (card.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (card.playing) "Pause $name" else "Play $name", Modifier.size(28.dp)) }
            }
        }
    }
}

/** A dot per mix, the one showing filled. */
@Composable
internal fun CarouselDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.Medium).testTag("carouselDots"),
        horizontalArrangement = Arrangement.spacedBy(Space.Small, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(8.dp) // spacing: exact (a dot)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    .testTag("carouselDot"),
            )
        }
    }
}
