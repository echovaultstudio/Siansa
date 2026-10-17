package app.musix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.data.normalizeKey
import app.musix.mix.SuggestedArtist
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The connection map on the "Other artists you might like" page: you in the middle, the artists you play
 * most around you, and the suggested artists on the outside, each joined to the artists of yours it's
 * like. Positions are in a unit square (0..1), so the map is pure and tested; the screen scales it.
 */
object DiscoveryMap {
    data class Node(val name: String, val x: Float, val y: Float, val weight: Float)
    data class Edge(val from: String, val to: String, val weight: Float)
    data class Graph(val seeds: List<Node>, val suggestions: List<Node>, val edges: List<Edge>)

    const val SEED_RING = 0.24f
    const val OUTER_RING = 0.42f

    /**
     * [suggestions] with the artists they're like, and how much you play each of your artists
     * ([plays], by display name). Only your artists that some suggestion is like are shown.
     */
    fun build(suggestions: List<SuggestedArtist>, plays: Map<String, Int>): Graph {
        val seedNames = suggestions.flatMap { it.because }.distinctBy(::normalizeKey)
        if (seedNames.isEmpty()) return Graph(emptyList(), emptyList(), emptyList())
        val most = seedNames.maxOf { plays[it] ?: 0 }.coerceAtLeast(1)
        val seeds = seedNames.mapIndexed { i, name ->
            val angle = angleOf(i, seedNames.size)
            Node(name, 0.5f + SEED_RING * cos(angle), 0.5f + SEED_RING * sin(angle), ((plays[name] ?: 0).toFloat() / most).coerceIn(0.15f, 1f))
        }
        val seedAngle = seedNames.withIndex().associate { (i, n) -> normalizeKey(n) to angleOf(i, seedNames.size) }
        // Each suggestion sits out from the artists of yours it's like (the average of their directions),
        // then the ring is spread so neighbours never overlap.
        val wanted = suggestions.map { s ->
            val angles = s.because.mapNotNull { seedAngle[normalizeKey(it)] }
            s to (if (angles.isEmpty()) 0f else circularMean(angles))
        }.sortedBy { it.second }
        val spread = spreadAngles(wanted.map { it.second }, (2 * PI / suggestions.size.coerceAtLeast(1) * 0.8).toFloat())
        val outer = wanted.mapIndexed { i, (s, _) ->
            val a = spread[i]
            Node(s.name, 0.5f + OUTER_RING * cos(a), 0.5f + OUTER_RING * sin(a), 1f)
        }
        val edges = suggestions.flatMap { s -> s.because.mapNotNull { b -> seeds.firstOrNull { normalizeKey(it.name) == normalizeKey(b) }?.let { Edge(it.name, s.name, it.weight) } } }
        return Graph(seeds, outer, edges)
    }

    /** Evenly round the circle, starting at the top. */
    private fun angleOf(i: Int, n: Int): Float = (-PI / 2 + 2 * PI * i / n.coerceAtLeast(1)).toFloat()

    private fun circularMean(angles: List<Float>): Float {
        val x = angles.sumOf { cos(it).toDouble() }
        val y = angles.sumOf { sin(it).toDouble() }
        return kotlin.math.atan2(y, x).toFloat()
    }

    /** [sorted] angles pushed apart until neighbours are at least [gap] apart, keeping their order. */
    fun spreadAngles(sorted: List<Float>, gap: Float): List<Float> {
        if (sorted.size < 2) return sorted
        val out = sorted.toMutableList()
        repeat(40) {
            var moved = false
            for (i in out.indices) {
                val next = (i + 1) % out.size
                var d = out[next] - out[i]
                if (next == 0) d += (2 * PI).toFloat()
                if (d < gap) {
                    val push = (gap - d) / 2
                    out[i] -= push
                    out[next] += push
                    moved = true
                }
            }
            if (!moved) return out
        }
        return out
    }

    /** How sure a suggestion is, for its card: how many of your artists it's like, and how much you play them. */
    fun reasons(s: SuggestedArtist, plays: Map<String, Int>): List<String> = buildList {
        similarToText(s.because)?.let(::add)
        val played = s.because.mapNotNull { b -> plays[b]?.takeIf { it > 0 }?.let { b to it } }.maxByOrNull { it.second }
        played?.let { (name, n) -> add("You've played $name ${if (n == 1) "once" else "$n times"} lately") }
        if (s.fans > 0) add("${shortCount(s.fans)} people listen to them on Deezer")
        if (s.albums > 0) add(if (s.albums == 1) "1 album to discover" else "${s.albums} albums to discover")
    }
}

/**
 * "Other artists you might like", under Your Library → Your listening: a map of how each suggested artist
 * connects to the ones you play, and a card per artist with why. Tap an artist for more and a Deezer link.
 */
@Composable
fun ArtistDiscoveryScreen(nav: NavController) {
    val graph = LocalGraph.current
    val data by loadState("discovery") {
        val suggestions = graph.suggestions.today()
        val seeds = suggestions.flatMap { it.because }.distinctBy(::normalizeKey)
        val keys = seeds.associateBy(::normalizeKey)
        val counts = HashMap<String, Int>()
        graph.history.all().filter { !it.skipped }.forEach { ev ->
            keys[normalizeKey(ev.artist)]?.let { name -> counts.merge(name, 1, Int::plus) }
        }
        suggestions to counts
    }
    var open by remember { mutableStateOf<SuggestedArtist?>(null) }
    Screen(title = "Other artists you might like", nav = nav) { padding ->
        LoadBox(data, Modifier.padding(padding)) { (suggestions, plays) ->
            if (suggestions.isEmpty()) {
                Text(
                    "Nothing yet. Suggestions are made once a day from the artists you play most, after a library sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = padding.calculateTopPadding()).padding(16.dp),
                )
                return@LoadBox
            }
            val map = remember(suggestions, plays) { DiscoveryMap.build(suggestions, plays) }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomBarPadding(24.dp)),
            ) {
                item {
                    Text(
                        "Artists you don't have yet, like the ones you play most. Lines join each one to your artists it's like; the brighter the line, the more you play that artist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item { ConnectionMap(map, suggestions) { open = it } }
                item { SectionTitle("Why you might like them") }
                items(suggestions, key = { it.name }) { s -> ReasonCard(s, DiscoveryMap.reasons(s, plays)) { open = s } }
            }
        }
    }
    open?.let { SuggestedArtistDialog(it) { open = null } }
}

@Composable
private fun ConnectionMap(map: DiscoveryMap.Graph, suggestions: List<SuggestedArtist>, onOpen: (SuggestedArtist) -> Unit) {
    val line = MaterialTheme.colorScheme.secondary
    val you = Accent
    BoxWithConstraints(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            fun at(n: DiscoveryMap.Node) = Offset(n.x * size.width, n.y * size.height)
            val centre = Offset(size.width / 2, size.height / 2)
            // You → your artists: thicker the more you play them.
            map.seeds.forEach { s -> drawLine(you.copy(alpha = 0.25f + 0.5f * s.weight), centre, at(s), strokeWidth = 2f + 6f * s.weight) }
            // Your artists → the suggestions they lead to.
            map.edges.forEach { e ->
                val from = map.seeds.firstOrNull { it.name == e.from } ?: return@forEach
                val to = map.suggestions.firstOrNull { it.name == e.to } ?: return@forEach
                drawLine(line.copy(alpha = 0.2f + 0.6f * e.weight), at(from), at(to), strokeWidth = 1.5f + 3f * e.weight)
            }
            map.seeds.forEach { s -> drawCircle(you, radius = 7f + 9f * s.weight, center = at(s)) }
            drawCircle(you, radius = 26f, center = centre)
        }
        // Labels and photos over the drawing, so they're sharp and tappable.
        Text(
            "You",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2A1700),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp).offset(x = w * 0.5f - 24.dp, y = w * 0.5f - 9.dp),
        )
        map.seeds.forEach { s ->
            Text(
                s.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(84.dp).offset(x = w * s.x - 42.dp, y = w * s.y + 12.dp),
            )
        }
        map.suggestions.forEach { n ->
            val artist = suggestions.firstOrNull { it.name == n.name } ?: return@forEach
            Column(
                Modifier.width(76.dp).offset(x = w * n.x - 38.dp, y = w * n.y - 22.dp).clickable { onOpen(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = artist.photo,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape).border(2.dp, line, CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(artist.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ReasonCard(artist: SuggestedArtist, reasons: List<String>, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artist.photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            reasons.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Accent))
                    Spacer(Modifier.width(8.dp))
                    Text(r, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The smallest angle between two directions, in radians. */
internal fun angleGap(a: Float, b: Float): Float {
    val d = abs(a - b) % (2 * PI).toFloat()
    return if (d > PI) (2 * PI).toFloat() - d else d
}
