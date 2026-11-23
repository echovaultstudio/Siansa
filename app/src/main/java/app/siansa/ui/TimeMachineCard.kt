package app.siansa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.siansa.data.PictureStyle
import app.siansa.data.Song
import app.siansa.mix.TimeCard
import app.siansa.mix.TimeCardList
import app.siansa.mix.TimeListKind
import kotlinx.coroutines.launch

/**
 * Home's Time Machine card (issue #1341; the owner, 23 Nov: "the design should follow the hero card visually"): the
 * hero card in a colour of its own, one of [card]'s lists big with Play and Shuffle, the others at its foot, each with
 * the date it's from on its picture. A tap on one at the foot brings it up as the big one; a tap on the big one opens
 * it in the Time Machine ([open]). Its look is fixed: stickers for the big picture, an aura for the small ones.
 * Nothing when the card has no list.
 */
@Composable
fun TimeMachineCard(card: TimeCard, songsOf: suspend (List<String>) -> List<Song>, play: (List<Song>, Boolean) -> Unit, open: (TimeCardList) -> Unit) {
    if (card.lists.isEmpty()) return
    // Which list is big: the first, until one at the foot is tapped. A new day's card starts again.
    var bigKind by rememberSaveable(card.day) { mutableStateOf(card.lists.first().kind) }
    val big = card.lists.firstOrNull { it.kind == bigKind } ?: card.lists.first()
    val scope = rememberCoroutineScope()
    // Its songs from the library on the phone: a few ms, then Shuffle shows.
    val songs by produceState(emptyList<Song>(), big.kind, big.songIds) { value = songsOf(big.songIds) }
    val item = HomeItem(
        key = "time-${big.kind}", title = big.title, subtitle = big.line, meta = songCount(big.songIds.size), colors = TIME_CARD_COLOURS,
        open = { open(big) },
        // Play is there before the songs are read: it reads them itself then.
        play = { if (songs.isNotEmpty()) play(songs, false) else scope.launch { songsOf(big.songIds).takeIf { it.isNotEmpty() }?.let { play(it, false) } } },
        mosaic = big.covers, songs = songs, songIdList = big.songIds, tag = big.tag,
    )
    val under = timeCardUnder(card, big.kind).map { list ->
        HomeItem(key = "time-${list.kind}", title = list.title, colors = timeListColours(list.kind), open = { bigKind = list.kind },
            mosaic = list.covers, songIdList = list.songIds, tag = list.tag)
    }
    CompositionLocalProvider(LocalPictureStyle provides PictureStyle.PILE, LocalHeroCardPictures provides PictureStyle.AURA) {
        HomeHero("Time Machine", item, play = null, rowSongs = emptyList(), tint = TIME_CARD_COLOURS.first, under = under, tag = "timeMachineCard", playSongs = play)
    }
}

/** The lists at the card's foot: all but the big one ([big], its kind), in the card's order. */
internal fun timeCardUnder(card: TimeCard, big: String): List<TimeCardList> = card.lists.filter { it.kind != big }

internal fun songCount(n: Int): String = if (n == 1) "1 song" else "$n songs"

/** The card's own colour: a deep teal, so it reads as the past and not as today's mixes. */
internal val TIME_CARD_COLOURS = Color(0xFF2A5560) to Color(0xFF14282E)

/** A colour for each kind of list at the card's foot, the same every day. */
internal fun timeListColours(kind: String): Pair<Color, Color> = when (kind) {
    TimeListKind.DAY.id -> Color(0xFFD89A3C) to Color(0xFF7A3A1E)
    TimeListKind.LAST_MONTH.id -> Color(0xFF4F8FD0) to Color(0xFF3A4E8A)
    TimeListKind.LAST_YEAR.id -> Color(0xFFB05AA8) to Color(0xFF5A2E7A)
    TimeListKind.TWO_YEARS.id -> Color(0xFF5FA86A) to Color(0xFF2E5A4A)
    TimeListKind.FIVE_YEARS.id -> Color(0xFFC8605A) to Color(0xFF6A2A3A)
    else -> Color(0xFF3FA39A) to Color(0xFF1C4A50)
}
