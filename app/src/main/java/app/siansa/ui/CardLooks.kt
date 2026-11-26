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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import app.siansa.data.CardLook

/*
 * How Home's coloured cards are painted (the owner, 13 Nov: some were too dominant). Each section of Home provides
 * its [CardLook] here; the cards in it (Today's mix, the book card, the generated tiles) ask [cardPaint] for their
 * fill and text colours, so one choice under Arrange Home repaints them all, and a card's own choice just its own.
 * The shortcut grid never takes part: its tiles are plain already.
 */

/** The look of the cards in the section being drawn; Home's choice where nothing narrower is given. */
val LocalCardLook = compositionLocalOf { CardLook.DEFAULT }

/**
 * The colour of the shortcut tiles in the section being drawn (#1261): null keeps them plain, as they always were; a
 * row's own colour (chosen under its cog) paints them in it.
 */
val LocalShortcutLook = compositionLocalOf<CardLook?> { null }

/** How to fill a card: a solid under a wash (either may be all there is), and what its words are drawn in. */
class CardPaint(val base: Color, val wash: Brush?, val text: Color, val faint: Color)

/**
 * The paint for a card in the colour of the section around it (the owner, 25 Nov: the same colours as Your listening's
 * card): the colour's fade from top to foot, with white words. From its cover takes the fade from [picture] (the card's
 * artwork) as Your listening does from its top song; a card with none shows that colour's own. [colors], the card's own
 * two, are kept for the shortcut tiles' chosen colours ([shortcutPaint]).
 */
@Composable
fun cardPaint(colors: Pair<Color, Color>, picture: String? = null, label: String = "card", look: CardLook = LocalCardLook.current): CardPaint {
    // Vivid: the card's own two colours, full (pale ones take dark words).
    if (look.resolved == CardLook.VIVID) return filledReadable(colors)
    val (top, foot) = cardFade(look, picture, label)
    return CardPaint(top, Brush.verticalGradient(listOf(top, foot)), Color.White, Color.White.copy(alpha = 0.85f))
}

/** [look]'s top and foot, [picture]'s for From its cover. As Home here (nothing narrower given) is the default. */
@Composable
internal fun cardFade(look: CardLook, picture: String?, label: String): Pair<Color, Color> {
    val colour = look.resolved.colour ?: return VIVID_SAMPLE
    if (colour != app.siansa.data.ListenColour.COVER || picture == null) return Color(colour.top) to Color(colour.foot)
    val tint = rememberHeaderTint(picture, label)
    return lerp(tint, Color.Black, COVER_TOP) to lerp(tint, Color.Black, COVER_FOOT)
}

/** From its cover: the picture's colour darkened this much at the top and at the foot, as Your listening's card. */
internal const val COVER_TOP = 0.55f
internal const val COVER_FOOT = 0.86f

/** A card filled with [colors] themselves: Vivid, or a shortcut tile in its row's chosen colours (By mood, Siansa, Mono). */
private fun filled(colors: Pair<Color, Color>) =
    CardPaint(colors.first, Brush.linearGradient(listOf(colors.first, colors.second)), Color.White, Color.White.copy(alpha = 0.85f))

/** [filled], with dark words on pale colours (Siansa's frost, Mono's light greys). */
private fun filledReadable(colors: Pair<Color, Color>): CardPaint {
    val paint = filled(colors)
    return if (wordsDarkOn(colors)) CardPaint(paint.base, paint.wash, DARK_WORDS, DARK_WORDS.copy(alpha = 0.75f)) else paint
}

/** Vivid's swatch and stand-in fade: a full pink to purple, as its sample card. */
internal val VIVID_SAMPLE = Color(0xFFD6338A) to Color(0xFF4A1470)

/**
 * The colour a row's shortcut tiles take (the owner, 23 Nov: "shortcuts don't get their colour changes at all. All
 * changes and settings must be possible for all cards"): the row's own Card colour when it has one; else Vivid once its
 * Colours are a choice that isn't the covers' own (By mood, Siansa, Mono), so that choice shows; else none, and the
 * tiles stay plain as they came (#1261).
 */
internal fun shortcutLookOf(cardColour: CardLook?, colours: app.siansa.data.PictureColours?): CardLook? =
    cardColour ?: CardLook.AS_HOME.takeIf { colours != null && colours != app.siansa.data.PictureColours.COVERS }

/**
 * The two colours a shortcut tile's row gives it ([LocalPictureColours]: its songs' moods for a mix, [songIds], or a
 * set), or null when it keeps its own (the covers' colours, or By mood for something that isn't a mix).
 */
@Composable
internal fun shortcutColours(title: String, songIds: List<String> = emptyList()): Pair<Color, Color>? {
    val palette = LocalPictureColours.current
    val mood = if (palette == app.siansa.data.PictureColours.MOOD) rememberMoodPaint(songIds) else null
    return (mood ?: setPaint(palette, title))?.let { it.first to it.second }
}

/**
 * A shortcut tile's paint in [look]: in its row's [chosen] colours when it has them ([shortcutColours]), whatever its
 * picture's are, with words that can be read on them; else its picture's or its [own] as before.
 */
@Composable
internal fun shortcutPaint(own: Pair<Color, Color>, chosen: Pair<Color, Color>?, picture: String?, title: String, look: CardLook): CardPaint {
    // A row's own Card colour paints its tiles in it; without one (As Home) or in Vivid, they take its chosen colours
    // themselves.
    if (chosen == null || (look != CardLook.AS_HOME && look != CardLook.VIVID)) return cardPaint(own, picture, title, look)
    return filledReadable(chosen)
}

/** Whether words on a fill of [colours] should be dark: the fill is light where the words begin. */
internal fun wordsDarkOn(colours: Pair<Color, Color>): Boolean =
    (colours.first.luminance() * 2f + colours.second.luminance()) / 3f > 0.42f

private val DARK_WORDS = Color(0xFF14181C)

/** Fills a card with [paint] in [shape]: the solid, then the wash over it. */
fun Modifier.cardFill(paint: CardPaint, shape: Shape): Modifier {
    // The wash on a fresh Modifier: on `this` it would carry the chain before it (the card's padding) a second time,
    // and paint the wash a padding's width inside the solid (the owner, 13 Nov: a grey frame round Today's mix).
    val wash = paint.wash?.let { Modifier.background(it, shape) } ?: Modifier
    return background(paint.base, shape).then(wash)
}

/** The sample card's own colours. */
private val SAMPLE_COLOURS = Color(0xFFD6338A) to Color(0xFF4A1470)

/**
 * A small card in [look], for choosing one by eye (the owner, 13 Nov): an artwork square, two lines and the play
 * circle, as Today's mix. [big] is the one card shown in a cog's dialog; the small ones stand in a row of four.
 */
@Composable
fun CardLookSample(look: CardLook, modifier: Modifier = Modifier, big: Boolean = false, chosen: Boolean = false) {
    val shape = RoundedCornerShape(if (big) 16.dp else 10.dp)
    CompositionLocalProvider(LocalCardLook provides look) {
        val paint = cardPaint(SAMPLE_COLOURS, null)
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

/** Home's card colours to pick from (the owner, 25 Nov): Your listening's swatches, [chosen] ringed and in bold. */
@Composable
fun CardLookChooser(chosen: CardLook, options: List<CardLook>, modifier: Modifier = Modifier, onPick: (CardLook) -> Unit) {
    ColourSwatches(options, chosen, { cardSwatchFade(it, chosen) }, { it.label }, { "cardLook-${it.id}" }, modifier.testTag("homeCardColours"), onPick = onPick)
}
