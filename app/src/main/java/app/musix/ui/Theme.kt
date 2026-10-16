package app.musix.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Musix colours, taken from the app icon: a warm sunrise amber for anything you press to play,
 * soft violet as the second colour, and deep plum backgrounds.
 */
object Brand {
    val Amber = Color(0xFFFFB547)
    val AmberDeep = Color(0xFFB86A00)
    val Violet = Color(0xFFB69CFF)
    val VioletDeep = Color(0xFF5B3FD0)
    val Plum = Color(0xFF110D1C)
    val PlumRaised = Color(0xFF1C1630)
    val PlumHigh = Color(0xFF272040)
}

/** The colour for play buttons, active controls, hearts and the playing song. */
val Accent = Brand.Amber

private val Dark = darkColorScheme(
    primary = Brand.Amber,
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF3D2A55),
    onPrimaryContainer = Color(0xFFF1E8FF),
    secondary = Brand.Violet,
    onSecondary = Color(0xFF1E1240),
    secondaryContainer = Color(0xFF2E2450),
    onSecondaryContainer = Color(0xFFE6DDFF),
    tertiary = Color(0xFFFF8A7A),
    background = Brand.Plum,
    onBackground = Color(0xFFEDE7F6),
    surface = Brand.Plum,
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = Brand.PlumHigh,
    onSurfaceVariant = Color(0xFFB9B0CC),
    surfaceContainerLowest = Color(0xFF0B0814),
    surfaceContainerLow = Color(0xFF161124),
    surfaceContainer = Brand.PlumRaised,
    surfaceContainerHigh = Brand.PlumHigh,
    surfaceContainerHighest = Color(0xFF322A4D),
    outline = Color(0xFF6E6585),
    outlineVariant = Color(0xFF3A3250),
)

private val Light = lightColorScheme(
    primary = Brand.AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF22164A),
    secondary = Brand.VioletDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0FF),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFEAE4F3),
    surfaceContainer = Color(0xFFF2EDF9),
    surfaceContainerHigh = Color(0xFFECE6F5),
)

/** Follows the system light/dark setting, always in Musix's own colours. */
@Composable
fun MusixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
