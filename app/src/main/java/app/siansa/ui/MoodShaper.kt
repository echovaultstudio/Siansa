package app.siansa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.siansa.data.Genre
import app.siansa.mix.FlowMood
import app.siansa.mix.MixSound
import app.siansa.mix.PadDot
import app.siansa.mix.SoundDial
import app.siansa.mix.VibeTuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Where a finger holds the pad's dot, while it's down: tempo across ([Offset.x]) and energy up ([Offset.y]), −1…1. */
@Stable
internal class MoodHold {
    var at by mutableStateOf<Offset?>(null)
}

/** How a mood looks and what it's called: its colour, the deep shade of it, the ink on it, its name and what it does. */
@Immutable
internal class MoodLook(val energy: Float, val tempo: Float) {
    private val rgb: IntArray = FlowMood.colour(energy, tempo)
    val colour: Color = Color(rgb[0], rgb[1], rgb[2])
    val deep: Color = Color((rgb[0] * FlowMood.DEEP).roundToInt(), (rgb[1] * FlowMood.DEEP).roundToInt(), (rgb[2] * FlowMood.DEEP).roundToInt())
    val ink: Color = if (FlowMood.darkInk(rgb)) MOOD_DARK_INK else Color.White
    val name: String = FlowMood.name(energy, tempo)
    val about: String = FlowMood.about(energy, tempo)
}

/** The look of [tuning]'s mood: it follows the finger while it's down ([hold]) and glides to the step it lands on. */
@Composable
internal fun rememberMoodLook(tuning: VibeTuning, hold: MoodHold): MoodLook {
    val held = hold.at
    val spec = if (held != null) snap<Float>() else spring()
    val energy by animateFloatAsState(held?.y ?: FlowMood.at(tuning.energy), spec, label = "moodEnergy")
    val tempo by animateFloatAsState(held?.x ?: FlowMood.at(tuning.tempo), spec, label = "moodTempo")
    return remember(energy, tempo) { MoodLook(energy, tempo) }
}

/**
 * Shaping a vibe (the owner's pick, 15 Nov: the mood pad): the pad with the mood's name under it, the sounds and
 * genres picked (each one "more" or "less", tapped through more, less, gone) with + to pick from a list, and whose
 * music it is. [dots] are a mix's songs on the pad, [inMix] the sounds a mix has of its own (a vibe's page).
 * [onSettled] runs when a finger is lifted or a pick changes, after [onChange] has given the new tuning.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodShaper(
    tuning: VibeTuning,
    look: MoodLook,
    hold: MoodHold,
    onChange: (VibeTuning) -> Unit,
    modifier: Modifier = Modifier,
    onSettled: () -> Unit = {},
    dots: List<PadDot> = emptyList(),
    inMix: List<MixSound> = emptyList(),
    picker: @Composable (onDone: () -> Unit) -> Unit = { done -> MoodPickerSheet(tuning, look, inMix, { onChange(it); onSettled() }, done) },
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    fun set(next: VibeTuning) {
        onChange(next)
        onSettled()
    }
    if (picking) picker { picking = false }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        MoodPad(tuning.energy, tempo = tuning.tempo, look = look, hold = hold, dots = dots,
            onMove = { e, t -> onChange(tuning.copy(energy = e, tempo = t)) }, onSettled = onSettled)
        Column(Modifier.testTag("moodWords")) {
            Text(look.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.testTag("moodName"))
            Text(look.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoodLabel("Sounds and genres")
        FlowRow(
            Modifier.testTag("moodPicks"),
            horizontalArrangement = Arrangement.spacedBy(Space.Small),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            FlowMood.picks(tuning).forEach { dial -> MoodChip(dial, look) { set(FlowMood.tap(tuning, dial.term)) } }
            AddChip { picking = true }
        }
        MoodLabel("Artists")
        MoodSegment(ARTISTS, FlowMood.artists(tuning.familiar), "moodArtists") { set(tuning.copy(familiar = FlowMood.familiar(it))) }
    }
}

/** A small heading over a part of the shaper, in capitals. */
@Composable
private fun MoodLabel(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.Tight).testTag("moodLabel"),
    )
}

/** A sound or genre as it's shown: sounds come from the search in small letters. */
internal fun shownSound(term: String): String = term.replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * A pick: filled with the mood's colour and a + for more of it, a dashed outline, struck through, with a − for less.
 * A sound is a pill; a genre has squarer corners, so the two are told apart at a glance.
 */
@Composable
private fun MoodChip(dial: SoundDial, look: MoodLook, onTap: () -> Unit) {
    val more = dial.value > 0
    val shape = if (dial.genre) RoundedCornerShape(MOOD_GENRE_CORNER) else CircleShape
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val name = shownSound(dial.term)
    Row(
        Modifier
            .height(MOOD_CHIP)
            .clip(shape)
            .then(if (more) Modifier.background(look.colour) else Modifier.dashedOutline(muted.copy(alpha = 0.6f), if (dial.genre) MOOD_GENRE_CORNER else MOOD_CHIP / 2))
            .clickable(onClickLabel = if (more) "Less $name" else "Take $name off", onClick = onTap)
            .padding(horizontal = Space.Medium)
            .semantics { stateDescription = if (more) "More" else "Less" }
            .testTag("moodPick"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        Text(if (more) "+" else "−", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (more) look.ink else muted)
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (more) FontWeight.Bold else FontWeight.Medium,
            color = if (more) look.ink else muted,
            textDecoration = if (more) null else TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "+ Add a sound or genre": opens the list to pick from. */
@Composable
private fun AddChip(onTap: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .height(MOOD_CHIP)
            .clip(CircleShape)
            .dashedOutline(muted.copy(alpha = 0.6f), MOOD_CHIP / 2)
            .clickable(onClick = onTap)
            .padding(horizontal = Space.Medium)
            .testTag("moodAdd"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        Text("+", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text("Add a sound or genre", style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A dashed outline with rounded corners, drawn just inside the edge. */
private fun Modifier.dashedOutline(color: Color, corner: Dp): Modifier = drawBehind {
    val line = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(line / 2, line / 2),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** A row of choices in one pill, the chosen one raised: Artists, and the picker's Sounds and Genres. */
@Composable
internal fun MoodSegment(options: List<String>, chosen: Int, tag: String, modifier: Modifier = Modifier, onChoose: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .border(MOOD_EDGE, scheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
            .padding(Space.Tight)
            .testTag(tag),
    ) {
        options.forEachIndexed { i, option ->
            val on = i == chosen
            Box(
                Modifier
                    .weight(1f)
                    .height(MOOD_CHIP)
                    .clip(CircleShape)
                    .then(if (on) Modifier.background(scheme.surfaceContainerHighest).border(MOOD_EDGE, scheme.outlineVariant, CircleShape) else Modifier)
                    .clickable { onChoose(i) }
                    .semantics { role = Role.Tab; selected = on }
                    .testTag("$tag-$i"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where [value] (−1…1) sits across the pad, 0 to 1: the dot keeps clear of the edges. */
internal fun padFraction(value: Float): Float = PAD_INSET + (1f - 2 * PAD_INSET) * (value.coerceIn(-1f, 1f) + 1f) / 2f

/** The pad's value (−1…1) at [fraction] of the way across it. */
internal fun padValue(fraction: Float): Float = (((fraction - PAD_INSET) / (1f - 2 * PAD_INSET)) * 2f - 1f).coerceIn(-1f, 1f)

/** Where step [step] (−2…2) of the pad sits across it, 0 to 1. */
internal fun padAt(step: Int): Float = padFraction(FlowMood.at(step))

/** The pad's step (−2…2) nearest to [value] (−1…1). */
internal fun padStep(value: Float): Int = (value * 2).roundToInt().coerceIn(-2, 2)

/**
 * The mood pad: [energy] up and down, [tempo] left and right, each −2…2, set together by one dot. The dot and the
 * pad's colour follow the finger exactly ([hold]); let go, it settles on the nearest of its 25 places. [dots] are
 * songs, drawn where their own tempo and energy are.
 */
@Composable
private fun MoodPad(
    energy: Int,
    tempo: Int,
    look: MoodLook,
    hold: MoodHold,
    dots: List<PadDot>,
    onMove: (energy: Int, tempo: Int) -> Unit,
    onSettled: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val now by rememberUpdatedState(energy to tempo)
    val move by rememberUpdatedState(onMove)
    val settled by rememberUpdatedState(onSettled)
    val shape = RoundedCornerShape(MOOD_BOARD_CORNER)
    fun step(e: Int, t: Int): Boolean {
        move(e.coerceIn(-2, 2), t.coerceIn(-2, 2))
        settled()
        return true
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(MOOD_BOARD_HEIGHT)
            .clip(shape)
            .background(MOOD_PAD_GROUND)
            .border(MOOD_EDGE, Color.White.copy(alpha = 0.07f), shape)
            .testTag("soundPad")
            .semantics {
                contentDescription = "Mood pad: up for more energy, right for faster"
                stateDescription = look.name + ". " + look.about
                customActions = listOf(
                    CustomAccessibilityAction("More energy") { step(now.first + 1, now.second) },
                    CustomAccessibilityAction("Calmer") { step(now.first - 1, now.second) },
                    CustomAccessibilityAction("Faster") { step(now.first, now.second + 1) },
                    CustomAccessibilityAction("Slower") { step(now.first, now.second - 1) },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun place(at: Offset) {
                        val t = padValue(at.x / size.width)
                        val e = -padValue(at.y / size.height)
                        hold.at = Offset(t, e)
                        val next = padStep(e) to padStep(t)
                        if (next != now) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            move(next.first, next.second)
                        }
                    }
                    place(down.position)
                    // The finger is the pad's while it's down: the page doesn't scroll under it.
                    drag(down.id) { change ->
                        place(change.position)
                        change.consume()
                    }
                    hold.at = null
                    settled()
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val at = Offset(size.width * padFraction(look.tempo), size.height * padFraction(-look.energy))
            // The mood's colour, brightest under the dot and gone at the far side of the pad.
            drawRect(
                Brush.radialGradient(
                    0f to look.colour, 0.5f to look.colour.copy(alpha = 0.55f), 1f to look.deep.copy(alpha = 0.2f),
                    center = at, radius = size.width * 0.8f,
                ),
            )
            // The mix's songs: fast ones to the right, lively ones at the top.
            dots.forEach { d ->
                drawCircle(
                    Color.White.copy(alpha = 0.34f), 2.5.dp.toPx(),
                    Offset(size.width * (PAD_SONGS_INSET + (1f - 2 * PAD_SONGS_INSET) * d.x), size.height * (1f - PAD_SONGS_INSET - (1f - 2 * PAD_SONGS_INSET) * d.y)),
                )
            }
            drawCircle(Color.Black.copy(alpha = 0.22f), MOOD_ORB.toPx() + 3.dp.toPx(), at + Offset(0f, 6.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.16f), MOOD_ORB_HALO.toPx(), at)
            drawCircle(Color.White, MOOD_ORB.toPx(), at)
        }
        val label = MaterialTheme.typography.labelSmall
        val ink = Color.White.copy(alpha = 0.6f)
        Text("More energy", style = label, color = ink, modifier = Modifier.align(Alignment.TopCenter).padding(top = Space.Small))
        Text("Calmer", style = label, color = ink, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.Small))
        Text("Slower", style = label, color = ink, modifier = Modifier.align(Alignment.CenterStart).padding(start = Space.Medium))
        Text("Faster", style = label, color = ink, modifier = Modifier.align(Alignment.CenterEnd).padding(end = Space.Medium))
    }
}

/**
 * The list to pick sounds and genres from, as a sheet. Sounds are the words the sound search understands (its own
 * list); genres are the ones Siansa Beats hears in the music, or the songs' genre tags without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoodPickerSheet(tuning: VibeTuning, look: MoodLook, inMix: List<MixSound>, onChange: (VibeTuning) -> Unit, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val sounds by produceState<List<String>?>(null) { value = graph.generator.soundConcepts() }
    val genres by rememberBrowseGenres(ALL_GENRES)
    val heard by produceState(false) { value = withContext(Dispatchers.IO) { graph.beatsGenres.saved(graph.shownLibraries()) != null } }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MoodPicker(tuning, look, sounds, genres, heard, inMix, onChange, onDone, Modifier.fillMaxHeight(0.92f).padding(horizontal = Space.Page))
    }
}

/** What the picker sheet shows (also drawn on its own by the screenshot and spacing tests). */
@Composable
internal fun MoodPicker(
    tuning: VibeTuning,
    look: MoodLook,
    sounds: List<String>?,
    genres: List<Genre>?,
    heard: Boolean,
    inMix: List<MixSound>,
    onChange: (VibeTuning) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    startTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var find by rememberSaveable { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val full = !FlowMood.hasRoom(tuning)
    Column(modifier.testTag("moodPicker"), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add to this mix", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone, modifier = Modifier.testTag("moodPickerDone")) { Text("Done", fontWeight = FontWeight.Bold) }
        }
        MoodSegment(listOf("Sounds", "Genres"), tab, "moodPickerTabs") { tab = it; find = "" }
        Text(
            when {
                full -> "That's ${FlowMood.MOST_PICKS}. Take one off to add another."
                tab == 0 -> "Words your sound search understands."
                heard -> "Genres Siansa Beats hears in your music. The main ones first: search for the rest."
                else -> "Genres from your songs' tags. The main ones first: search for the rest."
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.testTag("moodPickerFrom"),
        )
        FindField(find, "Find one…", "moodPickerFind") { find = it }
        val loading = if (tab == 0) sounds == null else genres == null
        // A vibe's own sounds first, while nothing is typed: they're what this mix can be shaped by.
        val own = if (tab == 1 && find.isBlank()) inMix else emptyList()
        val ownNames = own.map { it.name.lowercase() }.toSet()
        val rows: List<Pair<String, Int?>> = if (tab == 0) FlowMood.offered(sounds.orEmpty(), find, Int.MAX_VALUE) { it }.map { it to null }
        else FlowMood.genres(genres.orEmpty(), find).filter { it.name.lowercase() !in ownNames }.map { it.name to it.songCount }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("moodPickerList")) {
            if (own.isNotEmpty()) {
                item(key = "own-title") { PickerHeading("In this mix") }
                items(own, key = { "own-" + it.name }) { sound ->
                    PickerRow(sound.name, sound.songs, FlowMood.picked(tuning, sound.name), full, look) {
                        onChange(if (FlowMood.picked(tuning, sound.name)) FlowMood.drop(tuning, sound.name) else FlowMood.pick(tuning, sound.name, genre = true))
                    }
                }
                if (rows.isNotEmpty()) item(key = "all-title") { PickerHeading("In your music") }
            }
            items(rows, key = { "row-" + it.first }) { (name, count) ->
                PickerRow(name, count, FlowMood.picked(tuning, name), full, look) {
                    onChange(if (FlowMood.picked(tuning, name)) FlowMood.drop(tuning, name) else FlowMood.pick(tuning, name, genre = tab == 1))
                }
            }
            if (rows.isEmpty() && own.isEmpty()) {
                item(key = "none") {
                    Text(
                        when {
                            loading -> "Loading…"
                            find.isNotBlank() -> "Nothing found for “${find.trim()}”."
                            tab == 0 -> "Your sound search didn't give a list of sounds."
                            else -> "No genres yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.Page).testTag("moodPickerNone"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerHeading(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.Medium, bottom = Space.Tight),
    )
}

/** One sound or genre to pick: its name, how many songs (genres), and + or, once picked, a tick in the mood's colour. */
@Composable
private fun PickerRow(name: String, count: Int?, picked: Boolean, full: Boolean, look: MoodLook, onTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val can = picked || !full
    val shown = shownSound(name)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MOOD_ROW)
                .clickable(enabled = can, onClickLabel = if (picked) "Take $shown off" else "Add $shown", onClick = onTap)
                .semantics { selected = picked }
                .testTag("moodPickerRow"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge,
                color = if (can) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) Text(String.format(Locale.getDefault(), "%,d", count), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Box(
                Modifier
                    .size(MOOD_MARK)
                    .clip(CircleShape)
                    .then(if (picked) Modifier.background(look.colour) else Modifier.border(MOOD_MARK_EDGE, scheme.onSurfaceVariant.copy(alpha = if (can) 0.6f else 0.25f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (picked) Icons.Rounded.Check else Icons.Rounded.Add,
                    null,
                    Modifier.size(18.dp),
                    tint = if (picked) look.ink else scheme.onSurface.copy(alpha = if (can) 1f else 0.35f),
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
    }
}

/**
 * Flow's Play button: a pill in the mood's colour across the bottom of the page, with what's set under its words and
 * a play mark at its end. [busy]: a vibe is being made.
 */
@Composable
internal fun MoodPlayPill(look: MoodLook, summary: String, enabled: Boolean, busy: Boolean, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MOOD_PLAY)
            .clip(CircleShape)
            .background(look.colour.copy(alpha = if (enabled || busy) 1f else 0.55f))
            .clickable(enabled = enabled && !busy, onClickLabel = "Play this vibe", onClick = onPlay)
            .padding(start = Space.Page, end = Space.Small, top = Space.Small, bottom = Space.Small)
            .testTag("vibeGenerate"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (busy) "Making your vibe…" else "Play this vibe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = look.ink, maxLines = 1)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = look.ink.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("vibeSummary"))
        }
        Box(Modifier.size(MOOD_PLAY_MARK).clip(CircleShape).background(Color.Black.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = look.ink, strokeWidth = 2.dp) // spacing: exact
            else Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp), tint = look.ink)
        }
    }
}

/** True on a dark page. */
@Composable
internal fun darkPage(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private val ARTISTS = listOf("Favourites", "A mix", "New to me")
private val MOOD_DARK_INK = Color(0xFF1A1200)
private val MOOD_PAD_GROUND = Color(0xFF0E0C14)
private val MOOD_BOARD_HEIGHT = 204.dp
private val MOOD_BOARD_CORNER = 28.dp
private const val PAD_INSET = 0.14f
private const val PAD_SONGS_INSET = 0.08f
private val MOOD_ORB = 27.dp
private val MOOD_ORB_HALO = 37.dp
private val MOOD_EDGE = 1.dp
private val MOOD_CHIP = 40.dp
private val MOOD_GENRE_CORNER = 12.dp
private val MOOD_ROW = 52.dp
private val MOOD_MARK = 30.dp
private val MOOD_MARK_EDGE = 1.5.dp
internal val MOOD_PLAY = 64.dp
private val MOOD_PLAY_MARK = 44.dp
