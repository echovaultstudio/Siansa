package app.musix.ui

/**
 * How the lyrics fade out at the top and bottom of their area, so they blend into the blurred cover
 * instead of sitting in a box. Returns gradient stops (position 0..1, opacity 0..1): clear at both
 * edges, fully visible in between. The fade is [fadePx] tall, but never more than a quarter of the
 * area, so short screens still show most lines clearly.
 */
fun lyricsFadeStops(heightPx: Float, fadePx: Float): List<Pair<Float, Float>> {
    if (heightPx <= 0f || fadePx <= 0f) return listOf(0f to 1f, 1f to 1f)
    val edge = (fadePx / heightPx).coerceAtMost(0.25f)
    return listOf(0f to 0f, edge to 1f, 1f - edge to 1f, 1f to 0f)
}
