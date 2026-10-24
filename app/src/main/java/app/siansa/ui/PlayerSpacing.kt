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
