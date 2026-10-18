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
    val report by loadState(range) { graph.insights.report(range) }

    Box(Modifier.fillMaxSize().background(Brand.Plum)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(32.dp))) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Brand.VioletDeep, Brand.AmberDeep.copy(alpha = 0.55f), Brand.Plum)))
                        .statusBarsPadding()
                        .padding(bottom = 20.dp),
                ) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    val r = report.valueOrNull()
                    Text(
                        r?.title ?: "Your listening",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Text(
                        r?.subtitle.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    val n = NumberFormat.getIntegerInstance()

    // The big numbers.
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(n.format(r.minutes), "minutes listened", "${n.format(r.minutes / 60)} hours", Modifier.weight(1f), Brand.AmberDeep)
            Stat(n.format(r.plays), "plays", null, Modifier.weight(1f), Brand.VioletDeep)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtistPhoto(top, 104)
            Spacer(Modifier.width(16.dp))
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
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 2}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                ArtistPhoto(a, 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                        Box(
                            Modifier
                                .fillMaxWidth((a.plays.toFloat() / top.plays.coerceAtLeast(1)).coerceIn(0.05f, 1f))
                                .height(4.dp)
                                .background(Accent),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(n.format(a.plays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Top songs, playable as a list.
    if (r.topTracks.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Top songs", Modifier.weight(1f))
            val playable = r.topTracks.mapNotNull { it.song }
            if (playable.isNotEmpty()) {
                Button(
                    onClick = { graph.player.play(playable) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
            }
        }
        r.topTracks.take(10).forEachIndexed { i, t ->
            val song = t.song
            if (song != null) {
                SongRow(song = song, onClick = { graph.player.play(r.topTracks.mapNotNull { it.song }, r.topTracks.take(i).count { it.song != null }) }, leading = "${i + 1}")
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
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
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(r.rising) { a ->
                    Column(
                        Modifier.width(96.dp).clickable(enabled = a.artist != null) { a.artist?.let { nav.navigate("artist/${it.id}") } }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtistPhoto(a, 80)
                        Spacer(Modifier.height(4.dp))
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@Composable
private fun Stat(value: String, label: String, extra: String?, modifier: Modifier, color: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
            .padding(16.dp),
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
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val max = byHour.maxOrNull()?.coerceAtLeast(1) ?: 1
    val faint = Color.White.copy(alpha = 0.18f)
    val accent = Accent
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        listOf("00", "06", "12", "18", "23").forEachIndexed { i, label ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (i < 4) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Label(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<String>, background: Color) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(background).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
