package app.musix.ui

/**
 * The gap above "Playing from album" at the top of now playing, in dp, for a screen [screenHeightDp]
 * tall. It grows with the screen (2.5 % of its height) so tall phones don't look cramped at the top,
 * but stays between 12 and 28 dp so small phones keep room for the cover and large ones don't push
 * everything down.
 */
fun playerTopGapDp(screenHeightDp: Int): Float = (screenHeightDp * 0.025f).coerceIn(12f, 28f)
