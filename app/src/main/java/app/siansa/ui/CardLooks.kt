package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.siansa.data.CardLook

/*
 * How Home's coloured cards are painted (the owner, 13 Nov: some were too dominant). Each section of Home provides
 * its [CardLook] here; the cards in it (Today's mix, the book card, the generated tiles) ask [cardPaint] for their
 * fill and text colours, so one choice under Arrange Home repaints them all, and a card's own choice just its own.
 * The shortcut grid never takes part: its tiles are plain already.
 */

/** The look of the cards in the section being drawn; Home's choice where nothing narrower is given. */
val LocalCardLook = compositionLocalOf { CardLook.VIVID }

/** How to fill a card: a solid under a wash (either may be all there is), and what its words are drawn in. */
class CardPaint(val base: Color, val wash: Brush?, val text: Color, val faint: Color)

/** Soft: the card's colours at this much over the surface. */
internal const val SOFT_ALPHA = 0.32f

/** From the artwork: the picture's colour, this much at the card's start and end. */
internal const val ARTWORK_FROM = 0.55f
internal const val ARTWORK_TO = 0.22f

/**
 * The paint for a card of [colors] (its own two, as the generated tiles have), in the look of the section around it.
 * [picture] is the card's artwork, for the look that takes its colour from it; a card with none is painted soft then.
 */
@Composable
fun cardPaint(colors: Pair<Color, Color>, picture: String? = null, label: String = "card"): CardPaint {
    val look = LocalCardLook.current
    val scheme = MaterialTheme.colorScheme
    val quiet = CardPaint(scheme.surfaceContainerHigh, null, scheme.onSurface, scheme.onSurfaceVariant)
    return when (look) {
        CardLook.VIVID, CardLook.AS_HOME -> CardPaint(colors.first, Brush.linearGradient(listOf(colors.first, colors.second)), Color.White, Color.White.copy(alpha = 0.85f))
        CardLook.SOFT -> soft(colors, quiet)
        CardLook.PLAIN -> quiet
        CardLook.ARTWORK -> if (picture == null) soft(colors, quiet) else {
            val tint = rememberHeaderTint(picture, label)
            CardPaint(quiet.base, Brush.linearGradient(listOf(tint.copy(alpha = ARTWORK_FROM), tint.copy(alpha = ARTWORK_TO))), quiet.text, quiet.faint)
        }
    }
}

private fun soft(colors: Pair<Color, Color>, quiet: CardPaint) =
    CardPaint(quiet.base, Brush.linearGradient(listOf(colors.first.copy(alpha = SOFT_ALPHA), colors.second.copy(alpha = SOFT_ALPHA))), quiet.text, quiet.faint)

/** Fills a card with [paint] in [shape]: the solid, then the wash over it. */
fun Modifier.cardFill(paint: CardPaint, shape: Shape): Modifier {
    // The wash on a fresh Modifier: on `this` it would carry the chain before it (the card's padding) a second time,
    // and paint the wash a padding's width inside the solid (the owner, 13 Nov: a grey frame round Today's mix).
    val wash = paint.wash?.let { Modifier.background(it, shape) } ?: Modifier
    return background(paint.base, shape).then(wash)
}

/** The sample card's own colours, and the ones standing in for a picture's in the look that takes the picture's. */
private val SAMPLE_COLOURS = Color(0xFFD6338A) to Color(0xFF4A1470)
private val SAMPLE_ARTWORK = Color(0xFF8A6A2E) to Color(0xFF5A4420)

/**
 * A small card in [look], for choosing one by eye (the owner, 13 Nov): an artwork square, two lines and the play
 * circle, as Today's mix. [big] is the one card shown in a cog's dialog; the small ones stand in a row of four.
 */
@Composable
fun CardLookSample(look: CardLook, modifier: Modifier = Modifier, big: Boolean = false, chosen: Boolean = false) {
    val shape = RoundedCornerShape(if (big) 16.dp else 10.dp)
    CompositionLocalProvider(LocalCardLook provides look) {
        val paint = cardPaint(if (look == CardLook.ARTWORK) SAMPLE_ARTWORK else SAMPLE_COLOURS)
        Box(
            modifier
                .then(if (chosen) Modifier.border(2.dp, Accent, shape) else Modifier)
                .clip(shape)
                .cardFill(paint, shape)
                .padding(if (big) Space.Medium else Space.Small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(if (big) 48.dp else 22.dp).clip(RoundedCornerShape(if (big) 8.dp else 4.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFE0B030), Color(0xFFC8322A)))),
                )
                Spacer(Modifier.width(if (big) Space.Medium else Space.Small))
                Column(Modifier.weight(1f)) {
                    if (big) {
                        Text("Today's mix", style = MaterialTheme.typography.labelSmall, color = paint.faint)
                        Text("Sunlight on the windowsill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = paint.text, maxLines = 1)
                        Text("40 tracks", style = MaterialTheme.typography.bodySmall, color = paint.faint)
                    } else {
                        Line(paint.text, 0.8f, 6.dp)
                        Spacer(Modifier.height(Space.Tight))
                        Line(paint.faint, 0.55f, 4.dp)
                    }
                }
                if (big) Spacer(Modifier.width(Space.Small))
                Box(Modifier.size(if (big) 36.dp else 14.dp).clip(CircleShape).background(Accent))
            }
        }
    }
}

/** A line of words, as a bar: [fraction] of the width, [height] tall. */
@Composable
private fun Line(colour: Color, fraction: Float, height: Dp) {
    Box(
        Modifier.fillMaxWidth(fraction).height(height)
            .clip(CircleShape).background(colour),
    )
}

/** Four small cards to pick a look from, [chosen] ringed in the accent colour, each named under it. */
@Composable
fun CardLookChooser(chosen: CardLook, options: List<CardLook>, modifier: Modifier = Modifier, onPick: (CardLook) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
        options.forEach { look ->
            Column(
                Modifier.weight(1f)
                    .clickable(onClickLabel = look.label) { onPick(look) }
                    .semantics { selected = look == chosen }
                    .testTag("cardLook-${look.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CardLookSample(look, Modifier.fillMaxWidth().aspectRatio(SAMPLE_ASPECT), chosen = look == chosen)
                Spacer(Modifier.height(Space.Small))
                Text(
                    look.label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (look == chosen) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (look == chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A small sample card's width to its height. */
private const val SAMPLE_ASPECT = 1.45f
