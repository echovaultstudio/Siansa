package app.siansa.ui

/**
 * The gap above "Playing from album" at the top of now playing, in dp, for a screen [screenHeightDp]
 * tall. It grows with the screen (2.5 % of its height) so tall phones don't look cramped at the top,
 * but stays between 12 and 28 dp so small phones keep room for the cover and large ones don't push
 * everything down.
 */
fun playerTopGapDp(screenHeightDp: Int): Float = (screenHeightDp * 0.025f).coerceIn(12f, 28f)

/**
 * How round now playing's top corners are, in px, while it is dragged [dragPx] down: square when it fills
 * the screen, rounding as it follows the finger down (up to [maxPx]) so it moves as one card over the page
 * underneath.
 */
fun playerCardCornerPx(dragPx: Float, maxPx: Float): Float = dragPx.coerceIn(0f, maxPx.coerceAtLeast(0f))

/**
 * Where the top row of now playing ("Playing from album", the arrow and ⋮) goes, in px from the top of the page under
 * the status bar: exactly halfway between there and the cover's top edge, so the gap above it equals the gap below it
 * (the owner, 9 Nov).
 */
fun playerTopRowY(coverTop: Int, rowHeight: Int): Int = ((coverTop - rowHeight) / 2).coerceAtLeast(0)

/**
 * Where the cover's top edge is, in px: below a [topGap], the row, a [Space.Page] gap ([above]), then centred in what's
 * left of the cover's room ([room], its size [cover]); the same place as before the row was centred.
 */
fun playerCoverTop(topGap: Int, rowHeight: Int, above: Int, room: Int, cover: Int): Int =
    topGap + rowHeight + above + ((room - cover) / 2).coerceAtLeast(0)
