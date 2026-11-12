package app.siansa.ui

/*
 * How Home's menu comes into the top row (the owner, 12 Nov): the numbers behind [HomeMenuBar]'s motion, kept apart
 * from the drawing so they can be checked on their own.
 */

/** The menu's spring (the owner, 12 Nov): softer than the DJ bar's, about 0.4 s, with a touch of give at the end. */
internal const val MENU_SPRING_DAMPING = 0.82f
internal const val MENU_SPRING_STIFFNESS = 320f

/** What the menu's places are tinted with, over the blurred chips, fully open; and the hairline round the pill. */
private const val MENU_PILL_TINT = 0.40f
internal const val MENU_PILL_EDGE = 0.14f

/** How the places come in: each a little after the one before, over this much of the opening. */
private const val MENU_PLACES_FROM = 0.15f
private const val MENU_PLACES_STAGGER = 0.07f
private const val MENU_PLACES_OVER = 0.5f
private const val MENU_PLACE_SMALL = 0.6f

/** How much a place's alpha and scale ride the spring's overshoot past 1. */
private const val MENU_OVERSHOOT = 0.6f

/** How far [open] has brought place [index] in, 0 to 1. */
internal fun placeIn(open: Float, index: Int): Float =
    ((open.coerceIn(0f, 1f) - MENU_PLACES_FROM - index * MENU_PLACES_STAGGER) / MENU_PLACES_OVER).coerceIn(0f, 1f)

/** A place's scale at [open]: grown in by [placeIn], and a touch past full size while the spring overshoots. */
internal fun placeScale(open: Float, index: Int): Float {
    val grown = MENU_PLACE_SMALL + (1f - MENU_PLACE_SMALL) * placeIn(open, index)
    return grown * (if (open > 1f) 1f + (open - 1f) * MENU_OVERSHOOT else 1f)
}

/** The pill's tint over the chips at [open]: nothing closed, [MENU_PILL_TINT] open. */
internal fun pillTint(open: Float): Float = MENU_PILL_TINT * open.coerceIn(0f, 1f)

