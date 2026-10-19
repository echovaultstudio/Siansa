package app.musix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.mix.ListeningReport
import app.musix.mix.RankedArtist
import app.musix.mix.ReportRange
import java.text.NumberFormat

/** "Your month": top artists, songs and albums, your listening clock and how your taste moved. */
/** Always in dark colours: the page has its own dark background, so text must stay light in light mode too. */
@Composable
fun ReportScreen(nav: NavController, rangeId: String) {
    MusixDarkTheme { ReportPage(nav, rangeId) }
}

@Composable
private fun ReportPage(nav: NavController, rangeId: String) {
    val graph = LocalGraph.current
    var range by rememberSaveable { mutableStateOf(ReportRange.of(rangeId)) }
    // Another range shows the loading circle straight away, not the last report (like the time machine).
    val report by loadStateFresh(range) { graph.insights.report(range) }

    Box(Modifier.fillMaxSize().background(Brand.Plum)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Large))) {
            item {
                val r = report.valueOrNull()
                ListeningHeader(nav, r?.title ?: "Your listening", r?.subtitle.orEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.Page).padding(top = Space.Medium),
                        horizontalArrangement = Arrangement.spacedBy(Space.Small),
                    ) {
                        listOf(
                            ReportRange.THIS_MONTH to "This month",
                            ReportRange.LAST_MONTH to "Last month",
                            ReportRange.THIS_YEAR to "This year",
                            ReportRange.YEAR_AGO to "This time last year",
                        )
                            .forEach { (value, label) ->
                                FilterChip(selected = range == value, onClick = { range = value }, label = { Text(label) })
                            }
                    }
                }
            }
            item {
                LoadBox(report) { r -> Column { ReportBody(r, nav) } }
            }
        }
    }
}

@Composable
private fun ReportBody(r: ListeningReport, nav: NavController) {
    val graph = LocalGraph.current
    if (r.plays == 0) {
        Text(
            "Nothing played in this period.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Large),
        )
        return
    }
    val n = NumberFormat.getIntegerInstance()

    // The big numbers.
    Column(Modifier.padding(horizontal = Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
            Stat(n.format(r.minutes), "minutes listened", "${n.format(r.minutes / 60)} hours", Modifier.weight(1f), Brand.AmberDeep)
            Stat(n.format(r.plays), "plays", null, Modifier.weight(1f), Brand.VioletDeep)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
            Stat(n.format(r.artistCount), "artists", r.newArtists?.let { "$it new to you" }, Modifier.weight(1f), Brand.PlumHigh)
            Stat(n.format(r.trackCount), "different songs", null, Modifier.weight(1f), Brand.PlumHigh)
        }
    }

    // Top artists: number one big, the rest with a bar of how close they came.
    if (r.topArtists.isNotEmpty()) {
        SectionTitle("Top artists")
        val top = r.topArtists.first()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = top.artist != null) { top.artist?.let { nav.navigate("artist/${it.id}") } }
                .padding(horizontal = Space.Page, vertical = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtistPhoto(top, 104)
            Spacer(Modifier.width(Space.Page))
            Column {
                Text("#1", style = MaterialTheme.typography.titleMedium, color = Accent, fontWeight = FontWeight.Bold)
                Text(top.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${n.format(top.plays)} plays", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        r.topArtists.drop(1).take(4).forEachIndexed { i, a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = a.artist != null) { a.artist?.let { nav.navigate("artist/${it.id}") } }
                    .padding(horizontal = Space.Page, vertical = Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 2}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                ArtistPhoto(a, 44)
                Spacer(Modifier.width(Space.Medium))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = Space.Tight).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                        Box(
                            Modifier
                                .fillMaxWidth((a.plays.toFloat() / top.plays.coerceAtLeast(1)).coerceIn(0.05f, 1f))
                                .height(4.dp)
                                .background(Accent),
                        )
                    }
                }
                Spacer(Modifier.width(Space.Medium))
                Text(n.format(a.plays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Top songs, playable as a list.
    if (r.topTracks.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(end = Space.Page), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Top songs", Modifier.weight(1f))
            val playable = r.topTracks.mapNotNull { it.song }
            if (playable.isNotEmpty()) {
                Button(
                    onClick = { graph.player.play(playable) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(Space.Tight))
                    Text("Play")
                }
            }
        }
        r.topTracks.take(10).forEachIndexed { i, t ->
            val song = t.song
            if (song != null) {
                SongRow(song = song, onClick = { graph.player.play(r.topTracks.mapNotNull { it.song }, r.topTracks.take(i).count { it.song != null }) }, leading = "${i + 1}")
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Medium), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text("${t.plays}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (r.topAlbums.isNotEmpty()) {
        SectionTitle("Top albums")
        LazyRow(contentPadding = PaddingValues(horizontal = Space.Small)) {
            items(r.topAlbums) { a ->
                AlbumTile(
                    name = a.title,
                    subtitle = "${a.artist} · ${a.plays} plays",
                    coverArt = a.album?.coverArt,
                    onClick = { a.album?.let { nav.navigate("album/${it.id}") } },
                    modifier = Modifier.width(150.dp),
                )
            }
        }
    }

    ListeningClock(r.byHour, r.peakHour)

    // How your taste moved against the period before.
    if (r.rising.isNotEmpty() || r.fading.isNotEmpty() || r.genresUp.isNotEmpty() || r.genresDown.isNotEmpty()) {
        SectionTitle("How your taste moved")
        if (r.rising.isNotEmpty()) {
            Label(Icons.AutoMirrored.Rounded.TrendingUp, "New in your rotation", Accent)
            LazyRow(contentPadding = PaddingValues(horizontal = Space.Medium), horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                items(r.rising) { a ->
                    Column(
                        Modifier.width(96.dp).clickable(enabled = a.artist != null) { a.artist?.let { nav.navigate("artist/${it.id}") } }.padding(Space.Tight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtistPhoto(a, 80)
                        Spacer(Modifier.height(Space.Tight))
                        Text(a.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (r.genresUp.isNotEmpty()) {
            Label(Icons.AutoMirrored.Rounded.TrendingUp, "More of", Accent)
            Chips(r.genresUp, Accent.copy(alpha = 0.18f))
        }
        if (r.fading.isNotEmpty() || r.genresDown.isNotEmpty()) {
            Label(Icons.AutoMirrored.Rounded.TrendingDown, "Less of", Brand.Violet)
            Chips(r.fading + r.genresDown, Brand.Violet.copy(alpha = 0.16f))
        }
    }

    Text(
        r.source,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Section),
    )
}

@Composable
private fun Stat(value: String, label: String, extra: String?, modifier: Modifier, color: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
            .padding(Space.Page),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        if (extra != null) Text(extra, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun ArtistPhoto(a: RankedArtist, sizeDp: Int) {
    val artist = a.artist
    if (artist != null) {
        ArtistCircle(artist, Modifier.size(sizeDp.dp))
    } else {
        Box(
            Modifier.size(sizeDp.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tileColors(a.name).first, tileColors(a.name).second))),
            contentAlignment = Alignment.Center,
        ) {
            Text(a.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Plays across the day as 24 bars, with the busiest hour lit and a name for the habit. */
@Composable
private fun ListeningClock(byHour: IntArray, peak: Int?) {
    if (byHour.sum() == 0) return
    SectionTitle("Your listening clock")
    val persona = when (peak) {
        null -> null
        in 5..9 -> "Early bird"
        in 10..16 -> "Daytime listener"
        in 17..21 -> "Evening unwinder"
        else -> "Night owl"
    }
    if (peak != null && persona != null) {
        Text(
            "$persona · most plays around %02d:00".format(peak),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Space.Page),
        )
    }
    val max = byHour.maxOrNull()?.coerceAtLeast(1) ?: 1
    val faint = Color.White.copy(alpha = 0.18f)
    val accent = Accent
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = Space.Page, vertical = Space.Medium)) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * 23) / 24
        byHour.forEachIndexed { hour, count ->
            val h = (count.toFloat() / max) * size.height
            drawRoundRect(
                color = if (hour == peak) accent else if (count > 0) Brand.Violet.copy(alpha = 0.75f) else faint,
                topLeft = Offset(hour * (barWidth + gap), size.height - h.coerceAtLeast(2f)),
                size = Size(barWidth, h.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page)) {
        listOf("00", "06", "12", "18", "23").forEachIndexed { i, label ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (i < 4) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Label(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(Modifier.padding(horizontal = Space.Page, vertical = Space.Small), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.Small))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<String>, background: Color) {
    FlowRow(
        Modifier.padding(horizontal = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
        verticalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        items.forEach { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(background).padding(horizontal = Space.Medium, vertical = Space.Small),
            )
        }
    }
}
