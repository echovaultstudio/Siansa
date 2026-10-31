package app.siansa.ui

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
import app.siansa.data.normalizeKey
import app.siansa.mix.SuggestedArtist
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
    const val OUTER_RING = 0.40f

    /**
     * [suggestions] with the artists they're like, and how much you play each of your artists
     * ([plays], by display name). Only your artists that some suggestion is like are shown.
     */
    fun build(suggestions: List<SuggestedArtist>, plays: Map<String, Int>, maxSeeds: Int = Int.MAX_VALUE): Graph {
        val allSeeds = suggestions.flatMap { it.because }.distinctBy(::normalizeKey)
        // With too many of your artists for the map, the ones you play most (in their original order).
        val keep = allSeeds.sortedByDescending { plays[it] ?: 0 }.take(maxSeeds).map(::normalizeKey).toSet()
        val seedNames = allSeeds.filter { normalizeKey(it) in keep }
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
        repeat(200) {
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

    /**
     * How many suggestions and of your artists fit on a map [widthDp] wide without their photos and names
     * overlapping (worked out for maps 264 to 520 dp wide): 8 and 6, or 6 and 5 on narrow phones. The cards
     * below the map list every suggestion.
     */
    fun limits(widthDp: Float): Pair<Int, Int> = if (widthDp >= 312f) 8 to 6 else 6 to 5

    /** A label on the map: its centre and size (in dp), and whether it may move ("You" stays put). */
    data class Box(val cx: Float, val cy: Float, val w: Float, val h: Float, val fixed: Boolean = false)

    /**
     * [boxes] moved apart until none overlap (with [gap] between them), all inside a [width] by [height]
     * area. Each overlapping pair is pushed apart along the axis it overlaps least on, or the other axis when
     * the card's edge stops that, and a fixed box doesn't move. Spreading by angle alone, as before, let
     * photos and names pile up when there were many suggestions.
     */
    fun separate(boxes: List<Box>, width: Float, height: Float, gap: Float, rounds: Int = 400): List<Box> {
        val out = boxes.toMutableList()
        fun clamp(b: Box) = if (b.fixed) b else b.copy(
            cx = b.cx.coerceIn(b.w / 2, (width - b.w / 2).coerceAtLeast(b.w / 2)),
            cy = b.cy.coerceIn(b.h / 2, (height - b.h / 2).coerceAtLeast(b.h / 2)),
        )
        for (i in out.indices) out[i] = clamp(out[i])
        repeat(rounds) {
            var moved = false
            for (i in out.indices) for (j in i + 1 until out.size) {
                val a = out[i]
                val b = out[j]
                if (a.fixed && b.fixed) continue
                val overlapX = (a.w + b.w) / 2 + gap - abs(b.cx - a.cx)
                val overlapY = (a.h + b.h) / 2 + gap - abs(b.cy - a.cy)
                if (overlapX <= 0f || overlapY <= 0f) continue
                moved = true
                val shareA = if (a.fixed) 0f else if (b.fixed) 1f else 0.5f
                val shareB = 1f - shareA
                // The pair after pushing along one axis, and how much they still overlap on it.
                fun pushed(alongX: Boolean): Triple<Box, Box, Float> {
                    val d = if (alongX) b.cx - a.cx else b.cy - a.cy
                    val sign = if (d > 0f) 1f else if (d < 0f) -1f else if ((i + j) % 2 == 0) 1f else -1f
                    val push = if (alongX) overlapX else overlapY
                    val na = clamp(if (alongX) a.copy(cx = a.cx - sign * push * shareA) else a.copy(cy = a.cy - sign * push * shareA))
                    val nb = clamp(if (alongX) b.copy(cx = b.cx + sign * push * shareB) else b.copy(cy = b.cy + sign * push * shareB))
                    val left = if (alongX) (a.w + b.w) / 2 + gap - abs(nb.cx - na.cx) else (a.h + b.h) / 2 + gap - abs(nb.cy - na.cy)
                    return Triple(na, nb, left)
                }
                var best = pushed(overlapX < overlapY)
                if (best.third > 0.5f) pushed(overlapX >= overlapY).let { if (it.third < best.third) best = it }
                out[i] = best.first
                out[j] = best.second
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
 * Dark, with the same header as the other Your listening pages.
 */
@Composable
fun ArtistDiscoveryScreen(nav: NavController) {
    SiansaDarkTheme { DiscoveryPage(nav) }
}

@Composable
private fun DiscoveryPage(nav: NavController) {
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Large))) {
            item { ListeningHeader(nav, "Other artists you might like", "Like the artists you play most. New ones each day.") }
            item {
                LoadBox(data) { (suggestions, plays) ->
                    if (suggestions.isEmpty()) {
                        Text(
                            "Nothing yet. Suggestions are made once a day from the artists you play most, after a library sync.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Large),
                        )
                        return@LoadBox
                    }
                    Column {
                        Text(
                            "Lines join each artist to the ones you play that it's like. The thicker the line, the more you play that artist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.Page),
                        )
                        Spacer(Modifier.height(Space.Medium))
                        ConnectionMap(suggestions, plays) { open = it }
                        SectionTitle("Why you might like them")
                        Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                            suggestions.forEach { s -> ReasonCard(s, DiscoveryMap.reasons(s, plays)) { open = s } }
                        }
                    }
                }
            }
        }
    }
    open?.let { SuggestedArtistDialog(it) { open = null } }
}

/** Sizes on the map, in dp: "You", one of your artists (dot and name) and a suggestion (photo and name). */
private const val YOU_DP = 56f
private const val SEED_W = 80f
private const val SEED_H = 34f
private const val SUGGESTION_W = 72f
private const val SUGGESTION_H = 58f
private const val PHOTO_DP = 40f
/** The space kept between any two of them. */
private const val MAP_GAP = 4f

@Composable
private fun ConnectionMap(all: List<SuggestedArtist>, plays: Map<String, Int>, onOpen: (SuggestedArtist) -> Unit) {
    val line = MaterialTheme.colorScheme.secondary
    val you = Accent
    BoxWithConstraints(
        Modifier
            .padding(horizontal = Space.Page)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Space.Page))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val w = maxWidth.value
        // As many as fit this wide a map; the cards below list them all.
        val (maxSuggestions, maxSeeds) = DiscoveryMap.limits(w)
        val suggestions = remember(all, maxSuggestions) { all.take(maxSuggestions) }
        val map = remember(suggestions, plays, maxSeeds) { DiscoveryMap.build(suggestions, plays, maxSeeds) }
        // Every name and photo as a box, moved apart so none overlap and all stay inside the card.
        val placed = remember(map, w) {
            val boxes = buildList {
                add(DiscoveryMap.Box(w / 2, w / 2, YOU_DP, YOU_DP, fixed = true))
                map.seeds.forEach { add(DiscoveryMap.Box(it.x * w, it.y * w + SEED_H / 2 - 8f, SEED_W, SEED_H)) }
                map.suggestions.forEach { add(DiscoveryMap.Box(it.x * w, it.y * w + SUGGESTION_H / 2 - PHOTO_DP / 2, SUGGESTION_W, SUGGESTION_H)) }
            }
            DiscoveryMap.separate(boxes, w, w, MAP_GAP)
        }
        val seedBoxes = placed.subList(1, 1 + map.seeds.size)
        val suggestionBoxes = placed.subList(1 + map.seeds.size, placed.size)
        // Where lines meet each one: a seed's dot (top of its box), a suggestion's photo (its middle).
        fun seedDot(b: DiscoveryMap.Box) = Offset(b.cx, b.cy - b.h / 2 + 8f)
        fun photo(b: DiscoveryMap.Box) = Offset(b.cx, b.cy - b.h / 2 + PHOTO_DP / 2)
        val seedAt = map.seeds.indices.associate { map.seeds[it].name to seedDot(seedBoxes[it]) }
        val suggestionAt = map.suggestions.indices.associate { map.suggestions[it].name to photo(suggestionBoxes[it]) }
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / w
            fun px(o: Offset) = Offset(o.x * k, o.y * k)
            val centre = Offset(size.width / 2, size.height / 2)
            // You → your artists: thicker the more you play them.
            map.seeds.forEach { s ->
                val at = seedAt[s.name] ?: return@forEach
                drawLine(you.copy(alpha = 0.25f + 0.5f * s.weight), centre, px(at), strokeWidth = (1.5f + 4f * s.weight) * k)
            }
            // Your artists → the suggestions they lead to.
            map.edges.forEach { e ->
                val from = seedAt[e.from] ?: return@forEach
                val to = suggestionAt[e.to] ?: return@forEach
                drawLine(line.copy(alpha = 0.2f + 0.6f * e.weight), px(from), px(to), strokeWidth = (1f + 2f * e.weight) * k)
            }
            map.seeds.forEach { s -> seedAt[s.name]?.let { drawCircle(you, radius = (4f + 4f * s.weight) * k, center = px(it)) } }
            drawCircle(you, radius = YOU_DP / 2 * k, center = centre)
        }
        // Names and photos over the drawing, so they're sharp and tappable.
        Text(
            "You",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = OnAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(YOU_DP.dp).offset(x = (w / 2 - YOU_DP / 2).dp, y = (w / 2 - 9f).dp),
        )
        map.seeds.forEachIndexed { i, s ->
            val b = seedBoxes[i]
            Text(
                s.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(b.w.dp).offset(x = (b.cx - b.w / 2).dp, y = (b.cy - b.h / 2 + 16f).dp),
            )
        }
        map.suggestions.forEachIndexed { i, n ->
            val artist = suggestions.firstOrNull { it.name == n.name } ?: return@forEachIndexed
            val b = suggestionBoxes[i]
            Column(
                Modifier
                    .width(b.w.dp)
                    .offset(x = (b.cx - b.w / 2).dp, y = (b.cy - b.h / 2).dp)
                    .clip(RoundedCornerShape(Space.Small))
                    .clickable { onOpen(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = artist.photo,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(PHOTO_DP.dp).clip(CircleShape).border(2.dp, line, CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    artist.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.Tight),
                )
            }
        }
    }
}

/** One suggested artist and why, as a card on the page's edges. */
@Composable
private fun ReasonCard(artist: SuggestedArtist, reasons: List<String>, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = Space.Page)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.Medium))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artist.photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            reasons.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Accent))
                    Spacer(Modifier.width(Space.Small))
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
