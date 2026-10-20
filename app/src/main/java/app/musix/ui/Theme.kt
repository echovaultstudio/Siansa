package app.musix.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

/** Light, dark, or following the phone (issue #148). */
enum class ThemeMode(val label: String) { AUTO("Auto"), LIGHT("Light"), DARK("Dark") }

/**
 * Where the theme's colours come from (issue #148): Musix's own, the phone's wallpaper (Android 12+), a
 * preset made from one [seed] colour, or [YOURS]: a seed colour the listener picks.
 */
enum class ThemeColours(val label: String, val seed: Long? = null) {
    MUSIX("Musix"),
    WALLPAPER("Wallpaper"),
    OCEAN("Ocean", 0xFF1F6FD1),
    FOREST("Forest", 0xFF2E7D4F),
    ROSE("Rose", 0xFFC2416E),
    SUNSET("Sunset", 0xFFE8692A),
    MONO("Mono", 0xFF777777),
    YOURS("Your colour");

    companion object {
        fun parse(value: String?): ThemeColours = entries.firstOrNull { it.name == value } ?: MUSIX

        /** Wallpaper colours exist from Android 12 on. */
        val wallpaperAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /** What can be picked on this phone. */
        val available: List<ThemeColours> get() = entries.filter { it != WALLPAPER || wallpaperAvailable }
    }
}

/**
 * The colour of selected and active items (issue #313): chosen chips, the current tab, the playing song.
 * [THEME] takes the theme's accent (amber when the theme is grey); the others are fixed.
 */
enum class SelectionColour(val label: String, val argb: Long? = null) {
    THEME("Theme"),
    AMBER("Amber", 0xFFFFB547),
    VIOLET("Violet", 0xFF9C7CFF),
    BLUE("Blue", 0xFF4F8EF7),
    TEAL("Teal", 0xFF2BB5A8),
    GREEN("Green", 0xFF4CAF6A),
    PINK("Pink", 0xFFE5508F),
    RED("Red", 0xFFE5534B);

    companion object {
        fun parse(value: String?): SelectionColour = entries.firstOrNull { it.name == value } ?: THEME
    }
}

/** Whether [c] is (nearly) grey: its channels hardly differ. */
internal fun isGreyish(c: Color): Boolean = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue) < 0.12f

/** The colour for selected items: [choice]'s own, or the theme's [accent] (amber when that is grey). */
internal fun selectionColourOf(choice: SelectionColour, accent: Color): Color =
    choice.argb?.let { Color(it) } ?: if (isGreyish(accent)) Brand.Amber else accent

/** Text and icons on [selected]: near black on light colours, white on dark ones. */
internal fun onSelectionColourOf(selected: Color): Color =
    if (0.2126f * selected.red + 0.7152f * selected.green + 0.0722f * selected.blue > 0.5f) Color(0xFF1A1400) else Color.White

/** [this] with selected items in [selected]: Material's chips, tab bar and segmented buttons use the secondary container. */
private fun ColorScheme.withSelection(selected: Color): ColorScheme =
    copy(secondaryContainer = selected, onSecondaryContainer = onSelectionColourOf(selected))

/** The chosen theme. */
data class ThemeChoice(
    val mode: ThemeMode = ThemeMode.AUTO,
    val colours: ThemeColours = ThemeColours.MUSIX,
    val pureBlack: Boolean = false,
    /** The seed for [ThemeColours.YOURS], as ARGB. */
    val seed: Int = app.musix.data.DEFAULT_THEME_SEED,
    /** The colour of selected items (issue #313). */
    val selection: SelectionColour = SelectionColour.THEME,
)

/** The colour of selected and active items (issue #313): set in Themes. */
val Selected: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer

/** Text and icons on [Selected]. */
val OnSelected: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSecondaryContainer

/** The colour for play buttons, active controls, hearts and the playing song: the theme's main colour. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalAccent.current

/** Text and icons on [Accent]. */
val OnAccent: Color
    @Composable @ReadOnlyComposable get() = LocalOnAccent.current

/** The theme's dark colours, for parts that are always dark (the bar behind the tabs, cover pages). */
val DarkColours: ColorScheme
    @Composable @ReadOnlyComposable get() = LocalDarkColours.current

private val LocalAccent = staticCompositionLocalOf { Brand.Amber }
private val LocalOnAccent = staticCompositionLocalOf { Color(0xFF2A1700) }
private val LocalDarkColours = staticCompositionLocalOf { MusixDark }
private val LocalChoice = staticCompositionLocalOf { ThemeChoice() }

private val MusixDark = darkColorScheme(
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

private val MusixLight = lightColorScheme(
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

/** Dark colours on a pure black background (OLED screens). */
private fun ColorScheme.onBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(surfaceContainerLow, 0.45f),
    surfaceContainer = lerp(surfaceContainer, 0.55f),
    surfaceContainerHigh = lerp(surfaceContainerHigh, 0.65f),
    surfaceContainerHighest = lerp(surfaceContainerHighest, 0.75f),
)

/** [c] darkened towards black: [keep] of its brightness stays. */
private fun lerp(c: Color, keep: Float) = Color(c.red * keep, c.green * keep, c.blue * keep, c.alpha)

private fun ThemeTones.Roles.toScheme(dark: Boolean): ColorScheme {
    fun c(v: Int) = Color(v)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(primary), onPrimary = c(onPrimary), primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        inversePrimary = c(inversePrimary),
        secondary = c(secondary), onSecondary = c(onSecondary), secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary), tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(surface), onBackground = c(onSurface), surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant), surfaceTint = c(primary),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        outline = c(outline), outlineVariant = c(outlineVariant),
        surfaceBright = c(containerHighest), surfaceDim = c(surface),
        surfaceContainerLowest = c(containerLowest), surfaceContainerLow = c(containerLow), surfaceContainer = c(container),
        surfaceContainerHigh = c(containerHigh), surfaceContainerHighest = c(containerHighest),
    )
}

/** The colours for [colours] in light or [dark]; pure black only applies to dark. [seed] is used for "Your colour". */
@Composable
fun themeScheme(colours: ThemeColours, dark: Boolean, pureBlack: Boolean = false, seed: Int = LocalChoice.current.seed): ColorScheme {
    val context = LocalContext.current
    val base = when {
        colours == ThemeColours.YOURS -> remember(seed, dark) { ThemeTones.scheme(seed, dark, mono = ThemeTones.isGrey(seed)).toScheme(dark) }
        colours == ThemeColours.MUSIX -> if (dark) MusixDark else MusixLight
        colours == ThemeColours.WALLPAPER && ThemeColours.wallpaperAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        colours.seed != null -> remember(colours, dark) {
            ThemeTones.scheme((colours.seed ?: 0L).toInt(), dark, mono = colours == ThemeColours.MONO).toScheme(dark)
        }
        else -> if (dark) MusixDark else MusixLight
    }
    return if (dark && pureBlack) base.onBlack() else base
}

/** The accent of [colours]: Musix keeps its bright amber in light and dark, the others use their main colour. */
private fun accentOf(colours: ThemeColours, scheme: ColorScheme): Color =
    if (colours == ThemeColours.MUSIX) Brand.Amber else scheme.primary

private fun onAccentOf(colours: ThemeColours, scheme: ColorScheme): Color =
    if (colours == ThemeColours.MUSIX) Color(0xFF2A1700) else scheme.onPrimary

/** Whether [choice] is dark right now (Auto follows the phone, including its dark-mode schedule). */
@Composable
fun isDark(choice: ThemeChoice): Boolean = when (choice.mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** The app's theme: [choice]'s colours, light or dark. */
@Composable
fun MusixTheme(choice: ThemeChoice = ThemeChoice(), content: @Composable () -> Unit) {
    val dark = isDark(choice)
    val plain = themeScheme(choice.colours, dark, choice.pureBlack, choice.seed)
    val scheme = plain.withSelection(selectionColourOf(choice.selection, accentOf(choice.colours, plain)))
    val darkPlain = if (dark) plain else themeScheme(choice.colours, true, choice.pureBlack, choice.seed)
    val darkScheme = darkPlain.withSelection(selectionColourOf(choice.selection, accentOf(choice.colours, darkPlain)))
    CompositionLocalProvider(
        LocalChoice provides choice,
        LocalAccent provides accentOf(choice.colours, scheme),
        LocalOnAccent provides onAccentOf(choice.colours, scheme),
        LocalDarkColours provides darkScheme,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** The theme's dark colours whatever the setting, for pages with their own dark background (cover pages). */
@Composable
fun MusixDarkTheme(content: @Composable () -> Unit) {
    val choice = LocalChoice.current
    val plain = themeScheme(choice.colours, true, seed = choice.seed)
    val scheme = plain.withSelection(selectionColourOf(choice.selection, accentOf(choice.colours, plain)))
    CompositionLocalProvider(
        LocalAccent provides accentOf(choice.colours, scheme),
        LocalOnAccent provides onAccentOf(choice.colours, scheme),
        // Text on these pages is light, also when the phone is in light mode: before, text without a colour
        // of its own took the light theme's dark one and all but vanished ("Top artists" on Your month).
        androidx.compose.material3.LocalContentColor provides scheme.onSurface,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Two colours for gradient tiles (Liked songs): Musix's amber and violet, or the theme's main and third colours. */
val BrandGradient: List<Color>
    @Composable @ReadOnlyComposable get() =
        if (LocalChoice.current.colours == ThemeColours.MUSIX) listOf(Brand.Amber, Brand.VioletDeep)
        else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)

/** The theme saved in [settings]. */
fun themeChoiceOf(settings: app.musix.data.AppSettings): ThemeChoice = ThemeChoice(
    mode = ThemeMode.entries.firstOrNull { it.name == settings.themeMode } ?: ThemeMode.AUTO,
    colours = ThemeColours.parse(settings.themeColours).let { if (it == ThemeColours.WALLPAPER && !ThemeColours.wallpaperAvailable) ThemeColours.MUSIX else it },
    pureBlack = settings.pureBlack,
    seed = settings.themeSeed,
    selection = SelectionColour.parse(settings.themeSelection),
)
